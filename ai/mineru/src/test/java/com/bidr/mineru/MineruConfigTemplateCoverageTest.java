package com.bidr.mineru;

import org.junit.Assert;
import org.junit.Test;

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

/**
 * Title: MineruConfigTemplateCoverageTest
 * Description: 模板对账守卫（判据仿 core/llm 的 LlmConfigTemplateCoverageTest，但代码面是
 * {@link MineruProperties} 的 @ConfigurationProperties 绑定而非 {@code ${...}} 占位符，
 * 故代码集合取<b>反射展开字段树</b>）：
 * <b>模板叶子键集合与 MineruProperties 展开键集合必须完全相等</b>——漏写一把＝现场照模板
 * 填完仍缺配置；多写一把＝模板承诺了代码不读的键，两种都判红。模板注释行（只有说明没键名）
 * 不参与对账。
 * <p>
 * 自证腿：从两侧各抹一键，判据必须当场报出恰好那把（防死针）；空解析有下限（模板至少
 * 解析出 14 个叶子）。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class MineruConfigTemplateCoverageTest {

    private static final Path TEMPLATE =
            Paths.get(System.getProperty("basedir", System.getProperty("user.dir")),
                    "src", "main", "resources", "mineru-config.template.yml");

    private static final Pattern LINE = Pattern.compile("^( *)([A-Za-z0-9_-]+):(.*)$");

    @Test
    public void 模板叶子键与配置类字段完全相等() throws IOException {
        Assert.assertTrue("模板文件失踪＝下一个项目只能靠读源码猜配置面", Files.isRegularFile(TEMPLATE));
        Set<String> templateLeaves = templateLeaves(new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8));
        Set<String> codeLeaves = propertyLeaves(MineruProperties.class, "mineru");

        Set<String> missing = diff(codeLeaves, templateLeaves);
        Set<String> stale = diff(templateLeaves, codeLeaves);
        Assert.assertTrue("模板缺这些代码实际读取的键（现场照模板填完仍会缺配置）:\n" + String.join("\n", missing),
                missing.isEmpty());
        Assert.assertTrue("模板列了代码不再读取的键（运维改了没反应）:\n" + String.join("\n", stale),
                stale.isEmpty());
    }

    /**
     * 自证腿（防死针）：从两侧各抹一键，差集必须恰好是它——证明判据真的在按集合差比对，
     * 而不是两个空集合的假绿
     */
    @Test
    public void 判据本身可被证伪() throws IOException {
        String text = new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8);
        Set<String> templateLeaves = templateLeaves(text);
        Assert.assertTrue("模板没解析出任何键＝解析器坏了", templateLeaves.size() >= 14);
        Assert.assertTrue("叶子集合应含 mineru.cloud.model-version", templateLeaves.contains("mineru.cloud.model-version"));
        // 容器名不许当叶子：出现即"父节点也算键"，比较尺就错了
        Assert.assertFalse("容器节点 mineru.local 被当成叶子", templateLeaves.contains("mineru.local"));

        // 抹掉一个叶子（把该行注释化）⇒ 与代码集合的差必须恰好是它
        String truncated = text.replace("    model-version:", "    # model-version:");
        Set<String> missing = diff(propertyLeaves(MineruProperties.class, "mineru"), templateLeaves(truncated));
        Assert.assertEquals("抹掉一键后必须恰好缺这一键: " + missing, 1, missing.size());
        Assert.assertTrue(missing.contains("mineru.cloud.model-version"));
    }

    /**
     * 解析模板为全路径叶子键：按缩进栈拼父链；行尾注释不算键名；值与注释都不影响判定。
     * 纯注释行（以 # 开头，冒号前只有空白）跳过
     */
    static Set<String> templateLeaves(String text) {
        List<String[]> lines = Stream.of(text.split("\n", -1))
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

    /**
     * 反射展开 @ConfigurationProperties 类的字段树为点号全路径叶子键
     * （字段名转 kebab-case＝Spring 宽松绑定的规范名）；嵌套静态类递归、
     * 排除非配置型字段（MAPPER 一类的静态/常量）
     */
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
