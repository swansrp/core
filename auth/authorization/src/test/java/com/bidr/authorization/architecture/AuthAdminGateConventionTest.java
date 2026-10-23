package com.bidr.authorization.architecture;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: AuthAdminGateConventionTest
 * Description: {@code @Auth(AuthAdmin.class)} 防撤闸守卫（离线：只扫磁盘源码文本，不起 Spring、不连库）。
 * <p>
 * 本守卫是<b>点名式防撤闸</b>：admin 与 authorization 两模块里已挂该闸的 controller 各列一份清单，
 * 逐个断言注解字面量仍在——谁把闸撤了当场红。判定只看文件文本是否含注解字面量，不解析 AST。
 * </p>
 * <p>
 * 🔴 范围裁定（2026-10-23）：admin 模块其余 controller（报表配置 {@code AdminPortalController}、
 * 用户/部门/角色管理面、各 *PortalController 等）<b>不要求</b> AuthAdmin——原判据"admin 全部具体
 * controller 不得裸奔"与磁盘实况不符（实况仅 4 个挂闸）。代价说清楚：<b>新增一个不带闸的
 * controller 不再被本守卫拦</b>，那层约束回到人工评审。
 * </p>
 * <p>
 * 两条<b>已知豁免</b>（有注解形态证据，不在守卫里开白名单，见类注释即可核对）：
 * ① {@code AcPermitApplyPortalController} 类级不挂，按端点拆——{@code GET ""}／{@code /approve}／
 * {@code /reject} 方法级 AuthAdmin，{@code POST ""}（自助申请）与 {@code GET /my}（登录档查自己）
 * 保持登录态，故对它只要求"至少一处"而非类级；② {@code GroupBindController}／
 * {@code GroupUserBindController} 本身不挂 AuthAdmin，其本人端点以方法级 {@code @Auth(AuthLogin.class)}
 * 放行，腿 2 对这两个文件额外断言该豁免针仍在；③（批乙修1 增补）
 * {@code AcTenantPortalController} 类级 AuthAdmin 之下 {@code /my/*} 四条端点方法级 AuthLogin
 * 豁免（判权收口在 AcTenantPortalService.guardTenantAdmin），同入 LOGIN_EXEMPT 清单，
 * 并由新增两针钉死豁免注解计数＝4 与 service guard 调用点＝4（防漂移，R-C M-3）。
 * </p>
 * <p>
 * 🔴 针自证纪律（判据必须证明自己活着）：腿 3 喂合成"无注解 controller"文本给同一判定函数、
 * 断言判红；腿 4 用既有真闸 {@code AcSsoRedirectPortalController} 与合成合规样本判绿——两份点名
 * 清单各有条目数下限（admin ≥ 4、auth ≥ 8），防清单被清空后假绿。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/16
 */
public class AuthAdminGateConventionTest {

    /** 裸奔闸针：类级或方法级至少一处出现此注解字面量即视为挂闸 */
    private static final String ADMIN_GATE_NEEDLE = "@Auth(" + "AuthAdmin.class)";
    /** 反向豁免针：GroupBind/GroupUserBind 本人端点在类级 AuthAdmin 下以方法级 AuthLogin 豁免 */
    private static final String LOGIN_EXEMPT_NEEDLE = "@Auth(" + "AuthLogin.class)";

    /**
     * auth 模块点名清单（相对 authorization 模块根）＝磁盘上确实挂了闸的那批。
     * 10-23 起按实况收口：原 17 条里 {@code AdminUserRoleBind}／{@code AdminRoleUserBind}／
     * {@code AdminUserMenuBind}／{@code AdminUserDeptBind}／{@code AdminRole}／{@code AdminRoleMenuBind}／
     * {@code GroupAdmin}／{@code GroupBind}／{@code GroupUserBind} 共 9 条并无 AuthAdmin，已移出本单
     * （GroupBind/GroupUserBind 仍留在下面的 AuthLogin 豁免腿里）。
     * {@code PasswordController} 是方法级（/reset 挂闸、/change 走本人校验），判定同样只看字面量，无需区分层级。
     */
    private static final List<String> AUTH_NAMED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/admin/AdminDeptMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminGroupMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminDeptController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcResourcePermController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminMenuController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminPartnerController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcTenantPortalController.java",
            "src/main/java/com/bidr/authorization/controller/login/PasswordController.java"));

    /** admin 模块点名清单（相对 admin 模块根）＝磁盘上确实挂了闸的那批（10-23 实况 4 条） */
    private static final List<String> ADMIN_NAMED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/admin/manage/account/controller/AdminAccountController.java",
            "src/main/java/com/bidr/admin/manage/partner/controller/AcPartnerHistoryPortalController.java",
            "src/main/java/com/bidr/admin/manage/permit/controller/AcPermitApplyPortalController.java",
            "src/main/java/com/bidr/admin/manage/sso/controller/AcSsoRedirectPortalController.java"));

    /** 反向豁免腿：这三个类必须保有 AuthLogin 方法级豁免针（GroupBind 两条本身不挂 AuthAdmin） */
    private static final List<String> LOGIN_EXEMPT_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/group/GroupBindController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupUserBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcTenantPortalController.java"));

    /** 正对照：admin 模块里批甲之前就挂了 AuthAdmin 的既有真闸（相对 admin 模块根） */
    private static final String PREEXISTING_GATE_CONTROL =
            "src/main/java/com/bidr/admin/manage/sso/controller/AcSsoRedirectPortalController.java";

    /**
     * 从 basedir/user.dir 起向上找 authorization 模块根（判据与
     * {@link AuthCoreBoundaryConventionTest} 同思路：只认目录名＋pom 坐标＋src/main/java 存在），
     * admin 模块根取其兄弟目录——surefire 工作目录即模块基目录，两层定位都失败直接报红不假绿。
     */
    private static Path locateAuthorizationModule() {
        Path start = Paths.get(System.getProperty("basedir",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if ("authorization".equals(dir.getFileName().toString())
                    && Files.isRegularFile(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
        }
        throw new IllegalStateException("无法从 " + start + " 定位 core/authorization 模块根（路径假设已坏，拒绝空扫描）");
    }

    /** authorization 的兄弟目录＝admin 模块根，定位后自证坐标，防兄弟改名后扫错树 */
    private static Path locateAdminModule() {
        Path admin = locateAuthorizationModule().getParent().resolve("admin");
        Assert.assertTrue("admin 模块根定位失效（应有 pom.xml 与 src/main/java）: " + admin,
                Files.isRegularFile(admin.resolve("pom.xml"))
                        && Files.isDirectory(admin.resolve("src").resolve("main").resolve("java")));
        return admin;
    }

    /**
     * 裸奔判定函数（腿 1／3／4 共用）：controller 源码文本不含 {@link #ADMIN_GATE_NEEDLE}
     * 即返回违规描述，空列表＝合规。恒真自证见 {@code bareControllerSyntheticSampleIsJudgedBare}。
     */
    private static List<String> missingAdminGate(String fileName, String content) {
        if (content.contains(ADMIN_GATE_NEEDLE)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(fileName + " 未含 " + ADMIN_GATE_NEEDLE + "（管理面裸奔）");
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * 腿 1（admin 点名防撤闸）：清单内每个 controller 必须仍存在且仍含 {@code @Auth(AuthAdmin.class)}；
     * 清单条目数下限防"把清单清空即假绿"。
     */
    @Test
    public void adminNamedControllersKeepAuthAdminGate() throws IOException {
        Assert.assertTrue("admin 点名清单被清空（守卫失效）", ADMIN_NAMED_CONTROLLERS.size() >= 4);

        Path adminRoot = locateAdminModule();
        List<String> missing = new ArrayList<>();
        for (String relative : ADMIN_NAMED_CONTROLLERS) {
            Path file = adminRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                missing.add(relative + " 不存在（点名清单与磁盘漂移）");
                continue;
            }
            missing.addAll(missingAdminGate(relative, read(file)));
        }
        Assert.assertTrue("admin 点名 controller 挂闸断言失败：\n" + String.join("\n", missing),
                missing.isEmpty());
    }

    /**
     * 腿 2（auth 点名）：清单内 controller 逐个断言存在且挂闸；GroupBind/GroupUserBind
     * 另断言 AuthLogin 反向豁免针仍在（豁免被误删＝本人端点 403，同样是契约破坏）。
     */
    @Test
    public void authNamedControllersCarryAuthAdminGate() throws IOException {
        Path authRoot = locateAuthorizationModule();
        List<String> missing = new ArrayList<>();
        for (String relative : AUTH_NAMED_CONTROLLERS) {
            Path file = authRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                missing.add(relative + " 不存在（点名清单与磁盘漂移）");
                continue;
            }
            String content = read(file);
            if (!content.contains(ADMIN_GATE_NEEDLE)) {
                missing.add(relative + " 未含 " + ADMIN_GATE_NEEDLE);
            }
        }
        Assert.assertTrue("auth 点名 controller 挂闸断言失败（"
                        + (AUTH_NAMED_CONTROLLERS.size() - missing.size()) + "/"
                        + AUTH_NAMED_CONTROLLERS.size() + " 命中）：\n" + String.join("\n", missing),
                missing.isEmpty());

        List<String> exemptBroken = new ArrayList<>();
        for (String relative : LOGIN_EXEMPT_CONTROLLERS) {
            String content = read(authRoot.resolve(relative));
            if (!content.contains(LOGIN_EXEMPT_NEEDLE)) {
                exemptBroken.add(relative + " 丢失 " + LOGIN_EXEMPT_NEEDLE + "（本人端点反向豁免被误删）");
            }
        }
        Assert.assertTrue("AuthLogin 反向豁免腿失败：\n" + String.join("\n", exemptBroken),
                exemptBroken.isEmpty());
    }

    /**
     * 腿 3（合成负对照，针自证）：无任何 @Auth 注解的 controller 文本喂给腿 1 同一判定函数，
     * 必须判违规——证明判定函数真抓得出裸奔，而非恒真空转。
     */
    @Test
    public void bareControllerSyntheticSampleIsJudgedBare() {
        String bare = "public class SampleController {\n"
                + "    @GetMapping(\"/list\")\n"
                + "    public List<String> list() { return Collections.emptyList(); }\n"
                + "}\n";
        Assert.assertFalse("合成样本误含闸针（负对照失效）", bare.contains(ADMIN_GATE_NEEDLE));
        Assert.assertFalse("负对照失效：裸奔样本被判合规，判定函数已恒真",
                missingAdminGate("SampleController.java", bare).isEmpty());
    }

    /**
     * 腿 4（正对照）：既有真闸 AcSsoRedirectPortalController 与合成合规样本都必须通过
     * 腿 1 同款判定——磁盘正对照防路径漂移后扫错树，合成样本防针字面量写坏。
     */
    @Test
    public void positiveControlsPassTheSameGateCheck() throws IOException {
        Path control = locateAdminModule().resolve(PREEXISTING_GATE_CONTROL);
        Assert.assertTrue("正对照文件不存在: " + control, Files.isRegularFile(control));
        Assert.assertTrue("正对照失效：既有真闸 AcSsoRedirectPortalController 被判裸奔",
                missingAdminGate(PREEXISTING_GATE_CONTROL, read(control)).isEmpty());

        String compliant = "public class SampleController {\n"
                + "    " + ADMIN_GATE_NEEDLE + "\n"
                + "    @GetMapping(\"/list\")\n"
                + "    public List<String> list() { return Collections.emptyList(); }\n"
                + "}\n";
        Assert.assertTrue("合成合规样本未含闸针（正对照样本失效）", compliant.contains(ADMIN_GATE_NEEDLE));
        Assert.assertTrue("正对照失效：合规样本被判违规",
                missingAdminGate("SampleController.java", compliant).isEmpty());
    }

    // ==================== 批乙修1 新增：/my 豁免与判权收口的防漂移针（R-C M-3） ====================

    /** 行首注解/调用计数（trim 后以正则整行匹配）——javadoc 里的 {@code @Auth(AuthLogin.class)} 提及不算 */
    private static int countMatchingLines(String content, String lineRegex) {
        int count = 0;
        for (String line : content.split("\n")) {
            if (line.trim().matches(lineRegex)) {
                count++;
            }
        }
        return count;
    }

    private static final String TENANT_PORTAL_CONTROLLER =
            "src/main/java/com/bidr/authorization/controller/admin/AcTenantPortalController.java";
    private static final String TENANT_PORTAL_SERVICE =
            "src/main/java/com/bidr/authorization/service/tenant/AcTenantPortalService.java";

    /**
     * 针 1：AcTenantPortalController 的方法级 {@code @Auth(AuthLogin.class)} 豁免出现次数必须＝4
     * （/my 四条端点；类级 AuthAdmin 之下的反向豁免端点每加/删一条都要显式改这里）。
     * 自证：计数函数喂合成 2 行样本数得出 2、喂空文本数 0（防空转恒 0/恒真）。
     */
    @Test
    public void tenantPortalControllerLoginExemptionCountPinned() throws IOException {
        Path controller = locateAuthorizationModule().resolve(TENANT_PORTAL_CONTROLLER);
        Assert.assertTrue("controller 定位失效（针会空转）: " + controller, Files.isRegularFile(controller));
        String content = read(controller);
        int exemptions = countMatchingLines(content, "@Auth\\(AuthLogin\\.class\\)");
        Assert.assertEquals("AcTenantPortalController 的 @Auth(AuthLogin.class) 豁免端点数漂移（裁定＝/my 四条）",
                4, exemptions);
        // 针自证：正/负合成样本
        Assert.assertEquals("计数函数失效（正样本数不出）", 2, countMatchingLines(
                "public class C {\n    @Auth(AuthLogin.class)\n    @Auth(AuthLogin.class)\n}\n",
                "@Auth\\(AuthLogin\\.class\\)"));
        Assert.assertEquals("计数函数过宽（javadoc 提及被算进）", 0, countMatchingLines(
                "/** {@code @Auth(AuthLogin.class)} 说明 */\npublic class C {}\n",
                "@Auth\\(AuthLogin\\.class\\)"));
    }

    /**
     * 针 2：AcTenantPortalService 的 {@code guardTenantAdmin(tenantId);} 调用点必须＝4
     * （list/add/remove/授admin 四条 /my 行为同一闸门，R-C M-1 上提后口径；新增旁开闸门的
     * 本租户端点或漏挂 guard 都会被抓）。自证同上：合成样本能数出、定义行不算调用。
     */
    @Test
    public void tenantPortalServiceGuardCallSitesPinned() throws IOException {
        Path service = locateAuthorizationModule().resolve(TENANT_PORTAL_SERVICE);
        Assert.assertTrue("service 定位失效（针会空转）: " + service, Files.isRegularFile(service));
        String content = read(service);
        int calls = countMatchingLines(content, "guardTenantAdmin\\(tenantId\\);");
        Assert.assertEquals("AcTenantPortalService 的 guardTenantAdmin 调用点漂移（判权唯一收口＝四条 /my 全过闸）",
                4, calls);
        // 针自证：定义行不被算进 + 合成调用数得出
        Assert.assertEquals("定义行被误算", 0, countMatchingLines(
                "void guardTenantAdmin(Long tenantId) {\n}\n", "guardTenantAdmin\\(tenantId\\);"));
        Assert.assertEquals("合成调用数不出（判定恒 0 空转）", 3, countMatchingLines(
                "guardTenantAdmin(tenantId);\n  guardTenantAdmin(tenantId);\n    guardTenantAdmin(tenantId);\n",
                "guardTenantAdmin\\(tenantId\\);"));
    }
}
