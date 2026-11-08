package com.bidr.feishu.service.contact;

import com.bidr.feishu.config.OnFeishuDirectForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.utils.FuncUtil;
import com.lark.oapi.service.contact.v3.model.Department;
import com.lark.oapi.service.contact.v3.model.User;
import com.bidr.feishu.dto.sync.FeishuSyncResult;
import com.bidr.feishu.event.FeishuSyncCompletedEvent;
import com.bidr.feishu.util.FeishuMobileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Title: FeishuSyncService
 * Description: 飞书组织同步服务（对标 hse mdm 的 DeptInitService / UserAccountService）：
 * 飞书部门 → AcDept（dept_id = open_department_id，pid = 父部门），飞书用户 → AcAccount
 * （id = user_name = 飞书 user_id（employee_id），未分配时回退 open_id），手机号去除 +86 前缀入库。
 * 采用按 id upsert 而非 truncate，保留系统内置账号（如 bidrAdmin）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@Conditional(OnFeishuDirectForm.class)
@Service
@RequiredArgsConstructor
public class FeishuSyncService {

    private final FeishuDepartmentService feishuDepartmentService;
    private final FeishuUserService feishuUserService;
    private final AcDeptService acDeptService;
    private final AcAccountService acAccountService;
    private final AcUserService acUserService;
    private final CreateUserService createUserService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 全量同步飞书部门与用户至基础库
     */
    @Transactional(rollbackFor = Exception.class)
    public FeishuSyncResult syncAll() {
        FeishuSyncResult result = new FeishuSyncResult();
        List<Department> departments = feishuDepartmentService.listAll(FeishuDepartmentService.ROOT_DEPT_ID);
        syncDepts(departments);
        result.setDeptCount(departments.size() + 1);

        List<String> deptIds = new ArrayList<>();
        deptIds.add(FeishuDepartmentService.ROOT_DEPT_ID);
        for (Department dept : departments) {
            if (FuncUtil.isNotEmpty(dept.getOpenDepartmentId())) {
                deptIds.add(dept.getOpenDepartmentId());
            }
        }
        List<User> users = feishuUserService.listByDepartments(deptIds);
        syncAccounts(users);
        result.setAccountCount(users.size());
        log.info("飞书组织同步完成，部门 {} 个，用户 {} 个", result.getDeptCount(), result.getAccountCount());
        // 通知下游模块（同步事务提交后由 AFTER_COMMIT 监听器处理）
        eventPublisher.publishEvent(new FeishuSyncCompletedEvent(this, result));
        return result;
    }

    /**
     * 同步部门（含根部门）
     */
    public void syncDepts(List<Department> departments) {
        upsertDept(buildRootDept());
        if (FuncUtil.isNotEmpty(departments)) {
            for (Department dept : departments) {
                upsertDept(buildAcDept(dept));
            }
        }
    }

    /**
     * 同步用户：upsert 账号并为在职成员预创建登录用户（含默认角色），
     * 使管理员可在成员首次登录前先行配置角色与数据权限
     */
    public void syncAccounts(List<User> users) {
        if (FuncUtil.isNotEmpty(users)) {
            for (User user : users) {
                AcAccount account = buildAccount(user);
                upsertAccount(account);
                createUserIfAbsent(account);
            }
        }
    }

    /**
     * 按账号预创建登录用户：ac_user 不存在时以账号信息创建并绑定默认角色；
     * 已存在则跳过（避免覆盖管理员在后台调整过的角色/状态）；
     * 飞书侧已停用/离职的成员不预创建
     */
    private void createUserIfAbsent(AcAccount account) {
        if (!ActiveStatusDict.ACTIVATE.getValue().equals(account.getStatus())) {
            return;
        }
        if (FuncUtil.isNotEmpty(acUserService.getUserByUserName(account.getUserName()))) {
            return;
        }
        createUserService.createUser(account);
        log.info("飞书同步预创建用户: {}, name: {}", account.getId(), account.getName());
    }

    /**
     * 构建飞书根部门（ac_dept 根节点）
     */
    private AcDept buildRootDept() {
        AcDept root = new AcDept();
        root.setDeptId(FeishuDepartmentService.ROOT_DEPT_ID);
        root.setName("飞书组织");
        root.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        root.setShowOrder(0);
        return root;
    }

    private AcDept buildAcDept(Department dept) {
        AcDept acDept = new AcDept();
        acDept.setDeptId(dept.getOpenDepartmentId());
        if (FuncUtil.isNotEmpty(dept.getParentDepartmentId())
                && !FeishuDepartmentService.ROOT_DEPT_ID.equals(dept.getParentDepartmentId())) {
            acDept.setPid(dept.getParentDepartmentId());
        } else {
            acDept.setPid(FeishuDepartmentService.ROOT_DEPT_ID);
        }
        acDept.setName(dept.getName());
        acDept.setLeader(dept.getLeaderUserId());
        acDept.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        return acDept;
    }

    private AcAccount buildAccount(User user) {
        // 账号身份键与登录名统一使用飞书 user_id（employee_id，管理员分配的英文工号，未来支持账号密码登录）；
        // 未分配或权限不足时回退 open_id（ou_+32 位十六进制不可读，仅作内部兜底）
        String userId = FuncUtil.isEmpty(user.getUserId()) ? user.getOpenId() : user.getUserId();
        AcAccount account = new AcAccount();
        account.setId(userId);
        // 权限不足时（仅基础只读）飞书不返回 name，回退用 openId，避免 NOT NULL 列插入失败
        String name = FuncUtil.isEmpty(user.getName()) ? user.getOpenId() : user.getName();
        account.setName(name);
        account.setUserName(userId);
        account.setMobile(FeishuMobileUtil.normalize(user.getMobile()));
        account.setEmail(user.getEmail());
        account.setPictureLink(user.getAvatar() == null ? null : user.getAvatar().getAvatarOrigin());
        if (FuncUtil.isNotEmpty(user.getDepartmentIds())) {
            account.setDepartment(user.getDepartmentIds()[0]);
        } else {
            // 根部门直属/未分配成员拿不到 department_ids，兜底挂根部门：
            // 登录建用户时 bindDept 以 department 写 ac_user_dept（dept_id 列非空），缺省即炸 SQL
            account.setDepartment(FeishuDepartmentService.ROOT_DEPT_ID);
        }
        // find_by_department 基础只读权限不返回 status 字段，SDK getStatus() 恒 null——
        // 缺省视为在职，仅显式离职/退出/停用才判死，否则全组织被误标离职且跳过用户预创建
        boolean active = user.getStatus() == null
                || (!Boolean.TRUE.equals(user.getStatus().getIsResigned())
                && !Boolean.TRUE.equals(user.getStatus().getIsExited())
                && !Boolean.FALSE.equals(user.getStatus().getIsActivated()));
        account.setEmployStatus(active ? BoolDict.YES.getValue() : BoolDict.NO.getValue());
        account.setStatus(active ? ActiveStatusDict.ACTIVATE.getValue() : ActiveStatusDict.DEACTIVATE.getValue());
        return account;
    }

    /**
     * 按 deptId upsert 部门
     */
    private void upsertDept(AcDept dept) {
        AcDept existed = acDeptService.selectById(dept.getDeptId());
        if (FuncUtil.isEmpty(existed)) {
            dept.setCreateAt(new Date());
            acDeptService.insert(dept);
        } else {
            dept.setCreateBy(existed.getCreateBy());
            dept.setCreateAt(existed.getCreateAt());
            acDeptService.updateById(dept);
        }
    }

    /**
     * 按 open_id upsert 账号
     */
    private void upsertAccount(AcAccount account) {
        AcAccount existed = acAccountService.selectById(account.getId());
        if (FuncUtil.isEmpty(existed)) {
            account.setCreateAt(new Date());
            acAccountService.insert(account);
        } else {
            account.setCreateAt(existed.getCreateAt());
            acAccountService.updateById(account);
        }
    }
}
