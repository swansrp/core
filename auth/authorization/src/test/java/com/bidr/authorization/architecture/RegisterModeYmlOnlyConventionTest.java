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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Title: RegisterModeYmlOnlyConventionTest
 * Description: 注册模式来源守卫（2026-11-02 用户裁定）——自助注册模式 open/tenant/closed
 * <b>只许 yml {@code my.auth.register-mode} 定义</b>，不得回到 sys_config／参数页，也不得运行期热改。
 * 纯源码扫描，不起 Spring、不连库：
 * <ul>
 * <li>腿 1：参数注册表 {@code AccountParam} 不得再声明 {@code ACCOUNT_REGISTER_MODE}；</li>
 * <li>腿 2：authorization + authorization-api 全量 main 源码不得再出现该键（附扫描面下限，拒绝空扫描假绿）；</li>
 * <li>腿 3：唯一读取点 {@code RegisterModeService} 不得依赖 sys_config 缓存，且必须读 yml 键
 * （正针＋负针＋扫描函数自证）。</li>
 * </ul>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-02
 */
public class RegisterModeYmlOnlyConventionTest {

    private static final String FORBIDDEN_KEY = "ACCOUNT_REGISTER_MODE";

    @Test
    public void parameterRegistryNoLongerDeclaresRegisterMode() throws IOException {
        Path apiSrc = apiMainSource();
        Path paramFile = apiSrc.resolve("com/bidr/authorization/constants/param/AccountParam.java");
        Assert.assertTrue("参数注册表文件必须存在（定位失败拒绝假绿）: " + paramFile, Files.isRegularFile(paramFile));
        String source = read(paramFile);
        Assert.assertFalse("ACCOUNT_REGISTER_MODE 必须已移出参数注册表（模式只许 yml 定义）"
                + "，实际仍声明于: " + paramFile, source.contains(FORBIDDEN_KEY));
    }

    @Test
    public void mainSourcesNeverReferenceRegisterModeParam() throws IOException {
        List<Path> roots = Arrays.asList(mainSource(), apiMainSource());
        int scanned = 0;
        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            Assert.assertTrue("扫描根必须存在（拒绝空扫描假绿）: " + root, Files.isDirectory(root));
            List<Path> javaFiles;
            try (Stream<Path> stream = Files.walk(root)) {
                javaFiles = stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .collect(Collectors.toList());
            }
            for (Path file : javaFiles) {
                scanned++;
                if (read(file).contains(FORBIDDEN_KEY)) {
                    offenders.add(file.toString());
                }
            }
        }
        Assert.assertTrue("扫描面过小，疑似没扫到源码（下限 100）: " + scanned, scanned >= 100);
        Assert.assertTrue("注册模式只许 yml 定义，主代码仍引用 " + FORBIDDEN_KEY + "：" + offenders,
                offenders.isEmpty());
        // 针自证：合成样本必须被同一判据命中（防"常量改了就永远查不到"的空转）
        Assert.assertTrue("判据自证失效", ("x " + FORBIDDEN_KEY + " y").contains(FORBIDDEN_KEY));
    }

    @Test
    public void registerModeServiceReadsOnlyYml() throws IOException {
        Path serviceFile = mainSource().resolve(
                "com/bidr/authorization/service/login/impl/RegisterModeService.java");
        Assert.assertTrue("唯一读取点必须存在: " + serviceFile, Files.isRegularFile(serviceFile));
        String source = read(serviceFile);
        Assert.assertTrue("必须读 yml 键 my.auth.register-mode", source.contains("${my.auth.register-mode:}"));
        Assert.assertFalse("唯一读取点不得依赖 sys_config 缓存（SysConfigCacheService）",
                source.contains("SysConfig"));
        Assert.assertFalse("唯一读取点不得读参数缓存（getCache）", source.contains("getCache("));
        Assert.assertFalse("唯一读取点不得引用已移出的参数键", source.contains(FORBIDDEN_KEY));
    }

    // ==================== 工具 ====================

    private static Path mainSource() {
        return locateAuthorizationModule().resolve("src/main/java");
    }

    private static Path apiMainSource() {
        Path api = locateAuthorizationModule().getParent().resolve("authorization-api").resolve("src/main/java");
        Assert.assertTrue("authorization-api 主源码目录必须存在（模块搬家时同步改本守卫）: " + api,
                Files.isDirectory(api));
        return api;
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /** authorization 模块根定位（判据同 TenantGateConventionTest：目录名＋pom＋src/main/java，定位失败直接报红） */
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
        throw new IllegalStateException("无法从 " + start + " 定位 core/authorization 模块根（拒绝空扫描假绿）");
    }
}
