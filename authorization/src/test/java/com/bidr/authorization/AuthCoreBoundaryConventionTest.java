package com.bidr.authorization;

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
 * Title: AuthCoreBoundaryConventionTest
 * Description: core/authorization 的底座边界守卫（离线，跑磁盘源码文本而非 classpath）。
 * <p>
 * M15 把 SSO 签发口下沉进 core 的前提是：core 只做编排、不直连任何具体 IdP、也不反向依赖
 * framework 侧。所以钉两条负向断言：{@code core/authorization/src}（main+test 全部 .java）与
 * 本模块 {@code pom.xml} 的文本里，
 * <ol>
 *   <li>禁止出现 idm 包前缀（core 不感知 idm，身份来源一律走 SsoUserHandler SPI）；</li>
 *   <li>禁止出现 framework 包前缀（依赖方向 framework→core，不许倒挂）。</li>
 * </ol>
 * 同时钉一条<b>正向对照</b>：同仓 bidr-framework 的源码必须命中 idm 线程态持有类
 * （IdmHolder）——防止路径解析失效后空扫描假绿（判据思路取自本仓 common 侧
 * FrameworkBoundaryConventionTest，只读其思路、不跨仓引用）。
 * </p>
 * <p>
 * 依赖面违禁坐标只列 {@code bidr-framework} 与 {@code idm-spring-boot-starter}，<b>不含</b>
 * {@code bidr-mdm}／{@code bidr-notice}——这不是漏项而是归属划分（M15 棒0 review#3 S-2）：
 * 那两件与本模块无直接关系，越界引用它们坏的是"plsintec 那套编不过"，判据与正/负向腿都落在
 * 本仓 common 侧 {@code inkhub-common/.../FrameworkBoundaryConventionTest}（其 FRAMEWORK_ARTIFACTS
 * 三件齐全）。本守卫只管 core/authorization 自身这一格，两侧合起来才覆盖全套 framework 侧构件。
 * </p>
 * <p>
 * 🔴 违禁字面量在源码里用字符串拼接构造（"com.bidr." + "idm"），否则本守卫自身会命中自己扫的
 * forbidden 前缀——负向腿变成自证假红。
 * </p>
 * <p>
 * 🔴 依赖面（pom）判据必须是 {@code <artifactId>} 整串而非包名前缀（M15 棒0 review#3 F-1）：
 * 本仓声明 framework/idm 构件的真实形态是 {@code <groupId>com.bidr</groupId>} 与下一行的
 * {@code <artifactId>bidr-framework</artifactId>}，前缀针在全部 pom 里只命中一条注释行＝<b>死针</b>，
 * 于是"pom 面零越界"跑绿也证不了任何事。每个面的判据另配一条自证腿（合成串必须命中、
 * 真实声明文件必须命中），死针从此报红而非假绿。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
public class AuthCoreBoundaryConventionTest {

    /** 违禁前缀一：idm 包（拼接构造，见类注释） */
    private static final String FORBIDDEN_IDM = "com.bidr." + "idm";
    /** 违禁前缀二：framework 包（拼接构造） */
    private static final String FORBIDDEN_FRAMEWORK = "com.bidr." + "framework";

    /**
     * 依赖面违禁坐标：framework 侧母件与 idm starter。判法＝{@code <artifactId>名</artifactId>} 整串，
     * 与本仓 common 侧同族守卫的 {@code FRAMEWORK_ARTIFACTS} 一致（那里已是现成正例）。
     */
    private static final List<String> FORBIDDEN_POM_ARTIFACTS =
            Arrays.asList("bidr-framework", "idm-spring-boot-starter");

    /**
     * 从 basedir/user.dir 起向上找 authorization 模块根：不假设 cwd 层级，只认模块自身的
     * 特征（目录名 + pom 坐标 + src/main/java 存在），找不到直接 fail（宁可报红不假绿）。
     */
    private static Path locateAuthModule() {
        Path start = Paths.get(System.getProperty("basedir",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if ("authorization".equals(dir.getFileName().toString())
                    && Files.isRegularFile(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
        }
        // 兜底：从祖先目录里再认一次相对坐标 core/authorization（cwd 落在上层聚合时）
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("framework").resolve("core").resolve("authorization");
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("src").resolve("main").resolve("java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法从 " + start + " 定位 core/authorization 模块根（路径假设已坏，拒绝空扫描）");
    }

    /** authorization 之上的 framework 仓根（core 与 bidr-framework 互为该仓的兄弟目录） */
    private static Path locateFrameworkRepoRoot() {
        return locateAuthModule().getParent().getParent();
    }

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** 源码面判据：整串文本出现 idm／framework 包前缀即违规 */
    private static List<String> prefixViolations(String text) {
        List<String> hits = new ArrayList<>();
        if (text.contains(FORBIDDEN_IDM)) {
            hits.add(FORBIDDEN_IDM);
        }
        if (text.contains(FORBIDDEN_FRAMEWORK)) {
            hits.add(FORBIDDEN_FRAMEWORK);
        }
        return hits;
    }

    /** 依赖面判据：pom 文本以 {@code <artifactId>坐标</artifactId>} 整串声明 framework/idm 构件即违规 */
    private static List<String> coordinateViolations(String pomText) {
        List<String> hits = new ArrayList<>();
        for (String artifact : FORBIDDEN_POM_ARTIFACTS) {
            if (pomText.contains("<artifactId>" + artifact + "</artifactId>")) {
                hits.add(artifact);
            }
        }
        return hits;
    }

    @Test
    public void coreAuthorizationHasZeroIdmOrFrameworkReference() throws IOException {
        Path authModule = locateAuthModule();
        Assert.assertTrue("定位到的模块 pom 应声明 authorization 坐标",
                read(authModule.resolve("pom.xml")).contains("<artifactId>authorization</artifactId>"));

        Path srcDir = authModule.resolve("src");
        List<Path> scanned = collectJavaFiles(srcDir);

        List<String> violations = new ArrayList<>();
        for (Path file : scanned) {
            List<String> hits = prefixViolations(read(file));
            for (String hit : hits) {
                violations.add(srcDir.relativize(file).toString() + " 命中 " + hit);
            }
        }
        // 空扫描不算通过：authorization 模块磁盘上至少上百个 java 文件，下限给得保守
        Assert.assertTrue("扫描文件数异常（路径解析疑似失效）: " + scanned.size(), scanned.size() >= 80);
        Assert.assertTrue("core/authorization 出现越界引用：\n" + String.join("\n", violations),
                violations.isEmpty());
    }

    /**
     * 源码面判据自证（防死针）：合成的越界 import 必须命中，本守卫自己的拼接写法不许命中。
     */
    @Test
    public void prefixNeedleFiresOnSyntheticImportButNotOnItsOwnLiterals() {
        Assert.assertEquals(Arrays.asList(FORBIDDEN_FRAMEWORK),
                prefixViolations("import com.bidr." + "framework.mdm.service.MdmDeptService;"));
        Assert.assertEquals(Arrays.asList(FORBIDDEN_IDM),
                prefixViolations("import com.bidr." + "idm.config.IdmHolder;"));
        // 本类里那两处拼接（"com.bidr." + "idm"）在源码文本中不成串，否则负向腿自证假红
        Assert.assertTrue("拼接字面量失效：守卫会命中自己扫的前缀",
                prefixViolations(readOwnSource()).isEmpty());
    }

    @Test
    public void coreAuthorizationPomDeclaresNoFrameworkOrIdmArtifact() throws IOException {
        String pom = read(locateAuthModule().resolve("pom.xml"));
        Assert.assertTrue("core/authorization 的 pom 声明了 framework/idm 构件（plsintec 那套不含 framework 侧，"
                        + "且 M15「core 只基 core」前提已破）: " + coordinateViolations(pom),
                coordinateViolations(pom).isEmpty());
    }

    /**
     * 依赖面判据自证（review#3 F-1 的正面回炉）：本仓 pom 的真实声明形态是 groupId 与 artifactId
     * 分行书写，前缀针在这种形态下永不命中——所以坐标针必须能命中①合成声明②磁盘上确实引了
     * idm starter 的 bidr-framework/pom.xml。两腿任一不命中即判据失效，守卫报红而非空转。
     */
    @Test
    public void coordinateNeedleFiresOnRealAndSyntheticDeclaration() throws IOException {
        Assert.assertEquals(Arrays.asList("bidr-framework"),
                coordinateViolations("        <groupId>com.bidr</groupId>\n"
                        + "        <artifactId>bidr-framework</artifactId>\n"));
        String bridgePom = read(locateFrameworkRepoRoot().resolve("bidr-framework").resolve("pom.xml"));
        Assert.assertTrue("依赖面正对照失效：bidr-framework/pom.xml 未以 <artifactId> 整串声明 idm starter",
                coordinateViolations(bridgePom).contains("idm-spring-boot-starter"));
    }

    @Test
    public void bidrFrameworkStillReferencesIdmHolderAsPositiveControl() throws IOException {
        Path frameworkDir = locateFrameworkRepoRoot();
        Path bridgeSrc = frameworkDir.resolve("bidr-framework").resolve("src").resolve("main").resolve("java");
        Assert.assertTrue("bidr-framework 源码目录不存在（路径解析疑似失效）: " + bridgeSrc,
                Files.isDirectory(bridgeSrc));

        List<Path> scanned = collectJavaFiles(bridgeSrc);
        Assert.assertTrue("bidr-framework 扫描为空（假绿防线）", !scanned.isEmpty());
        boolean hit = false;
        for (Path file : scanned) {
            if (read(file).contains("Idm" + "Holder")) {
                hit = true;
                break;
            }
        }
        Assert.assertTrue("正向对照失败：bidr-framework 应命中 idm 线程态持有类（IdmHolder）", hit);
    }

    /** 读本守卫自身源码，用于钉"拼接写法不命中自己的前缀" */
    private static String readOwnSource() {
        Path candidate = Paths.get(locateAuthModule().toString(), "src", "test", "java", "com", "bidr",
                "authorization", "AuthCoreBoundaryConventionTest.java");
        try {
            return read(candidate);
        } catch (IOException e) {
            throw new AssertionError("读不到守卫自身源码，拼接自证腿无法执行: " + candidate, e);
        }
    }
}
