package com.bidr.authorization.service.tenant;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.mapper.AcUserDao;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.exception.ServiceException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Title: AcTenantPortalServiceTest
 * Description: 租户管理面判权收口的离线守卫（批乙棒4）——纯 JUnit4 不起 Spring、不连库：
 * <ul>
 * <li>判权矩阵（腿 1~4）直接喂 {@link AcTenantPortalService#assertTenantAdminGranted} 纯内核：
 * 平台放行／本租户 is_admin 放行／异租户拒（AC_TENANT_NOT_MEMBER）／成员拒（AC_IS_NOT_ADMIN），
 * 且必须断到 ServiceException 携带的<b>具体错误码</b>——防"抛了个通用错"的假针；</li>
 * <li>写入路径校验（腿 5~8）：租户有效／用户存在且有效／成员行三态／code 查重；</li>
 * <li>现查行为针（腿 9）：stub 仓 + AccountContext 走 {@code guardTenantAdmin} 全链，
 * <b>同一 claims、仅翻转关系表 fixture</b>，两次动作判决必须翻转——证明每次动作现查表、
 * 判权不吃登录烤定的身份数据（M-11 时效纪律的行为面）；</li>
 * <li>负对照源码针（腿 10）：service 源码文本禁止出现 TenantContext／getTenantIds 消费，
 * 判定函数自身用合成样本自证能抓（照 AuthAdminGateConventionTest 腿 3 的针自证纪律），
 * 并断正针 getRelation 现查口确在源码里（防把现查删了后 grep 反而"干净"的假绿）。</li>
 * </ul>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class AcTenantPortalServiceTest {

    private static final Long TENANT_ID = 7L;
    private static final Long OTHER_TENANT_ID = 9L;
    private static final Long USER_ID = 100L;
    private static final String OPERATOR = "E001";

    @Before
    public void setUp() {
        AccountContext.remove();
    }

    @After
    public void tearDown() {
        AccountContext.remove();
    }

    // ==================== 夹具 ====================

    private static AcUserTenant membership(boolean isAdmin) {
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(USER_ID);
        relation.setTenantId(TENANT_ID);
        relation.setIsAdmin(isAdmin);
        return relation;
    }

    private static AcUser user(String valid) {
        AcUser user = new AcUser();
        user.setUserId(USER_ID);
        user.setCustomerNumber(OPERATOR);
        user.setValid(valid);
        return user;
    }

    private static AcTenant tenant(Long id, String valid) {
        AcTenant tenant = new AcTenant();
        tenant.setId(id);
        tenant.setName("测试租户");
        tenant.setValid(valid);
        return tenant;
    }

    /** 取 ServiceException 的错误码；未抛错返回 null（各腿自行断言 null/具体码） */
    private static AccountErrCode denialCode(Runnable action) {
        try {
            action.run();
            return null;
        } catch (ServiceException e) {
            Assert.assertTrue("抛出的 ServiceException 应携带 AccountErrCode 枚举",
                    e.getErrCode() instanceof AccountErrCode);
            return (AccountErrCode) e.getErrCode();
        }
    }

    // ==================== 判权矩阵（腿 1~4） ====================

    /** 腿 1：平台档放行——无成员关系行也必须放行（AuthAdmin 或字段的平台腿） */
    @Test
    public void platformTierPassesWithoutAnyMembership() {
        Assert.assertNull("平台档不得拒绝",
                denialCode(() -> AcTenantPortalService.assertTenantAdminGranted(true, null)));
    }

    /** 腿 2：本租户 is_admin 放行 */
    @Test
    public void tenantAdminPassesOnLiveAdminMembership() {
        Assert.assertNull("本租户管理员不得拒绝",
                denialCode(() -> AcTenantPortalService.assertTenantAdminGranted(false, membership(true))));
    }

    /** 腿 3：异租户（现查无关系行）拒——必须拒在 AC_TENANT_NOT_MEMBER，不是别的错 */
    @Test
    public void foreignUserDeniedAsNotMember() {
        Assert.assertEquals(AccountErrCode.AC_TENANT_NOT_MEMBER,
                denialCode(() -> AcTenantPortalService.assertTenantAdminGranted(false, null)));
    }

    /** 腿 4：成员但非管理员拒——必须拒在 AC_IS_NOT_ADMIN */
    @Test
    public void plainMemberDeniedAsNotAdmin() {
        Assert.assertEquals(AccountErrCode.AC_IS_NOT_ADMIN,
                denialCode(() -> AcTenantPortalService.assertTenantAdminGranted(false, membership(false))));
    }

    // ==================== 写入路径校验（腿 5~8） ====================

    /** 腿 5：租户有效断言——null 与 valid='0' 都拒（AC_TENANT_NOT_AVAILABLE），有效行放行 */
    @Test
    public void tenantMustExistAndBeValid() {
        Assert.assertEquals(AccountErrCode.AC_TENANT_NOT_AVAILABLE,
                denialCode(() -> AcTenantPortalService.assertValidTenant(null)));
        Assert.assertEquals("停用租户（valid=0）必须拒", AccountErrCode.AC_TENANT_NOT_AVAILABLE,
                denialCode(() -> AcTenantPortalService.assertValidTenant(tenant(TENANT_ID, CommonConst.NO))));
        Assert.assertNull("有效租户不得拒",
                denialCode(() -> AcTenantPortalService.assertValidTenant(tenant(TENANT_ID, CommonConst.YES))));
    }

    /** 腿 6：用户存在且有效断言——null 与 valid='0' 都拒（AC_USER_NOT_EXISTED） */
    @Test
    public void userMustExistAndBeValid() {
        Assert.assertEquals(AccountErrCode.AC_USER_NOT_EXISTED,
                denialCode(() -> AcTenantPortalService.assertValidUser(null)));
        Assert.assertEquals("无效用户（valid=0）必须拒", AccountErrCode.AC_USER_NOT_EXISTED,
                denialCode(() -> AcTenantPortalService.assertValidUser(user(CommonConst.NO))));
        Assert.assertNull("有效用户不得拒",
                denialCode(() -> AcTenantPortalService.assertValidUser(user(CommonConst.YES))));
    }

    /** 腿 7：成员行三态——移/切要求行在（不在拒 NOT_MEMBER），加要求行不在（在拒 ALREADY_EXISTED） */
    @Test
    public void membershipPresenceGuardsWritePaths() {
        Assert.assertEquals(AccountErrCode.AC_TENANT_NOT_MEMBER,
                denialCode(() -> AcTenantPortalService.assertMembershipPresent(null)));
        Assert.assertNull("有效成员行必须放行切换",
                denialCode(() -> AcTenantPortalService.assertMembershipPresent(membership(false))));
        Assert.assertEquals(AccountErrCode.AC_TENANT_MEMBER_ALREADY_EXISTED,
                denialCode(() -> AcTenantPortalService.assertMembershipAbsent(membership(false))));
        Assert.assertNull("非成员可以加",
                denialCode(() -> AcTenantPortalService.assertMembershipAbsent(null)));
    }

    /** 腿 8：code 查重——命中他人有效行拒；命中自身（改租户豁免）与未命中放行 */
    @Test
    public void codeUniquenessCheck() {
        Assert.assertEquals(AccountErrCode.AC_TENANT_CODE_ALREADY_EXISTED,
                denialCode(() -> AcTenantPortalService.assertCodeFree(tenant(OTHER_TENANT_ID, CommonConst.YES), TENANT_ID)));
        Assert.assertNull("建租户（selfId=null）未命中必须放行",
                denialCode(() -> AcTenantPortalService.assertCodeFree(null, null)));
        Assert.assertNull("改租户命中自身行必须豁免",
                denialCode(() -> AcTenantPortalService.assertCodeFree(tenant(TENANT_ID, CommonConst.YES), TENANT_ID)));
    }

    // ==================== 现查行为针（腿 9 / 腿 9b·修1） ====================

    /** 可脚本化的关系仓 stub：getRelation 从 fixture 取数（不触库） */
    private static class StubUserTenantService extends AcUserTenantService {
        private AcUserTenant scripted;

        @Override
        public AcUserTenant getRelation(Long userId, Long tenantId) {
            return scripted;
        }
    }

    /** operator 工号→userId 解析 stub（修1 后 guard 走 AcUserDao 旁路口，不再走 AcUserService 通用路径） */
    private static AcUserDao stubUserDao() {
        AcUserDao dao = Mockito.mock(AcUserDao.class);
        Mockito.when(dao.selectTenantPortalUser(Mockito.isNull(), Mockito.anyString()))
                .thenReturn(user(CommonConst.YES));
        Mockito.when(dao.selectTenantPortalUser(Mockito.eq(USER_ID), Mockito.isNull()))
                .thenReturn(user(CommonConst.YES));
        return dao;
    }

    /** 租户仓 stub：selectById 从 fixture 取数（修1 起 guardTenantAdmin 上提 assertValidTenant，R-C M-1） */
    private static AcTenantService stubTenantRepo(String valid) {
        AcTenantService repo = Mockito.mock(AcTenantService.class);
        Mockito.when(repo.selectById(TENANT_ID)).thenReturn(tenant(TENANT_ID, valid));
        return repo;
    }

    /** 修3 起管理面不再有 AcUserService 通用消费（listMembers 展示读也走 dao 旁路口，R-C C-1） */
    private static AcTenantPortalService serviceFor(AcTenantService tenantRepo,
                                                    StubUserTenantService relationRepo) {
        return new AcTenantPortalService(tenantRepo, relationRepo, null, stubUserDao()) {
            @Override
            boolean isPlatformTier() {
                return false;
            }
        };
    }

    /**
     * 腿 9：同一登录态（claims 全程不变）、同一平台档，仅翻转 ac_user_tenant 的 is_admin fixture，
     * 两次动作判决必须翻转——证明 {@code guardTenantAdmin} 每次动作<b>现查</b>关系表，
     * is_admin 变更即时生效（M-11 时效纪律的行为面，与腿 10 的源码面负对照成对）。
     */
    @Test
    public void guardRequeriesMembershipPerAction() {
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setCustomerNumber(OPERATOR);
        AccountContext.set(accountInfo);

        StubUserTenantService stubRelationRepo = new StubUserTenantService();
        AcTenantPortalService service = serviceFor(stubTenantRepo(CommonConst.YES), stubRelationRepo);

        // 第一枪：现查得 is_admin=true ⇒ 放行
        stubRelationRepo.scripted = membership(true);
        Assert.assertNull("管理员首动作不得拒",
                denialCode(() -> service.guardTenantAdmin(TENANT_ID)));

        // 第二枪：claims 不动，仅关系表被改成普通成员 ⇒ 必须立即拒
        stubRelationRepo.scripted = membership(false);
        Assert.assertEquals("is_admin 被撤后同身份再动作必须即时拒（证明现查表而非吃烤定身份）",
                AccountErrCode.AC_IS_NOT_ADMIN,
                denialCode(() -> service.guardTenantAdmin(TENANT_ID)));

        // 第三枪：关系行整条消失（移成员）⇒ 拒在 NOT_MEMBER
        stubRelationRepo.scripted = null;
        Assert.assertEquals("成员被移后同身份再动作必须即时拒",
                AccountErrCode.AC_TENANT_NOT_MEMBER,
                denialCode(() -> service.guardTenantAdmin(TENANT_ID)));
    }

    /**
     * 腿 9b（修1 R-C M-1）：停用租户在 guardTenantAdmin 统一关闸——即便身份腿是管理员，
     * valid≠'1' 的租户也必须拒在 AC_TENANT_NOT_AVAILABLE（list/remove/授admin 四条 /my 行为一致）。
     * 负对照：同 fixture 换有效租户 ⇒ 放行（证明拒因是租户有效性而非夹具坏掉）。
     */
    @Test
    public void disabledTenantClosedForAllMyActions() {
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setCustomerNumber(OPERATOR);
        AccountContext.set(accountInfo);

        StubUserTenantService stubRelationRepo = new StubUserTenantService();
        stubRelationRepo.scripted = membership(true);

        AcTenantPortalService disabledService = serviceFor(stubTenantRepo(CommonConst.NO), stubRelationRepo);
        Assert.assertEquals("停用租户必须被 guard 统一关闸",
                AccountErrCode.AC_TENANT_NOT_AVAILABLE,
                denialCode(() -> disabledService.guardTenantAdmin(TENANT_ID)));

        AcTenantPortalService validService = serviceFor(stubTenantRepo(CommonConst.YES), stubRelationRepo);
        Assert.assertNull("负对照失效：有效租户同身份被拒（夹具判据坏了）",
                denialCode(() -> validService.guardTenantAdmin(TENANT_ID)));
    }

    // ==================== 负对照源码针（腿 10） ====================

    /** claims 消费判定函数：源码文本出现 TenantContext 或 getTenantIds 即视为消费 */
    private static boolean consumesTenantClaims(String source) {
        return source.contains("TenantContext") || source.contains("getTenantIds");
    }

    /**
     * 腿 10：service 源码禁止消费登录烤定的租户 claims（判权唯一合法取数＝现查关系表）。
     * <p>自证纪律：判定函数喂合成消费样本必须判红（防恒假绿）；正针断现查口
     * {@code getRelation(} 确在源码（防"把现查删了自然没有 claims"的假绿）。</p>
     */
    @Test
    public void serviceSourceMustNotConsumeTenantClaims() throws IOException {
        Path moduleRoot = Paths.get(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        Path source = moduleRoot.resolve(
                "src/main/java/com/bidr/authorization/service/tenant/AcTenantPortalService.java");
        Assert.assertTrue("service 源文件定位失效（针会空转，拒绝假绿）: " + source, Files.isRegularFile(source));
        String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

        Assert.assertFalse("AcTenantPortalService 不得消费 claims 侧租户档（TenantContext/getTenantIds）",
                consumesTenantClaims(text));
        // 正针：现查关系表的取数口必须在
        Assert.assertTrue("判权现查口 getRelation 不在源码里（现查被删＝本针失去意义）",
                text.contains("getRelation("));

        // 判定函数自证（负对照）：合成消费样本必须被抓
        Assert.assertTrue("claims 消费判定函数失效：合成 TenantContext 样本未判红",
                consumesTenantClaims("import com.bidr.authorization.holder.TenantContext;\n"
                        + "if (TenantContext.isPlatform()) { allow(); }"));
        Assert.assertTrue("claims 消费判定函数失效：合成 getTenantIds 样本未判红",
                consumesTenantClaims("List<Long> ids = accountInfo.getTenantIds();"));
        // 判定函数不误伤：不含两 token 的合法源码判绿
        Assert.assertFalse("claims 消费判定函数过宽：普通源码文本被误判",
                consumesTenantClaims("Validator.assertNotNull(membership, AccountErrCode.AC_TENANT_NOT_MEMBER);"));
    }

    /**
     * 腿 11（修3，R-C C-1）：listMembers 的展示读必须走 dao 旁路口——平台档 claims 不含被管租户时
     * 成员姓名列仍非 null。判据源＝被闸改写的通用批量口会让"建租户→加成员（写成功）→成员列表
     * 姓名为空"在主流程命中；本腿用 Mockito 断言 dao 旁路口被按 userId 逐条调用、且通用
     * AcUserService.select 一次都不碰（夹具已无该字段＝构造即四参，编译期天然钉死）。
     */
    @Test
    public void listMembersResolvesDisplayColumnsViaBypassDao() {
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setCustomerNumber(OPERATOR);
        AccountContext.set(accountInfo);

        AcUser member = new AcUser();
        member.setUserId(777L);
        member.setName("张三");
        member.setUserName("zhangsan");
        member.setValid(CommonConst.YES);

        AcUserDao dao = Mockito.mock(AcUserDao.class);
        // guard 的 operator 解析腿
        Mockito.when(dao.selectTenantPortalUser(Mockito.isNull(), Mockito.anyString()))
                .thenReturn(user(CommonConst.YES));
        // listMembers 展示读：只有旁路口被喂 userId 才拿得到姓名
        Mockito.when(dao.selectTenantPortalUser(777L, null)).thenReturn(member);

        AcUserTenantService relationRepo = Mockito.mock(AcUserTenantService.class);
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(777L);
        relation.setTenantId(TENANT_ID);
        Mockito.when(relationRepo.listByTenantId(TENANT_ID))
                .thenReturn(java.util.Collections.singletonList(relation));

        AcTenantPortalService service = new AcTenantPortalService(
                stubTenantRepo(CommonConst.YES), relationRepo, null, dao) {
            @Override
            boolean isPlatformTier() {
                return true;
            }
        };
        java.util.List<java.util.Map<String, Object>> rows = service.listMembers(TENANT_ID);

        Assert.assertEquals("成员清单必须一行一条", 1, rows.size());
        Assert.assertEquals("展示列必须经旁路口解析出姓名（非闸内批量口的静默 null）", "张三", rows.get(0).get("name"));
        Mockito.verify(dao).selectTenantPortalUser(777L, null);
    }
}
