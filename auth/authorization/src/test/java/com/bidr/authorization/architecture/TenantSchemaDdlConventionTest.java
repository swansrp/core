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
import java.util.List;

/**
 * Title: TenantSchemaDdlConventionTest
 * Description: 批乙棒1 租户两表 Schema DDL 磁盘断言（离线：只读源码文本，不起 Spring、不连库）。
 * <p>
 * 断言面：{@code ac_user_tenant} DDL 必含唯一键 {@code uk_user_tenant(user_id,tenant_id)} 与
 * {@code is_admin} 列；{@code ac_tenant} DDL 必含 {@code CREATE TABLE IF NOT EXISTS `ac_tenant`}
 * 与自增主键；两表 DDL 一律不得出现 CHARSET/COLLATE（项目纪律）。判据取
 * {@code setCreateDDL("...")} 字面量切片而非全文，防 javadoc 提及关键词造成假命中。
 * </p>
 * <p>
 * 🔴 守卫针自证纪律（项目硬纪律，判据必须证明自己活着）：负对照腿把①合成"缺针 DDL"
 * （无 uk_user_tenant 且带 CHARACTER SET）喂给同一判定函数、断言判红；②"无 setCreateDDL
 * 调用"的合成文件切片为空、切片函数必须报提取失败而非静默放行——空扫描与恒真判定都不算通过。
 * 正对照腿用合成合规样本判绿，防针字面量写坏。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantSchemaDdlConventionTest {

    /** DDL 提取锚点：Schema 类的建表语句唯一入口（BaseMybatisSchema 约定） */
    private static final String DDL_ANCHOR = "setCreateDDL(";

    /** ac_user_tenant 必含针（承诺面点名） */
    private static final String[] USER_TENANT_NEEDLES = {
            "CREATE TABLE IF NOT EXISTS `ac_user_tenant`",
            "UNIQUE KEY `uk_user_tenant` (`user_id`,`tenant_id`)",
            "PRIMARY KEY (`user_id`,`tenant_id`)",
            "`is_admin` tinyint"};

    /** ac_tenant 必含针（承诺面点名＋审计标准件） */
    private static final String[] TENANT_NEEDLES = {
            "CREATE TABLE IF NOT EXISTS `ac_tenant`",
            "`id` bigint NOT NULL AUTO_INCREMENT",
            "PRIMARY KEY (`id`)",
            "`valid` char(1) DEFAULT '1'",
            "`code` varchar(50)"};

    /** 两表共同禁区：表级字符集/排序规则子句（项目纪律，DDL 一律不写） */
    private static final String[] FORBIDDEN = {"CHARSET", "CHARACTER SET", "COLLATE"};

    /**
     * 从 basedir/user.dir 起向上定位 authorization 模块根（同 AuthAdminGateConventionTest
     * 思路：只认目录名＋pom 坐标＋src/main/java 存在），定位失败直接报错不假绿。
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

    private static String readSchemaSource(String simpleName) throws IOException {
        Path file = locateAuthorizationModule().resolve("src/main/java/com/bidr/authorization/dao/schema")
                .resolve(simpleName + ".java");
        Assert.assertTrue("Schema 源文件不存在（磁盘漂移）: " + file, Files.isRegularFile(file));
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).trim();
        // 文件体量下限防空文件/路径扫错造成的空扫描假绿
        Assert.assertTrue("Schema 源文件体量异常（疑似空文件）: " + file, content.length() > 200);
        return content;
    }

    /**
     * 提取 setCreateDDL(...) 字面量切片：从锚点到行尾分号闭包。找不到锚点返回空串——
     * 判定函数对空串必须判违规（负对照腿 4 自证这一点），禁止"提取失败＝放行"。
     */
    static String extractCreateDdl(String source) {
        int anchor = source.indexOf(DDL_ANCHOR);
        if (anchor < 0) {
            return "";
        }
        int end = source.indexOf(");", anchor);
        return end < 0 ? "" : source.substring(anchor, end);
    }

    /**
     * 共享判定函数（真文件与合成样本同走此口）：required 针逐一必须在 DDL 切片内出现，
     * forbidden 子串一个都不许出现；DDL 为空＝直接违规（防空切片假绿）。
     */
    static List<String> ddlViolations(String fileName, String ddl, String[] required, String[] forbidden) {
        List<String> violations = new ArrayList<>();
        if (ddl.trim().isEmpty()) {
            violations.add(fileName + " DDL 切片为空（提取失败或无 setCreateDDL）");
            return violations;
        }
        for (String needle : required) {
            if (!ddl.contains(needle)) {
                violations.add(fileName + " DDL 缺针: " + needle);
            }
        }
        for (String banned : forbidden) {
            if (ddl.contains(banned)) {
                violations.add(fileName + " DDL 出现禁串: " + banned);
            }
        }
        return violations;
    }

    /** 腿 1：ac_user_tenant DDL 含 uk_user_tenant 唯一键＋is_admin＋复合主键，且无 CHARSET/COLLATE */
    @Test
    public void acUserTenantDdlCarriesUkNeedles() throws IOException {
        String ddl = extractCreateDdl(readSchemaSource("AcUserTenantSchema"));
        List<String> violations = ddlViolations("AcUserTenantSchema", ddl, USER_TENANT_NEEDLES, FORBIDDEN);
        Assert.assertTrue("ac_user_tenant DDL 断言失败：\n" + String.join("\n", violations), violations.isEmpty());
    }

    /** 腿 2：ac_tenant DDL 含建表语句＋自增主键＋软删位＋code 列，且无 CHARSET/COLLATE */
    @Test
    public void acTenantDdlCarriesCreateTableAndAutoIncrement() throws IOException {
        String ddl = extractCreateDdl(readSchemaSource("AcTenantSchema"));
        List<String> violations = ddlViolations("AcTenantSchema", ddl, TENANT_NEEDLES, FORBIDDEN);
        Assert.assertTrue("ac_tenant DDL 断言失败：\n" + String.join("\n", violations), violations.isEmpty());
    }

    /**
     * 腿 3（负对照，针自证）：合成"缺针 DDL"（无 uk_user_tenant/is_admin，且带 CHARACTER SET）
     * 喂给腿 1 同一判定函数必须判红——证明针真抓得住，而非恒真空转。
     */
    @Test
    public void syntheticDdlMissingNeedlesIsJudgedViolation() {
        String bad = "setCreateDDL(\"CREATE TABLE IF NOT EXISTS `ac_user_tenant` (\n"
                + "  `user_id` bigint NOT NULL,\n"
                + "  `tenant_id` bigint NOT NULL\n"
                + ") CHARACTER SET utf8mb4\")";
        List<String> violations = ddlViolations("SyntheticSchema", bad, USER_TENANT_NEEDLES, FORBIDDEN);
        Assert.assertFalse("负对照失效：缺针且带 CHARACTER SET 的 DDL 被判合规，判定函数已恒真",
                violations.isEmpty());
    }

    /**
     * 腿 4（提取器自证）：不含 setCreateDDL 锚点的合成源码，extractCreateDdl 必须给空串
     * 且判定函数必须判违规——禁止"提取失败＝静默放行"。
     */
    @Test
    public void syntheticSourceWithoutAnchorIsJudgedViolation() {
        String noAnchor = "public class FakeSchema { /* 忘了调 setCreateDDL */ }";
        String ddl = extractCreateDdl(noAnchor);
        Assert.assertEquals("提取器在无锚点源码上未返回空串（假绿风险）", "", ddl);
        Assert.assertFalse("提取失败未被判违规（空切片放行＝假绿）",
                ddlViolations("FakeSchema", ddl, TENANT_NEEDLES, FORBIDDEN).isEmpty());
    }

    /** 腿 5（正对照）：合成合规样本必须判绿——防针字面量写坏成任何真文件都过不了的死针 */
    @Test
    public void syntheticCompliantDdlPassesTheSameCheck() {
        String good = "setCreateDDL(\"CREATE TABLE IF NOT EXISTS `ac_user_tenant` (\n"
                + "  `user_id` bigint NOT NULL,\n"
                + "  `tenant_id` bigint NOT NULL,\n"
                + "  `is_admin` tinyint(1) DEFAULT NULL,\n"
                + "  PRIMARY KEY (`user_id`,`tenant_id`) USING BTREE,\n"
                + "  UNIQUE KEY `uk_user_tenant` (`user_id`,`tenant_id`)\n"
                + ")\")";
        Assert.assertTrue("正对照失效：合成合规样本被判违规",
                ddlViolations("SyntheticSchema", good, USER_TENANT_NEEDLES, FORBIDDEN).isEmpty());
    }
}
