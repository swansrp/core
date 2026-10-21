package com.bidr.oss.service;

import com.bidr.oss.constant.param.OssParam;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 选型单源守卫（离线）：{@code OSS_SERVER_TYPE} 的读取只允许出现在 {@link OssServiceSelector} 一处。
 *
 * <p>这条链改造前有三份各写各的（选型器、{@code /web/oss} 上传、系统技能包字节通道），
 * 而"库值优先、配置文件兜底"的取值链一旦只补在其中一处，就会出现同一个进程里
 * 一条腿按库、一条腿按 yml 的分叉——底座选型分叉的后果是文件写进一个桶、从另一个桶读，
 * 比配错更难查。故把"只有一个读取点"钉成约定守卫，而不是靠 review 记着。</p>
 *
 * <p>扫描跑在 core 源码树上（surefire 工作目录＝模块 basedir，上两级即 core 根），与 profile 无关。
 * 空扫描不算通过：正向针钉住两个已知消费方的接线特征，工作目录假设被破坏时先报红而不是假绿。</p>
 *
 * @author Sharp
 * @since 2026-10-19
 */
class OssSelectionSingleSourceTest {

    private static final Path CORE_ROOT = Paths.get("..", "..").toAbsolutePath().normalize();

    /** 代码级读取选型值的三种写法（getSysConfigValue/getParamValueAvail/getCache），注释与本守卫不算 */
    private static final Pattern SELECTION_READ = Pattern.compile(
            "(getSysConfigValue|getParamValueAvail|getCache)\\s*\\(\\s*OssParam\\.OSS_SERVER_TYPE");

    /** 唯一允许的读取点 */
    private static final String ALLOWED_READER = "service/OssServiceSelector.java";

    /** 正向针：已知消费方必须经选型器取实现（证明接线还在、扫描器读对了树） */
    private static final String[][] POSITIVE_NEEDLES = {
            {"store/oss/src/main/java/com/bidr/oss/controller/OssController.java", "ossServiceSelector.select()"},
            {"ai/agent-console/src/main/java/com/bidr/agent/console/skill/SystemSkillStore.java", "ossServiceSelector.select()"},
    };

    @Test
    @DisplayName("core 全树只有 OssServiceSelector 读 OSS_SERVER_TYPE——取值链不许有第二份")
    void selectionValueReadOnlyInSelector() throws IOException {
        List<Path> javaFiles = coreSources();
        assertTrue(javaFiles.size() > 500,
                "扫描必须覆盖 core 全部 main 源码，实得 " + javaFiles.size() + " 个文件——工作目录假设破坏时先报红");

        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles) {
            if (file.toString().replace('\\', '/').endsWith(ALLOWED_READER)) {
                continue;
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (SELECTION_READ.matcher(lines.get(i)).find()) {
                    offenders.add(CORE_ROOT.relativize(file) + ":" + (i + 1));
                }
            }
        }
        assertEquals(Collections.emptyList(), offenders,
                "选型值出现第二个读取点：它读不到配置文件那条兜底腿，同一进程会按两种选型走。改为注入 OssServiceSelector");
    }

    @Test
    @DisplayName("正向针：两处已知消费方确实经选型器——它们掉了说明守卫的前提已失效")
    void positiveNeedlesKeepTheScannerHonest() throws IOException {
        for (String[] needle : POSITIVE_NEEDLES) {
            Path file = CORE_ROOT.resolve(needle[0]);
            assertTrue(Files.exists(file), "正向针文件必须存在: " + needle[0]);
            String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertTrue(content.contains(needle[1]),
                    needle[0] + " 必须含特征 " + needle[1] + "——它丢了说明选型接线被拆，本守卫的前提已失效");
        }
    }

    @Test
    @DisplayName("种子默认值必须是空白＝未表态：填成任何一家都会让配置文件那条腿永远读不到")
    void seededDefaultKeepsTheYmlLegAlive() {
        assertTrue(StringUtils.isBlank(OssParam.OSS_SERVER_TYPE.getDefaultValue()),
                "@MetaParam 只插行不改值：种子默认只要带着某家编号，新装现场的库行就永久压住 oss.server-type（yml 腿成死键）");
    }

    private static List<Path> coreSources() throws IOException {
        try (Stream<Path> walk = Files.walk(CORE_ROOT)) {
            return walk.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .collect(Collectors.toList());
        }
    }
}
