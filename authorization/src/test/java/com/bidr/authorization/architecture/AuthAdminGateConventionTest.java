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
 * 反向豁免，腿 2 对这两个文件额外断言该豁免针仍在。
 * </p>
 * <p>
 * 🔴 针自证纪律（判据必须证明自己活着）：腿 3 喂合成"无注解 controller"文本给腿 1 同一判定函数、
 * 断言判红；腿 4 用既有真闸 {@code AcSsoRedirectPortalController} 与合成合规样本判绿——空扫描
 * 与恒真判定都不算通过。文件数下限（admin 总数 ≥ 20、具体 ≥ 18，当前实况 21／19）防空扫描假绿。
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
     * auth 模块写面 controller 点名清单（相对 authorization 模块根）。
     * 批甲棒 1 共改 18 个 auth 文件，其中 {@code PasswordServiceImpl}／{@code UserInfoController}
     * 非 controller 不入此单；此处 16 条即全部涉闸 controller。{@code PasswordController} 是
     * 方法级（/reset 挂闸、/change 走本人校验），判定同样只看字面量，无需区分层级。
     */
    private static final List<String> AUTH_NAMED_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/admin/AdminUserRoleBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminRoleUserBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminUserMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminDeptMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminGroupMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminUserDeptBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminDeptController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AcResourcePermController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminMenuController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminRoleController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminRoleMenuBindController.java",
            "src/main/java/com/bidr/authorization/controller/admin/AdminPartnerController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupAdminController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupBindController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupUserBindController.java",
            "src/main/java/com/bidr/authorization/controller/login/PasswordController.java"));

    /** 反向豁免腿：这两个类必须在类级 AuthAdmin 之外保有 AuthLogin 方法级豁免针 */
    private static final List<String> LOGIN_EXEMPT_CONTROLLERS = Collections.unmodifiableList(Arrays.asList(
            "src/main/java/com/bidr/authorization/controller/group/GroupBindController.java",
            "src/main/java/com/bidr/authorization/controller/group/GroupUserBindController.java"));

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
     * 腿 1（admin 全覆盖）：admin 模块全部具体 controller 逐个断言至少一处
     * {@code @Auth(AuthAdmin.class)}；文件数下限防空扫描假绿。
     */
    @Test
    public void adminConcreteControllersAllCarryAuthAdminGate() throws IOException {
        Path adminSrc = locateAdminModule().resolve("src").resolve("main").resolve("java");
        List<Path> scanned = collectControllerFiles(adminSrc);

        // 扫描文件数下限（当前实况 21）：路径解析失效/目录挪走时这里先红，不会空扫假绿
        Assert.assertTrue("admin controller 扫描数异常（路径解析疑似失效）: " + scanned.size(),
                scanned.size() >= 20);

        List<Path> concrete = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        for (Path file : scanned) {
            String content = read(file);
            if (!isConcreteController(content)) {
                continue;
            }
            concrete.add(file);
            violations.addAll(missingAdminGate(adminSrc.relativize(file).toString(), content));
        }
        // 具体 controller 下限（当前实况 19）：abstract 基类若吞掉全部具体类也照样红
        Assert.assertTrue("admin 具体 controller 数异常: " + concrete.size(), concrete.size() >= 18);
        Assert.assertTrue("admin 具体 controller 存在裸奔：\n" + String.join("\n", violations),
                violations.isEmpty());
    }

    /**
     * 腿 2（auth 点名）：16 条写面 controller 逐个断言存在且挂闸；GroupBind/GroupUserBind
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
}
