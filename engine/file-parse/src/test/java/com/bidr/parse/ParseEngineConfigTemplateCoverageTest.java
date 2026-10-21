package com.bidr.parse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板对账守卫（第六家，仿 tool/document 的 DocumentConfigTemplateCoverageTest）：
 * <b>模板叶子键集合与 {@link ParseEngineProperties} 展开键集合必须完全相等</b>——
 * 漏写一把＝现场照模板填完仍缺配置；多写一把＝模板承诺了代码不读的键。
 *
 * @author Sharp
 * @since 2026-10-21
 */
class ParseEngineConfigTemplateCoverageTest {

    private static final Path TEMPLATE =
            Paths.get(System.getProperty("basedir", System.getProperty("user.dir")),
                    "src", "main", "resources", "engine-parse-config.template.yml");

    @Test
    void 模板叶子键与配置类字段完全相等() throws IOException {
        assertTrue(Files.isRegularFile(TEMPLATE), "模板文件失踪＝下一个项目只能靠读源码猜配置面");
        Set<String> templateLeaves = templateLeaves(
                new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8));
        Set<String> codeLeaves = propertyLeaves(
                ParseEngineProperties.class, "engine.parse");

        Set<String> missing = diff(codeLeaves, templateLeaves);
        Set<String> stale = diff(templateLeaves, codeLeaves);
        assertTrue(missing.isEmpty(), "模板缺这些代码实际读取的键:\n" + String.join("\n", missing));
        assertTrue(stale.isEmpty(), "模板列了代码不再读取的键:\n" + String.join("\n", stale));
    }

    @Test
    void 判据本身可被证伪() throws IOException {
        String text = new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8);
        Set<String> templateLeaves = templateLeaves(text);
        assertEquals(3, templateLeaves.size(), "三把键全在模板");
        assertTrue(templateLeaves.contains("engine.parse.transcriber"));
        assertFalse(templateLeaves.contains("engine.parse"), "容器节点不许当叶子");

        String truncated = text.replace("    transcriber:", "    # transcriber:");
        Set<String> missing = diff(
                propertyLeaves(ParseEngineProperties.class, "engine.parse"),
                templateLeaves(truncated));
        assertEquals(1, missing.size(), "抹掉一键后必须恰好缺这一键: " + missing);
        assertTrue(missing.contains("engine.parse.transcriber"));
    }

    private static Set<String> diff(Set<String> from, Set<String> against) {
        Set<String> copy = new TreeSet<>(from);
        copy.removeAll(against);
        return copy;
    }

    /** 模板叶子键提取（与 tool/document 同款算法：两空格一层、叶子=无子行的键） */
    static Set<String> templateLeaves(String text) {
        java.util.regex.Pattern line = java.util.regex.Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");
        List<String[]> lines = java.util.stream.Stream.of(text.split("\r?\n", -1))
                .map(line::matcher)
                .filter(java.util.regex.Matcher::matches)
                .map(m -> new String[]{m.group(1), m.group(2)})
                .filter(parts -> !parts[1].startsWith("#"))
                .collect(java.util.stream.Collectors.toList());
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

    /** 配置类展开键（primitive/java 类型＝叶子，其余递归下钻） */
    static Set<String> propertyLeaves(Class<?> type, String prefix) {
        Set<String> leaves = new TreeSet<>();
        for (java.lang.reflect.Field field : type.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
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
}
