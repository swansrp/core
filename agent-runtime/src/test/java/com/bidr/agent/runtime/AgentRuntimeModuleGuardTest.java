package com.bidr.agent.runtime;

import com.bidr.agent.runtime.config.AgentRuntimeAutoConfiguration;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;
import com.bidr.agent.runtime.constant.param.AgentRuntimeParam;
import com.bidr.agent.runtime.dao.mapper.ChatSessionMapper;
import com.bidr.kernel.BaseApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: AgentRuntimeModuleGuardTest
 * Description: 「可直接下沉」的不变式守卫——本模块日后整体迁入 core（agent-runtime-client）时，
 * 以下四条必须成立，任何一条被破坏都会让搬迁变成改造：
 * <ol>
 * <li>模块源码零项目引用（不得出现 com.bidr.epc）；</li>
 * <li>装配以 AutoConfiguration 形式注册（imports 文件可加载到）；</li>
 * <li>配置键前缀恒为 my.agent.runtime（下沉后 yml 键零改）；</li>
 * <li>系统参数键名一律带 AGENT_RUNTIME_ 前缀（sys_config 的 key 即枚举名，跨模块需全局唯一）。</li>
 * </ol>
 *
 * @author sharp
 * @since 2026/9/22
 */
class AgentRuntimeModuleGuardTest {

    private static final String PROJECT_PACKAGE_MARK = "com.bidr.epc";

    private static final String AUTOCONFIG_IMPORTS = "META-INF/spring/"
            + "org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    @Test
    @DisplayName("模块源码零项目引用：不得出现 com.bidr.epc")
    void moduleSourcesMustNotReferenceProjectPackage() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        assertTrue(Files.isDirectory(sourceRoot),
                "测试须在模块根目录运行（surefire 默认工作目录即模块根），当前缺少 " + sourceRoot.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    if (content.contains(PROJECT_PACKAGE_MARK)) {
                        offenders.add(sourceRoot.relativize(path).toString());
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        assertEquals(new ArrayList<>(), offenders,
                "本模块必须零项目引用（业务耦合只允许出现在 AgentRequestContext 等 SPI 的实现侧）");
    }

    @Test
    @DisplayName("装配入口在 AutoConfiguration.imports 中注册")
    void autoConfigurationIsRegistered() throws IOException {
        Path importsFile = Paths.get("src", "main", "resources", AUTOCONFIG_IMPORTS);
        assertTrue(Files.isRegularFile(importsFile), "缺少 " + AUTOCONFIG_IMPORTS + "，自动装配不会生效");

        String content = new String(Files.readAllBytes(importsFile), StandardCharsets.UTF_8);
        assertTrue(content.contains(AgentRuntimeAutoConfiguration.class.getName()),
                "imports 文件中未注册 " + AgentRuntimeAutoConfiguration.class.getName());
    }

    @Test
    @DisplayName("配置键前缀恒为 my.agent.runtime（下沉后 yml 键零改）")
    void propertiesPrefixIsStable() {
        ConfigurationProperties annotation =
                AgentRuntimeProperties.class.getAnnotation(ConfigurationProperties.class);

        assertNotNull(annotation, "AgentRuntimeProperties 缺少 @ConfigurationProperties");
        assertEquals("my.agent.runtime", annotation.prefix());
    }

    @Test
    @DisplayName("系统参数键名一律带 AGENT_RUNTIME_ 前缀")
    void paramKeysArePrefixed() {
        for (AgentRuntimeParam param : AgentRuntimeParam.values()) {
            assertTrue(param.name().startsWith("AGENT_RUNTIME_"),
                    "系统参数键名必须带 AGENT_RUNTIME_ 前缀（sys_config 的 key 即枚举名）：" + param.name());
        }
    }

    @Test
    @DisplayName("模块自带的 Mapper 落在框架 MapperScan 默认模式的命中集内")
    void mapperIsCoveredByFrameworkScanPattern() throws IOException {
        String basePackage = frameworkMapperScanBasePackage();
        // 复刻 MyBatis-Spring 的解析口径：包名转资源路径 → classpath*:<path>/**/*.class
        String searchPath = "classpath*:" + basePackage.replace('.', '/') + "/**/*.class";

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        List<String> mapperClasses = new ArrayList<>();
        for (Resource resource : resolver.getResources(searchPath)) {
            String path = resource.getURL().getPath();
            int index = path.lastIndexOf("com/bidr/");
            if (index >= 0 && path.endsWith(".class")) {
                mapperClasses.add(path.substring(index, path.length() - ".class".length())
                        .replace('/', '.'));
            }
        }

        assertTrue(mapperClasses.contains(ChatSessionMapper.class.getName()),
                "默认模式 " + basePackage + " 扫不到 " + ChatSessionMapper.class.getName()
                        + "（实际命中 " + mapperClasses.size() + " 个类）；"
                        + "表代码换包后必须重新确认这个断言");
    }

    /** 读框架 @MapperScan 的默认值（`${kernel.scan.mapper-packages:<默认>}`） */
    private static String frameworkMapperScanBasePackage() {
        String raw = String.join(",", BaseApplication.class.getAnnotation(MapperScan.class).value());
        if (!raw.contains(":")) {
            return raw;
        }
        return raw.substring(raw.indexOf(':') + 1, raw.length() - 1);
    }
}