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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Title: AuthAdminGateConventionTest
 * Description: 批甲裸奔闸收口守卫（离线：只扫磁盘源码文本，不起 Spring、不连库）。
 * <p>
 * 批甲给 auth/admin 两模块的写面与管理面挂了 {@code @Auth(AuthAdmin.class)}。本守卫把"新增
 * controller 不得裸奔"钉成可执行约定：admin 模块全部具体 controller 必须至少一处出现该注解
 * （类级或方法级均可），auth 模块写面 controller 按点名清单逐个断言。判定只看文件文本是否含
 * 注解字面量，不解析 AST——够抓"忘挂闸"，不追求精确。
 * </p>
 * <p>
 * 两条<b>已知豁免</b>（有注解形态证据，不在守卫里开白名单，见类注释即可核对）：
 * ① {@code AcPermitApplyPortalController} 类级不挂，按端点拆——{@code GET ""}／{@code /approve}／
 * {@code /reject} 方法级 AuthAdmin，{@code POST ""}（自助申请）与 {@code GET /my}（登录档查自己）
 * 保持登录态，故腿 1 对它只要求"至少一处"而非类级；② {@code GroupBindController}／
 * {@code GroupUserBindController} 类级 AuthAdmin 之下，本人端点以方法级 {@code @Auth(AuthLogin.class)}
 * 反向豁免，腿 2 对这两个文件额外断言该豁免针仍在；③（批乙修1 增补）
 * {@code AcTenantPortalController} 类级 AuthAdmin 之下 {@code /my/*} 四条端点方法级 AuthLogin
 * 豁免（判权收口在 AcTenantPortalService.guardTenantAdmin），同入 LOGIN_EXEMPT 清单，
 * 并由新增两针钉死豁免注解计数＝4 与 service guard 调用点＝4（防漂移，R-C M-3）。
 * </p>
 * <p>
 * 🔴 针自证纪律（判据必须证明自己活着）：腿 3 喂合成"无注解 controller"文本给腿 1 同一判定函数、
 * 断言判红；腿 4 用既有真闸 {@code AcSsoRedirectPortalController} 与合成合规样本判绿——空扫描
 * 与恒真判定都不算通过。文件数下限（admin 总数 ≥ 20、具体 ≥ 18，当前实况 21／19）防空扫描假绿。
 * </p>
 * <p>
 * 🔴 2026-10-20 状态同步（commit 056b471c「修正不必要的authAdmin」368 文件有意摘除后守卫随之改写）：
 * 原先"admin 全部具体 controller／auth 点名 17 条必须挂 AuthAdmin"的全称不变式已随摘除作废，
 * 改为<b>双清单钉状态</b>——仍挂闸清单（防误摘：挂闸的再摘＝平台闸丢失，宁红勿静默）＋
 * 摘除清单（防回填：回填 AuthAdmin 会把租户管理员锁在平台闸外，宁红勿静默）。两个清单都是
 * 磁盘现状的忠实快照，改姿势必须连清单一起改＝守卫逼着显式决策，而不是默默漂移。
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
     * 仍挂 AuthAdmin 的 auth 写面 controller（相对 authorization 模块根；防误摘清单）。
     * 2026-10-20 现状快照（056b471c 摘除后仍保留平台闸的 8 条）：菜单/部门/资源/合作伙伴/
     * 租户管理面/改密是平台级动作；{@code PasswordController} 是方法级（/reset 挂闸、/change
     * 走本人校验），判定只看字面量不区分层级。
     */
    private static final List<String> AUTH_GATED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/admin/AdminDeptMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminGroupMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminDeptController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcResourcePermController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminMenuController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminPartnerController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcTenantPortalController.java",
            "src/main/java/com/bidr/authorization/controller/login/PasswordController.java"));

    /**
     * 056b471c 有意摘除 AuthAdmin 的 controller（防回填清单）：用户/角色/分组/绑定面已开放给
     * 租户管理员使用（10-17 身份统一后 ac_user_tenant 成员即租户管理员），回填平台闸＝把
     * 租户管理员锁在外面——这两件事都该红着改清单，而不是默默漂移。
     */
    private static final List<String> AUTH_UNGATED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/admin/AdminUserRoleBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminRoleUserBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminUserMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminUserDeptBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminRoleController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminRoleMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupAdminController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupBindController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupUserBindController.java"));

    /** 反向豁免腿：这三个类必须在类级 AuthAdmin 之外保有 AuthLogin 方法级豁免针 */
    private static final List<String> LOGIN_EXEMPT_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/group/GroupBindController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupUserBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcTenantPortalController.java"));

    /** 仍挂 AuthAdmin 的 admin 具体 controller（相对 admin 模块 src/main/java；防误摘清单，正对照含在内） */
    private static final List<String> ADMIN_GATED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "com/bidr/admin/manage/account/controller/AdminAccountController.java",
            "com/bidr/admin/manage/partner/controller/AcPartnerHistoryPortalController.java",
            "com/bidr/admin/manage/permit/controller/AcPermitApplyPortalController.java",
            "com/bidr/admin/manage/sso/controller/AcSsoRedirectPortalController.java"));

    /**
     * 056b471c 有意摘除/从未挂闸的 admin 具体 controller（防回填清单）：门户（Portal）与
     * 统计/表格/部门 Excel/用户组/角色视图等读面与租户可用面，默认登录态（AuthLogin 兜底）。
     */
    private static final List<String> ADMIN_UNGATED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "com/bidr/admin/controller/AdminPortalController.java",
            "com/bidr/admin/controller/AdminPortalAssociateController.java",
            "com/bidr/admin/controller/statistic/AdminPortalDashboardController.java",
            "com/bidr/admin/controller/statistic/AdminPortalDashboardStatisticController.java",
            "com/bidr/admin/controller/statistic/AdminPortalIndicatorController.java",
            "com/bidr/admin/controller/statistic/AdminPortalIndicatorGroupController.java",
            "com/bidr/admin/controller/table/PortalPivotColumnPortalController.java",
            "com/bidr/admin/controller/table/PortalTableFilterPortalController.java",
            "com/bidr/admin/controller/table/PortalTablePortalController.java",
            "com/bidr/admin/manage/dept/controller/AdminDeptExcelController.java",
            "com/bidr/admin/manage/group/controller/AdminGroupController.java",
            "com/bidr/admin/manage/group/controller/AdminGroupExcelController.java",
            "com/bidr/admin/manage/role/controller/AcRolePortalController.java",
            "com/bidr/admin/manage/user/controller/AdminUserController.java",
            "com/bidr/admin/manage/user/controller/AdminUserGroupBindController.java"));

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

    private static List<Path> collectControllerFiles(Path adminSrc) throws IOException {
        try (Stream<Path> stream = Files.walk(adminSrc)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * 具体类判据：存在 class 声明行（注释里的 "abstract class" 字样不算声明）且该行不含 abstract
     * （abstract 基类不要求挂闸）。按声明行判断而非全文 contains，防注释命中造成假阴性。
     * JDK 8 无 String.lines()，用 split 换行。
     */
    private static boolean isConcreteController(String content) {
        for (String line : content.split("\n")) {
            String l = line.trim();
            if (l.matches("(public\\s+|final\\s+|abstract\\s+)*class\\s+\\w+.*")
                    && !l.contains("abstract")) {
                return true;
            }
        }
        return false;
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
     * 腿 1（admin 双清单钉状态，2026-10-20 随 056b471c 改写）：仍挂闸 4 条防误摘、
     * 摘除 15 条防回填；扫描文件数下限防空扫描假绿。
     */
    @Test
    public void adminGateInventoryMatchesIntentionalState() throws IOException {
        Path adminSrc = locateAdminModule().resolve("src").resolve("main").resolve("java");
        List<Path> scanned = collectControllerFiles(adminSrc);

        // 扫描文件数下限（当前实况 21）：路径解析失效/目录挪走时这里先红，不会空扫假绿
        Assert.assertTrue("admin controller 扫描数异常（路径解析疑似失效）: " + scanned.size(),
                scanned.size() >= 20);

        // 防误摘：仍挂闸清单逐条必须在场且含闸针
        List<String> stripped = new ArrayList<String>();
        for (String relative : ADMIN_GATED_CONTROLLERS) {
            Path file = adminSrc.resolve(relative);
            if (!Files.isRegularFile(file)) {
                stripped.add(relative + " 不存在（挂闸清单与磁盘漂移）");
            } else if (!missingAdminGate(relative, read(file)).isEmpty()) {
                stripped.add(relative + " 丢失闸针（防误摘）");
            }
        }
        Assert.assertTrue("admin 仍挂闸清单被误摘：\n" + String.join("\n", stripped), stripped.isEmpty());

        // 防回填：摘除清单逐条必须不含闸针
        List<String> regressed = new ArrayList<String>();
        for (String relative : ADMIN_UNGATED_CONTROLLERS) {
            Path file = adminSrc.resolve(relative);
            if (!Files.isRegularFile(file)) {
                regressed.add(relative + " 不存在（摘除清单与磁盘漂移）");
            } else if (missingAdminGate(relative, read(file)).isEmpty()) {
                regressed.add(relative + " 出现 " + ADMIN_GATE_NEEDLE + "（平台闸回填会把租户管理员锁在外面）");
            }
        }
        Assert.assertTrue("admin 摘除清单出现平台闸回填：\n" + String.join("\n", regressed), regressed.isEmpty());

        // 完整性（review M1，2026-10-21）：每个扫到的具体 controller 必须恰入两清单之一——
        // 双清单钉状态只对清单内条目有牙，新文件两清单都不进＝双向漏检（挂闸忘挂/该挂未挂都抓不住）
        List<String> orphans = new ArrayList<String>();
        for (Path file : scanned) {
            String relative = adminSrc.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
            if (!isConcreteController(read(file))) {
                continue;
            }
            boolean gated = ADMIN_GATED_CONTROLLERS.contains(relative);
            boolean ungated = ADMIN_UNGATED_CONTROLLERS.contains(relative);
            if (gated == ungated) {
                orphans.add(relative + (gated
                        ? " 同时出现在挂闸与摘除两清单（清单自相矛盾）"
                        : " 未入任一清单（新 controller 必须显式选边，防双向漏检）"));
            }
        }
        Assert.assertTrue("admin controller 清单完整性缺口：\n" + String.join("\n", orphans), orphans.isEmpty());
    }

    /**
     * 腿 2（auth 点名双清单，2026-10-20 随 056b471c 改写）：仍挂闸 8 条防误摘、摘除 9 条防回填；
     * GroupBind/GroupUserBind/AcTenantPortal 另断言 AuthLogin 反向豁免针仍在
     * （豁免被误删＝本人端点 403，同样是契约破坏）。
     */
    @Test
    public void authNamedControllersCarryAuthAdminGate() throws IOException {
        Path authRoot = locateAuthorizationModule();
        List<String> stripped = new ArrayList<String>();
        for (String relative : AUTH_GATED_CONTROLLERS) {
            Path file = authRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                stripped.add(relative + " 不存在（挂闸清单与磁盘漂移）");
                continue;
            }
            if (!read(file).contains(ADMIN_GATE_NEEDLE)) {
                stripped.add(relative + " 丢失 " + ADMIN_GATE_NEEDLE + "（防误摘）");
            }
        }
        Assert.assertTrue("auth 仍挂闸清单被误摘：\n" + String.join("\n", stripped), stripped.isEmpty());

        List<String> regressed = new ArrayList<String>();
        for (String relative : AUTH_UNGATED_CONTROLLERS) {
            Path file = authRoot.resolve(relative);
            if (!Files.isRegularFile(file)) {
                regressed.add(relative + " 不存在（摘除清单与磁盘漂移）");
                continue;
            }
            if (read(file).contains(ADMIN_GATE_NEEDLE)) {
                regressed.add(relative + " 出现 " + ADMIN_GATE_NEEDLE + "（平台闸回填会把租户管理员锁在外面）");
            }
        }
        Assert.assertTrue("auth 摘除清单出现平台闸回填：\n" + String.join("\n", regressed), regressed.isEmpty());

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
