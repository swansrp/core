package com.bidr.identity.mirror;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.bidr.identity.mirror.dao.entity.IdmMirrorState;
import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Title: IdentityMirrorConventionTest
 * Description: identity-mirror 模块的 G9 边界守卫（T2.3d，离线——跑磁盘源码文本而非容器/库）。
 * <p>镜像模块的存在理由＝子系统拿到「这是谁」而<b>拿不到「他能干什么」</b>：授权判定一律回
 * provider（/authz 端点），本地永不落授权语义、也永不进程内引用 provider 的 dao/service。
 * 钉三条负向断言＋各自的判据自证腿（防死针，形态照 authorization 侧
 * {@code AuthCoreBoundaryConventionTest}）：</p>
 * <ol>
 *   <li><b>零授权字段</b>：四个镜像实体的 Java 字段名与映射列名（@TableId/@TableField）
 *       不得含 role/menu/perm/function——身份面（id/编码/名称/归属）之外的字段进实体即红；</li>
 *   <li><b>零上游物理表引用</b>：src/main 全部源码不得出现上游表前缀字面量（"a"+"c_" 拼接构造，
 *       防守卫自命中）——镜像只认快照 DTO 形状，不认 provider 库表名；</li>
 *   <li><b>零 provider 进程内引用</b>：src/main 的 import 面白名单＝
 *       {@code com.bidr.authorization.bind.}（UserRef 占位）与
 *       {@code com.bidr.authorization.constants.}（RequestConst.X_API_KEY 头名）——
 *       其余 com.bidr.authorization.* 一律禁（dao/service/controller/config 等 server 独占包）。
 *       api 模块新增消费面时扩白名单（pom 面由 tools/dep-direction-check.mjs rule3 对偶钉）。</li>
 * </ol>
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class IdentityMirrorConventionTest {

    /** 违禁字段名片段（授权语义四族：角色/菜单/权限/功能码） */
    private static final List<String> FORBIDDEN_FIELD_TOKENS =
            Arrays.asList("role", "menu", "perm", "function");

    /** 违禁上游表前缀（拼接构造，见类注释②；本守卫自身源码不成串） */
    private static final String FORBIDDEN_UPSTREAM_TABLE_PREFIX = "a" + "c_";

    /**
     * import 面白名单前缀（api 模块内本模块实际消费的包）。
     *
     * <p>判据区分「provider 实现面」与「api 契约面」：{@code service}/{@code dao}/{@code controller}
     * 等实现包禁止（镜像与 provider 只许 HTTP 快照面交互）；api 里的纯契约类型允许——{@code envelope}
     * 于 2026-11-04（对账单 #27）收编了原先 mirror 内联的信封 DTO，属同一档契约件。</p>
     */
    private static final List<String> ALLOWED_AUTHORIZATION_IMPORT_PREFIXES =
            Arrays.asList("com.bidr.authorization.bind.", "com.bidr.authorization.constants.",
                    "com.bidr.authorization.envelope.");

    /** 四个镜像实体（守卫对象清单——加第五张表要同步这里） */
    private static final List<Class<?>> MIRROR_ENTITIES = Arrays.asList(
            IdmUserMirror.class, IdmTenantMirror.class, IdmUserTenantMirror.class, IdmMirrorState.class);

    // ======================== 模块定位 ========================

    /**
     * 从 basedir/user.dir 起向上找 identity-mirror 模块根（特征＝目录名＋pom＋src/main/java），
     * 找不到直接 fail（宁可报红不空扫描假绿）。
     */
    private static Path locateMirrorModule() {
        Path start = Paths.get(System.getProperty("basedir",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if ("identity-mirror".equals(dir.getFileName().toString())
                    && Files.isRegularFile(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
        }
        throw new IllegalStateException("无法从 " + start + " 定位 identity-mirror 模块根（路径假设已坏，拒绝空扫描）");
    }

    private static List<Path> collectMainJavaFiles() throws IOException {
        Path mainDir = locateMirrorModule().resolve("src").resolve("main").resolve("java");
        try (Stream<Path> stream = Files.walk(mainDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    // ======================== 判据（供自证腿复用） ========================

    /** 实体字段名/列名命中授权语义片段 → 违规描述；干净 → 空列表 */
    private static List<String> authorizationFieldViolations(Class<?> entityClass) {
        List<String> hits = new ArrayList<>();
        for (java.lang.reflect.Field field : entityClass.getDeclaredFields()) {
            String javaName = field.getName().toLowerCase();
            for (String token : FORBIDDEN_FIELD_TOKENS) {
                if (javaName.contains(token)) {
                    hits.add(entityClass.getSimpleName() + "." + field.getName() + " 字段名命中 " + token);
                }
            }
            String column = columnOf(field);
            if (column != null) {
                String columnName = column.toLowerCase();
                for (String token : FORBIDDEN_FIELD_TOKENS) {
                    if (columnName.contains(token)) {
                        hits.add(entityClass.getSimpleName() + "." + field.getName() + " 列名 " + column + " 命中 " + token);
                    }
                }
            }
        }
        return hits;
    }

    /** 实体字段映射列（剥反引号；无 @TableId/@TableField 返回 null＝非库列） */
    private static String columnOf(java.lang.reflect.Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        String raw = tableId != null ? tableId.value() : null;
        if (raw == null || raw.isEmpty()) {
            TableField tableField = field.getAnnotation(TableField.class);
            raw = tableField == null ? null : tableField.value();
        }
        return raw == null ? null : raw.replace("`", "");
    }

    /** 单条 import 是否违规（非白名单的 com.bidr.authorization.* 即违规） */
    private static boolean isForbiddenAuthorizationImport(String importLine) {
        String target = importLine.replaceFirst("^import\\s+(?:static\\s+)?", "").replaceAll("\\s*;$", "").trim();
        if (!target.startsWith("com.bidr.authorization.")) {
            return false;
        }
        for (String allowed : ALLOWED_AUTHORIZATION_IMPORT_PREFIXES) {
            if (target.startsWith(allowed)) {
                return false;
            }
        }
        return true;
    }

    // ======================== 腿 ①：四实体零授权字段 ========================

    @Test
    public void mirrorEntitiesCarryNoAuthorizationFields() {
        List<String> violations = new ArrayList<>();
        for (Class<?> entityClass : MIRROR_ENTITIES) {
            violations.addAll(authorizationFieldViolations(entityClass));
        }
        Assert.assertTrue("镜像实体出现授权语义字段（G9 边界——授权判定回 provider，本地不落）：\n"
                + String.join("\n", violations), violations.isEmpty());
    }

    /** 腿①判据自证：合成的 user_role 字段（Java 名与列名两形态）必须命中 */
    @Test
    public void fieldNeedleFiresOnSyntheticRoleColumn() {
        class Synthetic {
            @TableField(value = "user_role")
            @SuppressWarnings("unused")
            String userRole;
            @TableField(value = "menu_ids")
            @SuppressWarnings("unused")
            String menuIds;
        }
        List<String> hits = new ArrayList<>();
        for (java.lang.reflect.Field field : Synthetic.class.getDeclaredFields()) {
            String javaName = field.getName().toLowerCase();
            for (String token : FORBIDDEN_FIELD_TOKENS) {
                if (javaName.contains(token)) {
                    hits.add(field.getName() + ":" + token);
                }
            }
            String column = columnOf(field);
            if (column != null) {
                for (String token : FORBIDDEN_FIELD_TOKENS) {
                    if (column.toLowerCase().contains(token)) {
                        hits.add(column + ":" + token);
                    }
                }
            }
        }
        Assert.assertTrue("合成 role/menu 针未命中＝判据失效（假绿防线）", hits.contains("userRole:role"));
        Assert.assertTrue(hits.contains("user_role:role"));
        Assert.assertTrue(hits.contains("menuIds:menu"));
        Assert.assertTrue(hits.contains("menu_ids:menu"));
    }

    // ======================== 腿 ②：main 源码零上游表前缀 ========================

    @Test
    public void mainSourcesHaveZeroUpstreamTableReference() throws IOException {
        List<Path> scanned = collectMainJavaFiles();
        Assert.assertTrue("main 扫描文件数异常（路径解析疑似失效）: " + scanned.size(), scanned.size() >= 10);
        List<String> violations = new ArrayList<>();
        for (Path file : scanned) {
            if (read(file).contains(FORBIDDEN_UPSTREAM_TABLE_PREFIX)) {
                violations.add(file.toString());
            }
        }
        Assert.assertTrue("main 源码出现上游物理表前缀（镜像只认快照 DTO 形状，不认 provider 库表名）：\n"
                + String.join("\n", violations), violations.isEmpty());
    }

    /** 腿②判据自证：合成 DDL 文本必须命中拼接针 */
    @Test
    public void upstreamPrefixNeedleFiresOnSyntheticText() {
        Assert.assertTrue("拼接针失效（假绿防线）",
                ("CREATE TABLE IF NOT EXISTS `a" + "c_user` (...)").contains(FORBIDDEN_UPSTREAM_TABLE_PREFIX));
        Assert.assertEquals("a" + "c_", FORBIDDEN_UPSTREAM_TABLE_PREFIX);
    }

    // ======================== 腿 ③：import 面白名单（零 provider 进程内引用） ========================

    @Test
    public void mainImportsCarryNoProviderInternalTypes() throws IOException {
        List<Path> scanned = collectMainJavaFiles();
        Assert.assertTrue("main 扫描文件数异常: " + scanned.size(), scanned.size() >= 10);
        List<String> violations = new ArrayList<>();
        for (Path file : scanned) {
            for (String line : read(file).split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import ") && isForbiddenAuthorizationImport(trimmed)) {
                    violations.add(file.getFileName() + " → " + trimmed);
                }
            }
        }
        Assert.assertTrue("main 源码 import 了 provider 独占类型（镜像与 provider 只许 HTTP 快照面交互）：\n"
                + String.join("\n", violations), violations.isEmpty());
    }

    /** 腿③判据自证：server 独占类针必须红、白名单两针必须绿 */
    @Test
    public void importNeedleFiresOnServerOnlyClassAndPassesWhitelist() {
        Assert.assertTrue("server service 针未命中＝判据失效",
                isForbiddenAuthorizationImport("import com.bidr.authorization.service.permit.PermitService;"));
        Assert.assertTrue("server dao 针未命中＝判据失效",
                isForbiddenAuthorizationImport("import com.bidr.authorization.dao.repository.AcUserService;"));
        Assert.assertTrue("server entity 针未命中＝判据失效",
                isForbiddenAuthorizationImport("import com.bidr.authorization.dao.entity.AcUser;"));
        Assert.assertFalse("api bind 白名单针误报",
                isForbiddenAuthorizationImport("import com.bidr.authorization.bind.annotation.UserRef;"));
        Assert.assertFalse("api constants 白名单针误报",
                isForbiddenAuthorizationImport("import com.bidr.authorization.constants.common.RequestConst;"));
        // #27 新增白名单项必须同轮配正向针：只改 ALLOWED_* 不改本组，白名单扩大将无人看守
        Assert.assertFalse("api envelope 白名单针误报",
                isForbiddenAuthorizationImport("import com.bidr.authorization.envelope.Envelope;"));
        Assert.assertFalse("非 authorization 包误报",
                isForbiddenAuthorizationImport("import com.bidr.kernel.mybatis.repository.BaseSqlRepo;"));
    }
}
