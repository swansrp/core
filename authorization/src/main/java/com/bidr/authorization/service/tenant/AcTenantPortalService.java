package com.bidr.authorization.service.tenant;

import com.bidr.authorization.annotation.auth.AuthAdmin;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.mapper.AcUserDao;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AcTenantPortalService
 * Description: 租户管理面服务（批乙棒4）——平台档面（目录/建/改/停用）＋本租户面（成员清单/加/移/切管理员）。
 * <p>🔴 判权唯一收口在本类（§4a 裁定 2）：租户管理员动作＝AuthAdmin 语义（平台档角色比对，腿见
 * {@link #isPlatformTier()}）或<b>现查</b> ac_user_tenant 关系行的 is_admin（{@link #guardTenantAdmin(Long)}）。
 * M-11 时效纪律：成员/管理员身份每次动作都现查关系表，绝不消费登录时烤进 token 的租户 claims 做判定。</p>
 * <p>纯判权内核收成一族 static 断言函数（{@code assertXxx}），不起 Spring 即可直测，
 * 形态照 {@code AcSsoRedirectServiceTest} 的离线判据路线。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class AcTenantPortalService {

    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;
    private final SysConfigCacheService sysConfigCacheService;
    /** 🔴 portal 专用 ac_user 取数口（挂 @InterceptorIgnore，M-24）——管理面所有 ac_user 读点
     * （guard operator 解析、addMember 目标查在否、listMembers 展示列）都走它，
     * 不经被租户闸按登录 claims 改写的通用 select/selectById 路径 */
    private final AcUserDao acUserDao;

    // ==================== 平台档面（调用方已由类级 AuthAdmin 挂闸） ====================

    /**
     * 租户目录列表（有效租户全量）。
     */
    public List<AcTenant> listTenants() {
        return acTenantService.listValid();
    }

    /**
     * 租户详情（含已停用行——管理面要能回看停用租户）。
     */
    public AcTenant getTenantDetail(Long id) {
        Validator.assertNotNull(id, ErrCodeSys.PA_PARAM_NULL, "租户id");
        AcTenant tenant = acTenantService.selectById(id);
        Validator.assertNotNull(tenant, ErrCodeSys.PA_DATA_NOT_EXIST, "租户");
        return tenant;
    }

    /**
     * 建租户：code 非空时先做有效行查重，命中即拒（AC_TENANT_CODE_ALREADY_EXISTED）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AcTenant createTenant(String name, String code) {
        Validator.assertNotBlank(name, ErrCodeSys.PA_PARAM_NULL, "租户名称");
        String trimmedCode = FuncUtil.isNotEmpty(code) ? code.trim() : null;
        assertCodeFree(acTenantService.getByCode(trimmedCode), null);
        AcTenant tenant = new AcTenant();
        String operator = AccountContext.getOperator();
        Date now = new Date();
        tenant.setName(name);
        tenant.setCode(trimmedCode);
        tenant.setValid(CommonConst.YES);
        tenant.setCreateBy(operator);
        tenant.setCreateAt(now);
        tenant.setUpdateBy(operator);
        tenant.setUpdateAt(now);
        acTenantService.insert(tenant);
        return tenant;
    }

    /**
     * 改租户：name 必填；code 给了才改并查重（自身行豁免，见 {@link #assertCodeFree}）。
     */
    @Transactional(rollbackFor = Exception.class)
    public AcTenant updateTenant(Long id, String name, String code) {
        AcTenant tenant = getTenantDetail(id);
        Validator.assertNotBlank(name, ErrCodeSys.PA_PARAM_NULL, "租户名称");
        String trimmedCode = FuncUtil.isNotEmpty(code) ? code.trim() : null;
        assertCodeFree(acTenantService.getByCode(trimmedCode), tenant.getId());
        tenant.setName(name);
        if (FuncUtil.isNotEmpty(trimmedCode)) {
            tenant.setCode(trimmedCode);
        }
        tenant.setUpdateBy(AccountContext.getOperator());
        tenant.setUpdateAt(new Date());
        acTenantService.updateById(tenant);
        return tenant;
    }

    /**
     * 停用租户（软删 valid='0'）；不存在即拒。
     */
    public void disableTenant(Long id) {
        getTenantDetail(id);
        acTenantService.disableById(id);
    }

    // ==================== 本租户面（方法级登录档豁免进入，判权在本类收口） ====================

    /**
     * 本租户成员清单（关系行＋用户展示列；成员量级小，逐条主键查用户）。
     * <p>🔴 修3（R-C C-1，M-24 不变量补齐）：展示读走 {@code acUserDao.selectTenantPortalUser}
     * 旁路口而非 {@code acUserService} 批量 select——后者命中 ac_user 锚点会被闸按登录烤定的
     * claims 改写，平台档管非所属租户时成员姓名静默置 null（"建租户→加成员→看成员"主流程）。</p>
     */
    public List<Map<String, Object>> listMembers(Long tenantId) {
        guardTenantAdmin(tenantId);
        List<AcUserTenant> relations = acUserTenantService.listByTenantId(tenantId);
        if (FuncUtil.isEmpty(relations)) {
            return new ArrayList<>();
        }
        Map<Long, AcUser> userMap = new LinkedHashMap<>();
        for (AcUserTenant relation : relations) {
            if (!userMap.containsKey(relation.getUserId())) {
                AcUser user = acUserDao.selectTenantPortalUser(relation.getUserId(), null);
                if (user != null) {
                    userMap.put(user.getUserId(), user);
                }
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>(relations.size());
        for (AcUserTenant relation : relations) {
            AcUser user = userMap.get(relation.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenantId", relation.getTenantId());
            row.put("userId", relation.getUserId());
            row.put("isAdmin", Boolean.TRUE.equals(relation.getIsAdmin()));
            row.put("name", FuncUtil.isNotEmpty(user) ? user.getName() : null);
            row.put("userName", FuncUtil.isNotEmpty(user) ? user.getUserName() : null);
            row.put("customerNumber", FuncUtil.isNotEmpty(user) ? user.getCustomerNumber() : null);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 加成员：判权（含租户有效闸，guard 内收口）→ 用户须存在且有效（ac_user valid='1'，
     * 🔴 取数走 {@code acUserDao.selectTenantPortalUser} 旁路口——闸按登录烤定的租户档改写
     * SELECT，"单租户管理员加新人（目标仅属他租户或尚无租户）"否则永假，M-24）
     * → 不得重复成员 → 插关系行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long tenantId, Long userId, Boolean isAdmin) {
        guardTenantAdmin(tenantId);
        assertValidUser(acUserDao.selectTenantPortalUser(userId, null));
        assertMembershipAbsent(acUserTenantService.getRelation(userId, tenantId));
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(userId);
        relation.setTenantId(tenantId);
        relation.setIsAdmin(Boolean.TRUE.equals(isAdmin));
        acUserTenantService.insert(relation);
    }

    /**
     * 移成员：判权 → 关系行必须在（不在即拒）→ 物理删关系行。
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long tenantId, Long userId) {
        guardTenantAdmin(tenantId);
        assertMembershipPresent(acUserTenantService.getRelation(userId, tenantId));
        acUserTenantService.deleteRelation(userId, tenantId);
    }

    /**
     * 切租户内管理员：判权 → is_admin 只允许在有效成员行上切换（无行即拒）→ 原位更新。
     */
    @Transactional(rollbackFor = Exception.class)
    public void setMemberAdmin(Long tenantId, Long userId, Boolean isAdmin) {
        guardTenantAdmin(tenantId);
        AcUserTenant relation = assertMembershipPresent(acUserTenantService.getRelation(userId, tenantId));
        relation.setIsAdmin(Boolean.TRUE.equals(isAdmin));
        acUserTenantService.updateById(relation);
    }

    // ==================== 判权收口（§4a 裁定 2） ====================

    /**
     * 本租户面四条动作（list/add/remove/授admin）的统一闸门：租户存在且有效（R-C M-1：
     * 停用租户对四条 /my 行为一致关闸，不再只卡 addMember）→ operator 工号经
     * {@code acUserDao.selectTenantPortalUser} 专用旁路口解析为 userId（🔴 不读 claims 里的
     * 身份数据判权，取数也不走被租户闸改写的通用路径，M-24）→ {@link #assertTenantAdminGranted}
     * 两腿判权；成员身份与 is_admin <b>每次动作现查</b>关系表，变更即时生效。
     */
    void guardTenantAdmin(Long tenantId) {
        Validator.assertNotNull(tenantId, ErrCodeSys.PA_PARAM_NULL, "租户id");
        assertValidTenant(acTenantService.selectById(tenantId));
        String operator = AccountContext.getOperator();
        Validator.assertNotBlank(operator, ErrCodeSys.PA_PARAM_NULL, "操作人");
        AcUser operatorUser = assertValidUser(acUserDao.selectTenantPortalUser(null, operator));
        assertTenantAdminGranted(isPlatformTier(),
                acUserTenantService.getRelation(operatorUser.getUserId(), tenantId));
    }

    /**
     * 平台档腿：判据与 {@link AuthAdmin#validate} 收口同一静态谓词
     * {@link AuthAdmin#matchesAdminRole}（sys_config ACCOUNT_ADMIN_ROLE_ID × 角色比对，
     * R-C M-2——两处判据不许各写一份）；只回答"是不是平台管理员"，
     * 不回答成员/管理员身份（那必须现查表）。
     * <p>包可见性：测试子类改写本腿注入平台档场景，不为测试拓宽公开 API。</p>
     */
    boolean isPlatformTier() {
        Long adminRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID);
        return AuthAdmin.matchesAdminRole(AccountContext.get(), adminRoleId);
    }

    // ==================== 纯判权内核（static，脱离 Spring 可直测） ====================

    /**
     * 判权矩阵本体：平台档放行；否则必须是本租户成员且关系行 is_admin 为真。
     * <p>非本租户（无关系行）⇒ {@link AccountErrCode#AC_TENANT_NOT_MEMBER}；
     * 成员但非管理员 ⇒ {@link AccountErrCode#AC_IS_NOT_ADMIN}。</p>
     */
    static void assertTenantAdminGranted(boolean platformTier, AcUserTenant membership) {
        if (platformTier) {
            return;
        }
        Validator.assertNotNull(membership, AccountErrCode.AC_TENANT_NOT_MEMBER);
        Validator.assertTrue(Boolean.TRUE.equals(membership.getIsAdmin()), AccountErrCode.AC_IS_NOT_ADMIN);
    }

    /** 租户有效断言：null 或 valid≠'1' ⇒ 拒（写入路径闸） */
    static AcTenant assertValidTenant(AcTenant tenant) {
        Validator.assertTrue(FuncUtil.isNotEmpty(tenant) && CommonConst.YES.equals(tenant.getValid()),
                AccountErrCode.AC_TENANT_NOT_AVAILABLE);
        return tenant;
    }

    /** 用户有效断言：null 或 valid≠'1' ⇒ 拒（加成员与 operator 解析共用） */
    static AcUser assertValidUser(AcUser user) {
        Validator.assertTrue(FuncUtil.isNotEmpty(user) && CommonConst.YES.equals(user.getValid()),
                AccountErrCode.AC_USER_NOT_EXISTED);
        return user;
    }

    /** 成员行必须存在断言（移成员/切管理员/守卫取数） */
    static AcUserTenant assertMembershipPresent(AcUserTenant membership) {
        Validator.assertNotNull(membership, AccountErrCode.AC_TENANT_NOT_MEMBER);
        return membership;
    }

    /** 加成员查重：已是成员 ⇒ 拒 */
    static void assertMembershipAbsent(AcUserTenant membership) {
        Validator.assertNull(membership, AccountErrCode.AC_TENANT_MEMBER_ALREADY_EXISTED);
    }

    /**
     * code 查重：命中有效行且不是自己（改租户时 selfId 传入自身 id；建租户传 null）⇒ 拒。
     */
    static void assertCodeFree(AcTenant codeOwner, Long selfId) {
        Validator.assertFalse(FuncUtil.isNotEmpty(codeOwner) && !FuncUtil.equals(codeOwner.getId(), selfId),
                AccountErrCode.AC_TENANT_CODE_ALREADY_EXISTED);
    }
}
