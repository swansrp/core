package com.bidr.document;

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
 * 模板对账守卫（判据仿 tool/mineru 的 MineruConfigTemplateCoverageTest）：
 * <b>模板叶子键集合与 {@link DocumentProperties} 展开键集合必须完全相等</b>——
 * 漏写一把＝现场照模板填完仍缺配置；多写一把＝模板承诺了代码不读的键。
 * 自证腿防死针（抹一键差集恰好是它）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
class DocumentConfigTemplateCoverageTest {

    private static final Path TEMPLATE =
            Paths.get(System.getProperty("basedir", System.getProperty("user.dir")),
                    "src", "main", "resources", "tool-document-config.template.yml");

    private static final Pattern LINE = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");

    @Test
    void 模板叶子键与配置类字段完全相等() throws IOException {
        assertTrue(Files.isRegularFile(TEMPLATE), "模板文件失踪＝下一个项目只能靠读源码猜配置面");
        Set<String> templateLeaves =
                templateLeaves(new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8));
        Set<String> codeLeaves = propertyLeaves(DocumentProperties.class, "tool.document");

        Set<String> missing = diff(codeLeaves, templateLeaves);
        Set<String> stale = diff(templateLeaves, codeLeaves);
        assertTrue(missing.isEmpty(), "模板缺这些代码实际读取的键:\n" + String.join("\n", missing));
        assertTrue(stale.isEmpty(), "模板列了代码不再读取的键:\n" + String.join("\n", stale));
    }

    @Test
    void 判据本身可被证伪() throws IOException {
        String text = new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8);
        Set<String> templateLeaves = templateLeaves(text);
        assertEquals(5, templateLeaves.size(), "五把键全在模板");
        assertTrue(templateLeaves.contains("tool.document.transcriber"));
        assertFalse(templateLeaves.contains("tool.document"), "容器节点不许当叶子");

        String truncated = text.replace("    transcriber:", "    # transcriber:");
        Set<String> missing = diff(propertyLeaves(DocumentProperties.class, "tool.document"),
                templateLeaves(truncated));
        assertEquals(1, missing.size(), "抹掉一键后必须恰好缺这一键: " + missing);
        assertTrue(missing.contains("tool.document.transcriber"));
    }

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
