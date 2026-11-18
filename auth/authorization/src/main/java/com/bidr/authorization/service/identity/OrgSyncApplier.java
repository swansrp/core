package com.bidr.authorization.service.identity;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.authorization.service.account.AccountInitService;
import com.bidr.authorization.identity.OrgSyncResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: OrgSyncApplier
 * Description: 组织同步落库器（T1.1 棒4，落库反转的内核半边）——消费 pack-spi
 * {@link OrgSyncResult} 纯数据写内核表：部门树 upsert ac_dept、账号 upsert ac_account、
 * 建号走 {@link AccountInitService#accountSyncUser()}（即 CreateUserServiceImpl 漏斗，
 * 棒3 已接 TenantResolver——租户形态解析不到归属的身份预检跳过 fail-closed）。
 * 桥只拉数不写表（M-5），落库编排单点在此。
 *
 * <p>写法沿用原 bidr 侧同步链（MdmJobService.syncMdmDept/syncMdmAccount → DeptInitService/
 * UserAccountService）的<b>全量替换</b>语义：delete＋saveBatch（部门/账号主键均为外部业务键
 * dept_id/id，重建后引用稳定），整体单事务、失败即回滚不落半截快照；两条腿各带空快照护栏
 * （空列表不动既有组织树，原链无此护栏属补强）。</p>
 *
 * <p>🔴 2026-11-11：TRUNCATE→DELETE。TRUNCATE 是 DDL 隐式提交，pre 实测 accountSyncUser 撞
 * ac_user.user_name 唯一键失败后事务回滚不了已清空的表（组织数据直接丢、子系统 visible 面空）；
 * DELETE 带 WHERE 参与事务，失败时部门/账号快照一起回滚，旧数据原样保留。</p>
 * DeptRow/UserRow 五要素/八要素之外的列（abbreviate/org/category 等）SPI 无源，随收编放弃、
 * 重建后为空（登记于棒4报告"已知数据缩减"）。</p>
 *
 * <p>同包族裁定：与 TenantBindOnLoginService 同属 authorization 的 service 层（任务书口径
 * "core authorization 与 TenantBindOnLoginService 同包族"），包名 service.identity 自立子域。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgSyncApplier {

    private final AcDeptService acDeptService;
    private final AcAccountService acAccountService;
    private final AccountInitService accountInitService;

    /**
     * 全量应用一次组织同步结果（单事务）。空腿护栏见类注释；账号腿落库后即走
     * accountSyncUser 建号/挂部门更新漏斗（读 ac_account active 行，与本次落库同事务可见）。
     * null 快照＝两腿跳过、漏斗照走（见方法体内注释）。
     *
     * @param result 桥侧拉平的组织快照（部门行＋用户行＋同步时间）
     */
    @Transactional(rollbackFor = Exception.class)
    public void apply(OrgSyncResult result) {
        if (result == null) {
            // null 快照（桥侧拉数失败的异常形态）：两腿落库全跳过（不清任何表），建号漏斗照走——
            // 漏斗是"读库对齐"的幂等腿，照原链语义每趟执行，与快照成败无关
            log.warn("[组织同步]同步结果为 null，跳过两腿落库（护栏：不清任何表），建号漏斗照走");
            accountInitService.accountSyncUser();
            return;
        }
        applyDepts(result);
        applyAccounts(result);
        // 建号漏斗（棒3 接好 resolver 的同一漏斗）：ac_account → ac_user 全量对齐，
        // 租户形态解析不到归属的身份预检跳过（fail-closed，见 AccountInitService 预检注释）
        accountInitService.accountSyncUser();
        log.info("[组织同步]落库完成：部门 {} 行，账号 {} 行（syncedAt={}）",
                result.depts == null ? 0 : result.depts.size(),
                result.users == null ? 0 : result.users.size(),
                result.syncedAt);
    }

    private void applyDepts(OrgSyncResult result) {
        if (FuncUtil.isEmpty(result.depts)) {
            log.warn("[组织同步]部门快照为空，跳过部门腿（护栏：半拉子数据不清空既有部门树）");
            return;
        }
        List<AcDept> depts = new ArrayList<>();
        for (OrgSyncResult.DeptRow row : result.depts) {
            depts.add(buildDept(row));
        }
        // 🔴 2026-11-11：禁止 TRUNCATE——它是 DDL 隐式提交，后续 accountSyncUser 一旦失败
        // （pre 实测：SSO 账号 user_name 唯一键冲突）事务回滚救不回已清空的表，组织数据直接丢。
        // 用带 WHERE 的 DELETE（DML，参与事务）⇒ 失败整批回滚，既有部门树原样保留。
        acDeptService.delete(new LambdaQueryWrapper<AcDept>().isNotNull(AcDept::getDeptId));
        acDeptService.saveBatch(depts);
    }

    private void applyAccounts(OrgSyncResult result) {
        if (FuncUtil.isEmpty(result.users)) {
            log.warn("[组织同步]账号快照为空，跳过账号腿（护栏：半拉子数据不清空既有账号）");
            return;
        }
        List<AcAccount> accounts = new ArrayList<>();
        for (OrgSyncResult.UserRow row : result.users) {
            accounts.add(buildAccount(row));
        }
        // 同部门腿：DELETE 参与事务，accountSyncUser 失败时账号快照也一起回滚，不落半截/空表
        acAccountService.delete(new LambdaQueryWrapper<AcAccount>().isNotNull(AcAccount::getId));
        acAccountService.saveBatch(accounts);
    }

    private AcDept buildDept(OrgSyncResult.DeptRow row) {
        AcDept dept = new AcDept();
        dept.setDeptId(row.externalId);
        dept.setPid(row.parentExternalId);
        dept.setName(row.name);
        dept.setLeader(row.leader);
        dept.setShowOrder(row.showOrder);
        // 原 activeStatus 字典源 SPI 无源，统一"已启用"（getDepartmentByStatus 等消费面口径不变）
        dept.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        return dept;
    }

    private AcAccount buildAccount(OrgSyncResult.UserRow row) {
        AcAccount account = new AcAccount();
        account.setId(row.externalId);
        account.setName(row.displayName);
        account.setUserName(row.username);
        account.setDepartment(row.deptExternalId);
        account.setEmail(row.email);
        account.setMobile(row.mobile);
        account.setPictureLink(row.avatar);
        // 在职腿：active→YES，其余（resigned/frozen/null，SPI 允许 null）→NO——与原链 BoolDict 口径一致
        account.setEmployStatus(FuncUtil.equals(row.status, "active")
                ? BoolDict.YES.getValue() : BoolDict.NO.getValue());
        // 启用腿：frozen→停用（selectActive 建号链不再捞起），其余→已启用
        account.setStatus(FuncUtil.equals(row.status, "frozen")
                ? ActiveStatusDict.DEACTIVATE.getValue() : ActiveStatusDict.ACTIVATE.getValue());
        return account;
    }
}
