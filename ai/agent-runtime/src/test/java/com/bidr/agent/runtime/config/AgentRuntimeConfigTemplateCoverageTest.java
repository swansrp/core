package com.bidr.agent.runtime.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentRuntimeConfigTemplateCoverageTest
 * Description: 配置模板对账守卫（判据照 {@code tool/document} 与 {@code tool/mineru} 的同族守卫）：
 * <b>模板叶子键集合与 {@link AgentRuntimeProperties} 反射展开的键集合必须完全相等</b>——
 * 漏写一把＝现场照模板填完仍缺配置（本模块的坑最难查：{@code enabled} 缺席时整模块静默不装配，
 * {@code /web/agent/rt/**} 直接 404，而 yml 里看着"什么都没写而已"）；多写一把＝模板承诺了代码不读的键。
 * <p>
 * 模板此前不存在（2026-10-21 补）：README 只有四行片段，其余 11 把键（尤其 provider 需重启、
 * proxy 三把键刻意不进 sys_config、openhands 专用三把）只能读源码才知道。
 * 自证腿防死针：抹掉一键后差集必须<b>恰好</b>是那一键。
 *
 * @author Sharp
 * @since 2026-10-21
 */
class AgentRuntimeConfigTemplateCoverageTest {

    private static final String PREFIX = "my.agent.runtime";

    private static final Path TEMPLATE =
            Paths.get(System.getProperty("basedir", System.getProperty("user.dir")),
                    "src", "main", "resources", "agent-runtime-config.template.yml");

    private static final Pattern LINE = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");

    @Test
    void 模板叶子键与配置类字段完全相等() throws IOException {
        assertTrue(Files.isRegularFile(TEMPLATE), "模板文件失踪＝下一个项目只能靠读源码猜配置面");
        Set<String> templateLeaves = templateLeaves(templateText());
        Set<String> codeLeaves = propertyLeaves(AgentRuntimeProperties.class, PREFIX);

        Set<String> missing = diff(codeLeaves, templateLeaves);
        Set<String> stale = diff(templateLeaves, codeLeaves);
        assertTrue(missing.isEmpty(), "模板缺这些代码实际读取的键:\n" + String.join("\n", missing));
        assertTrue(stale.isEmpty(), "模板列了代码不再读取的键:\n" + String.join("\n", stale));
    }

    @Test
    void 判据本身可被证伪() throws IOException {
        String text = templateText();
        Set<String> templateLeaves = templateLeaves(text);
        assertEquals(15, templateLeaves.size(), "配置类展开 15 把键（含 openhands 专用三把与 proxy 子块三把）须全在模板");
        assertTrue(templateLeaves.contains(PREFIX + ".proxy.port"), "嵌套子块不许漏");
        assertTrue(templateLeaves.contains(PREFIX + ".enabled"), "装配闸那把不许漏");
        assertFalse(templateLeaves.contains(PREFIX), "容器节点不许当叶子");

        String truncated = text.replace("      enabled: true", "      # enabled: true");
        Set<String> missing = diff(propertyLeaves(AgentRuntimeProperties.class, PREFIX), templateLeaves(truncated));
        assertEquals(1, missing.size(), "抹掉一键后必须恰好缺这一键: " + missing);
        assertTrue(missing.contains(PREFIX + ".enabled"));
    }

    private static String templateText() throws IOException {
        return new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8);
    }

    /** 解析模板缩进树取叶子全路径（注释行不进扫描，容器节点不算叶子） */
    static Set<String> templateLeaves(String text) {
        List<String[]> lines = Stream.of(text.split("\r?\n", -1))
                .map(LINE::matcher)
                .filter(Matcher::matches)
                .map(m -> new String[]{m.group(1), m.group(2)})
                .filter(parts -> !parts[1].startsWith("#"))
                .collect(Collectors.toList());
        Set<String> leaves = new TreeSet<>();
        String[] stack = new String[32];
        for (int i = 0; i < lines.size(); i++) {
            String indent = lines.get(i)[0];
            String key = lines.get(i)[1];
            int depth = indent.length() / 2;
            stack[depth] = key;
            String nextIndent = i + 1 < lines.size() ? lines.get(i + 1)[0] : "";
            boolean hasChild = nextIndent.length() > indent.length();
            if (!hasChild) {
                StringBuilder path = new StringBuilder();
                for (int d = 0; d <= depth; d++) {
                    path.append(stack[d]).append('.');
                }
                leaves.add(path.deleteCharAt(path.length() - 1).toString());
            }
        }
        return leaves;
    }

    static Set<String> propertyLeaves(Class<?> type, String prefix) {
        Set<String> leaves = new TreeSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String key = prefix + "." + kebab(field.getName());
            if (field.getType().isPrimitive() || field.getType().getName().startsWith("java.")) {
                leaves.add(key);
            } else {
                leaves.addAll(propertyLeaves(field.getType(), key));
            }
        }
        return leaves;
    }

    private static String kebab(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Set<String> diff(Set<String> from, Set<String> against) {
        Set<String> copy = new TreeSet<>(from);
        copy.removeAll(against);
        return copy;
    }
}
