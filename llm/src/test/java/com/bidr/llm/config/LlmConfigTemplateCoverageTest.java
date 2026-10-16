package com.bidr.llm.config;

import com.bidr.llm.constant.param.LlmParam;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Title: LlmConfigTemplateCoverageTest
 * Description: 模板↔代码对账针（离线）。
 * <p>{@code src/main/resources/llm-config.template.yml} 是给"下一个项目逐项填空"的配置面清单，
 * 它的价值全在**跟代码读的键一致**：少一列＝现场照模板填完仍然缺配置（就是 10-16 这次
 * 「llm.api-key 没人桥接、live agent 报密钥未配置」那类难的归因）；多一列＝模板承诺了一个代码不读的键，
 * 运维改了没反应还以为配错了。所以两边集合必须严格相等，不是"模板 ⊇ 代码"。</p>
 * <p>键集合的正源是代码里的 {@code ${llm...}} 占位符（正则扫 {@code src/main/java}），
 * 模板侧按缩进还原成点分全名后只取叶子（父节点是容器，代码不会直接读）。
 * 正向腿＝两边都非空且等长；空扫描一律当失败而不是 PASS。</p>
 *
 * @author Sharp
 * @since 2026/10/16
 */
public class LlmConfigTemplateCoverageTest {

    /** surefire 工作目录＝模块 basedir（server/framework/core/llm） */
    private static final Path CODE_DIR = Paths.get("src/main/java");
    private static final Path TEMPLATE = Paths.get("src/main/resources/llm-config.template.yml");

    /** 代码读的键：${llm.xxx:默认} 里冒号前的部分 */
    private static final Pattern CODE_KEY = Pattern.compile("\\$\\{(llm[^:}]*)");

    /** 模板里的一行声明：缩进 + 键名 + 冒号（行尾注释与行内值都不影响键名判定） */
    private static final Pattern TEMPLATE_KEY = Pattern.compile("^( *)([A-Za-z0-9._-]+):(.*)$");

    /** 运行期可覆盖标记后面必须点到真实存在的参数名，否则就是给运维一个查不到的键 */
    private static final Pattern SYS_CONFIG_REF = Pattern.compile("sys_config:\\s*([A-Z][A-Z0-9_]*)");

    private Set<String> codeKeys() throws IOException {
        Assert.assertTrue("找不到 " + CODE_DIR.toAbsolutePath() + "（工作目录假设被破坏，扫描假设失效）",
                Files.isDirectory(CODE_DIR));
        Set<String> keys = new TreeSet<>();
        final List<Path> sources = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(CODE_DIR)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(sources::add);
        }
        Assert.assertFalse("src/main/java 下没有 java 文件＝扫描空转", sources.isEmpty());
        for (Path file : sources) {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Matcher m = CODE_KEY.matcher(text);
            while (m.find()) {
                keys.add(m.group(1));
            }
        }
        Assert.assertFalse("代码里没有任何 ${llm...} 占位符＝扫描假设坏了（本守卫会假绿）", keys.isEmpty());
        return keys;
    }

    /**
     * 缩进还原成点分全名：父链靠缩进栈维护，因此模板换成更深一级也照样认。
     *
     * @return 全名 → 该行原文（含行尾注释，供标记腿判读）
     */
    private Map<String, String> templateKeys(String yaml) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> chain = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        for (String line : yaml.split("\r?\n", -1)) {
            String stripped = stripComment(line);
            Matcher m = TEMPLATE_KEY.matcher(stripped);
            if (!m.matches()) {
                continue;
            }
            int indent = m.group(1).length();
            String name = m.group(2);
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                chain.remove(chain.size() - 1);
            }
            List<String> path = new ArrayList<>(chain);
            path.add(name);
            // 存原始行（含行尾注释）：🔁/✋ 标记与 sys_config 格名就写在注释里，标记腿要读它
            out.put(String.join(".", path), line);
            indents.add(indent);
            chain.add(name);
        }
        return out;
    }

    /** 去掉行内注释（模板不含引号包裹的 #，故首个 # 之后一律丢弃） */
    private String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash < 0 ? line : line.substring(0, hash);
    }

    /** 只取叶子：没有任何键以「本键.」开头的才算叶子——父节点是容器，代码不会直接读它 */
    private Set<String> leaves(Map<String, String> all) {
        Set<String> out = new TreeSet<>();
        for (String key : all.keySet()) {
            boolean hasChild = false;
            for (String other : all.keySet()) {
                if (other.startsWith(key + ".")) {
                    hasChild = true;
                    break;
                }
            }
            if (!hasChild) {
                out.add(key);
            }
        }
        return out;
    }

    @Test
    public void 模板列全代码读取的每一个键() throws IOException {
        Assert.assertTrue("找不到模板 " + TEMPLATE.toAbsolutePath()
                + "（清单失踪＝下一个项目只能靠读源码猜配置面）", Files.isRegularFile(TEMPLATE));
        Map<String, String> declared = templateKeys(
                new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8));
        Set<String> templateLeaves = leaves(declared);
        Set<String> code = codeKeys();

        Set<String> missing = new TreeSet<>(code);
        missing.removeAll(templateLeaves);
        Set<String> stale = new TreeSet<>(templateLeaves);
        stale.removeAll(code);
        Assert.assertTrue("模板缺这些代码实际读取的键（现场照模板填完仍会缺配置）:\n" + String.join("\n", missing),
                missing.isEmpty());
        Assert.assertTrue("模板列了代码不再读取的键（运维改了没反应，会往错误方向排查）:\n" + String.join("\n", stale),
                stale.isEmpty());
    }

    /**
     * 标记腿：写了 🔁 就得给出参数页里真存在的格名。
     * 标错方向的代价不对称——标 ✋ 的键运维只会去改 yml 重启（顶多多等一次发版），
     * 标 🔁 却指了一个不存在/拼错的参数名，等于让人在管理页里找一个不填也不会坏的格子。
     */
    @Test
    public void 运行期可覆盖标记必须指到真实参数名() throws IOException {
        Map<String, String> declared = templateKeys(
                new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8));
        List<String> problems = new ArrayList<>();
        int marked = 0;
        for (Map.Entry<String, String> entry : declared.entrySet()) {
            String line = entry.getValue();
            if (!line.contains("🔁")) {
                continue;
            }
            marked++;
            Matcher m = SYS_CONFIG_REF.matcher(line);
            if (!m.find()) {
                problems.add(entry.getKey() + " 标了 🔁 却没写 sys_config: <参数名>");
                continue;
            }
            try {
                LlmParam.valueOf(m.group(1));
            } catch (IllegalArgumentException e) {
                problems.add(entry.getKey() + " 指向 LlmParam 里不存在的参数：" + m.group(1));
            }
        }
        Assert.assertTrue("模板里没有任何 🔁 标记＝标记腿空转（本批下沉的两键必须带）", marked >= 2);
        Assert.assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    /** 针还活着：从模板集合里抹掉一键，判据必须当场报出来（否则上一条测试可能是假绿） */
    @Test
    public void 扫描与判据本身可被证伪() throws IOException {
        Map<String, String> declared = templateKeys(
                new String(Files.readAllBytes(TEMPLATE), StandardCharsets.UTF_8));
        Assert.assertTrue("模板没解析出任何键＝解析器坏了", declared.size() >= 20);
        Set<String> templateLeaves = leaves(declared);

        // 叶子里不该出现容器名：出现即说明"父节点也算键"，两集合的比较尺就错了
        Assert.assertFalse("容器节点 llm.vision 被当成叶子，叶子判据坏了",
                templateLeaves.contains("llm.vision"));
        Assert.assertTrue("叶子集合应包含 llm.vision.model-name",
                templateLeaves.contains("llm.vision.model-name"));

        // 抹掉一个叶子 ⇒ 与代码集合的差集必须恰好是它（正向对照，证明判据真的在比这两个集合）
        String victim = "llm.streaming.max-wait-ms";
        Set<String> truncated = new TreeSet<>(templateLeaves);
        truncated.remove(victim);
        Set<String> missing = new TreeSet<>(codeKeys());
        missing.removeAll(truncated);
        Assert.assertEquals("截掉一键后必须恰好缺这一键（判据真的在按集合差比对）: " + missing, 1, missing.size());
        Assert.assertTrue(missing.contains(victim));
    }

    /** 目录找不到要报错而不是空过：模板路径写错时上面两条都会变成"没有可报" */
    @Test
    public void 模板文件本身可读且非空() throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(TEMPLATE);
            Assert.assertTrue("模板是空文件", bytes.length > 200);
        } catch (IOException e) {
            throw new UncheckedIOException("读不到模板：" + TEMPLATE.toAbsolutePath(), e);
        }
    }
}
