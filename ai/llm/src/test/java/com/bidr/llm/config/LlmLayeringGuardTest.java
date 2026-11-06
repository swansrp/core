package com.bidr.llm.config;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Title: LlmLayeringGuardTest
 * Description: <b>族界守卫</b>（SPI 反转新口径 2026-11-05，a94434be 推翻原 M16「document→llm 单向」）——
 * 依赖方向＝<b>llm → document 单向</b>：视觉配置适配器 {@code LlmVisionConfigProvider} 住本模块，
 * 把 {@code ModelConfigProvider} 翻成 document 自持的 {@code VisionConfigProvider} SPI 喂回去；
 * document 零 llm 编译依赖（8 工具链全编不再被 document 卡死的立项目标）。
 * 对 tool 族的另一成员 mineru 依旧<b>零引用</b>（「仅 MinerU 外」，转录腿整体住 document）。
 * <p>
 * 四腿齐备（守卫纪律）：
 * <ul>
 *   <li><b>主判据（反向腿）</b>：document 源码与 pom 零引用 llm，扫描面带地板值与核心类自证——空扫描/路径漂移不算绿；</li>
 *   <li><b>残留纯度腿</b>：llm 源码与 pom 零引用 mineru（反向放开只放 document 这一个）；</li>
 *   <li><b>负向合成腿</b>：合成含禁用包名的源码喂<b>同一谓词</b>必须判红，干净源码与含 javadoc
 *       提述的散文必须判绿（防谓词写成 catch-all）；</li>
 *   <li><b>真实正对照腿</b>：llm 侧确实单向引用 document 的 SPI（LlmVisionConfigProvider、
 *       LlmDefaultAutoConfiguration），pom 也真挂 document——证明这条边界是"引用得到、只是不许反着来"。</li>
 * </ul>
 * 🔴 判法带注释剥离：document 的 javadoc 有溯源散文提述（TranscribeStep/VisionEndpoint 记
 * {@code com.bidr.llm.parse.*} 契约来源），按原文 contains 会误伤散文——先剥块/行注释再判，
 * 只认真编译引用。
 * 违禁包名一律<b>拼接构造</b>（同 authorization 族界守卫写法）：否则本文件自己就是扫描面里第一条命中。
 * 本模块其余测试是 JUnit4，本守卫同族写法——不混 jupiter，免 surefire 换 provider 造成 Tests run: 0。
 *
 * @author Sharp
 * @since 2026/10/21（2026-11-05 随 SPI 反转改判方向）
 */
public class LlmLayeringGuardTest {

    private static final String FORBIDDEN_LLM = "com.bidr." + "llm";

    private static final String FORBIDDEN_MINERU = "com.bidr." + "mineru";

    /** pom 面判法＝整串 {@code <artifactId>名</artifactId>}（注释里的字样不算） */
    private static final String POM_TAG_LLM = "<artifactId>" + "llm</artifactId>";

    private static final String POM_TAG_MINERU = "<artifactId>" + "mineru</artifactId>";

    private static final String POM_TAG_DOCUMENT = "<artifactId>" + "document</artifactId>";

    /** document 侧 java 地板数：实测 src 下 46 只，地板取 40——防扫描空仓冒充绿 */
    private static final int DOCUMENT_FILE_FLOOR = 40;

    /** llm 侧 java 地板数：实测 src 下 197 只，地板取 120 */
    private static final int LLM_FILE_FLOOR = 120;

    /** llm 主源码地板数：实测 src/main/java 下 152 只，地板取 100 */
    private static final int LLM_MAIN_FILE_FLOOR = 100;

    /** 1. 主判据（反向腿）：document 源码与 pom 零引用 llm（剥注释后判；扫描面下限自证） */
    @Test
    public void document源码与pom零引用llm族() throws IOException {
        Path documentRoot = locateToolDocument();
        List<Path> sources = javaFiles(documentRoot.resolve("src"));
        Assert.assertTrue("扫描面不足：document 模块 java 文件 " + sources.size() + " 只（地板 "
                        + DOCUMENT_FILE_FLOOR + "）＝扫描失效",
                sources.size() >= DOCUMENT_FILE_FLOOR);
        Assert.assertTrue("扫描面没覆盖到 FileMarkdownService＝扫描路径漂移",
                containsNamed(sources, "FileMarkdownService.java"));
        Assert.assertTrue("扫描面没覆盖到 VisionConfigProvider＝扫描路径漂移",
                containsNamed(sources, "VisionConfigProvider.java"));

        List<String> hits = new ArrayList<>();
        for (Path file : sources) {
            if (referencesLlm(stripComments(read(file)))) {
                hits.add(documentRoot.relativize(file).toString());
            }
        }
        if (read(documentRoot.resolve("pom.xml")).contains(POM_TAG_LLM)) {
            hits.add("pom.xml 依赖 llm");
        }
        Assert.assertEquals("document 反向引用了 llm（import 或 pom）：" + hits,
                new ArrayList<String>(), hits);
    }

    /** 2. 残留纯度腿：llm 源码与 pom 零引用 mineru（反向放开只放 document，mineru 照旧禁入） */
    @Test
    public void llm源码与pom零引用mineru族() throws IOException {
        Path moduleRoot = locateLlmModule();
        List<Path> sources = javaFiles(moduleRoot.resolve("src"));
        Assert.assertTrue("扫描面不足：llm 模块 java 文件 " + sources.size() + " 只（地板 "
                        + LLM_FILE_FLOOR + "）＝扫描失效",
                sources.size() >= LLM_FILE_FLOOR);
        Assert.assertTrue("扫描面没覆盖到 provider 包＝扫描路径漂移",
                containsNamed(sources, "ModelConfigProvider.java"));

        List<String> hits = new ArrayList<>();
        for (Path file : sources) {
            if (referencesMineru(stripComments(read(file)))) {
                hits.add(moduleRoot.relativize(file).toString());
            }
        }
        if (read(moduleRoot.resolve("pom.xml")).contains(POM_TAG_MINERU)) {
            hits.add("pom.xml 依赖 mineru");
        }
        Assert.assertEquals("llm 引用了 mineru（仅 MinerU 外口径被破）：" + hits,
                new ArrayList<String>(), hits);
    }

    /** 3. 负向合成腿：同一谓词拦得住脏源码、放得过干净源码与 javadoc 提述（防瞎、防 catch-all） */
    @Test
    public void 负向合成腿_谓词既不瞎也不是catchall() throws IOException {
        String pollutedLlm = "package com.bidr.document.x;\nimport " + FORBIDDEN_LLM
                + ".provider.ModelConfigProvider;\nclass X {}\n";
        Assert.assertTrue("合成脏样本（llm）没被判出＝守卫是瞎的", referencesLlm(stripComments(pollutedLlm)));

        String pollutedMineru = "import " + FORBIDDEN_MINERU
                + ".MineruLocalClient;\nclass W {}\n";
        Assert.assertTrue("合成脏样本（mineru）没被判出＝第二个包名没进谓词",
                referencesMineru(stripComments(pollutedMineru)));

        Assert.assertFalse("干净样本被判红＝谓词成了 catch-all，扫描结果无意义",
                referencesLlm(stripComments("import com.bidr.document.FileMarkdownService;\nclass Y {}\n")));

        // javadoc 提述放行腿：真实扫描面里就有溯源散文，判红＝剥注释失灵、天天误报
        Assert.assertFalse("javadoc 提述被判红＝注释剥离失灵",
                referencesLlm(stripComments("/** 契约自 {@code " + FORBIDDEN_LLM
                        + ".parse.DocumentTranscriber} 带过来 */\nclass Z {}\n")));

        // 自证：本守卫自身源码拼接写法生效——剥注释后无连续禁用串。
        // 🔴 package 行须豁免：守卫必然住在被守的族里（com.bidr.llm.*），自己的包声明是
        // 结构性自提述，不算引用；两条主判据都不拿本文件当 llm 违禁扫描面
        // （1 号腿扫 document、2 号腿只对 mineru），排除它不会放走真实违规
        Path self = locateLlmModule().resolve(
                "src/test/java/com/bidr/llm/config/LlmLayeringGuardTest.java");
        Assert.assertTrue("自证腿找不到本文件＝路径假设已坏", Files.isRegularFile(self));
        String selfText = stripComments(read(self)).replaceFirst("(?m)^package .*;$", "");
        Assert.assertFalse("本守卫源码含 llm 禁用串＝拼接写法被破坏，主判据会自撞", referencesLlm(selfText));
        Assert.assertFalse("本守卫源码含 mineru 禁用串＝拼接写法被破坏", referencesMineru(selfText));
    }

    /** 4. 真实正对照腿：llm 侧单向引用 document 的 SPI（边界是活的，方向没有两头断） */
    @Test
    public void 正对照腿_llm单向引用document接口() throws IOException {
        Path llmRoot = locateLlmModule();
        List<Path> mains = javaFiles(llmRoot.resolve("src/main/java"));
        Assert.assertTrue("llm 主源码扫描不足：" + mains.size() + " 只（地板 "
                        + LLM_MAIN_FILE_FLOOR + "）＝扫描失效",
                mains.size() >= LLM_MAIN_FILE_FLOOR);

        List<String> referencing = new ArrayList<>();
        for (Path file : mains) {
            if (read(file).contains("import com.bidr." + "document.")) {
                referencing.add(file.getFileName().toString());
            }
        }
        Assert.assertTrue("llm → document 的单向引用少于 2 只文件（应为 LlmVisionConfigProvider、"
                + "LlmDefaultAutoConfiguration）＝对照腿判据漂了，现值 " + referencing,
                referencing.size() >= 2);
        Assert.assertTrue("llm pom 未挂 document＝正对照腿不完整",
                read(llmRoot.resolve("pom.xml")).contains(POM_TAG_DOCUMENT));
    }

    // ---------------- 夹具 ----------------

    /** 守卫谓词：剥注释后的源码文本含禁用包名即违规（javadoc 溯源提述不算编译引用） */
    private static boolean referencesLlm(String text) {
        return text.contains(FORBIDDEN_LLM);
    }

    private static boolean referencesMineru(String text) {
        return text.contains(FORBIDDEN_MINERU);
    }

    /** 剥块注释（含 javadoc）与行注释；本扫描面为自家源码，字符串内注释符的误差可容忍 */
    private static String stripComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static boolean containsNamed(List<Path> files, String fileName) {
        for (Path file : files) {
            if (fileName.equals(file.getFileName().toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    /**
     * 从 basedir/user.dir 起向上找 llm 模块根：不假设 cwd 层级，只认模块自身特征
     * （目录名 ＋ pom 坐标 ＋ src/main/java 在场）；找不到直接报错（宁可报红不假绿）。
     */
    private static Path locateLlmModule() {
        Path start = Paths.get(System.getProperty("basedir",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (isLlmModule(dir)) {
                return dir;
            }
        }
        // 兜底：cwd 落在上层聚合（core / server / 项目根）时按已知相对坐标认
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            for (String relative : new String[]{"ai/llm", "framework/core/ai/llm", "core/ai/llm"}) {
                Path candidate = dir.resolve(relative);
                if (isLlmModule(candidate)) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("无法从 " + start + " 定位 core ai/llm 模块根（路径假设已坏，拒绝空扫描）");
    }

    private static boolean isLlmModule(Path dir) {
        return dir != null
                && "llm".equals(String.valueOf(dir.getFileName()))
                && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))
                && toText(dir.resolve("pom.xml")).contains("<artifactId>llm</artifactId>");
    }

    /** document 模块根＝llm 之上的 core 根换到 tool/document（跨族界只读源码、不引依赖） */
    private static Path locateToolDocument() {
        Path llm = locateLlmModule();
        for (Path dir = llm.getParent(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("tool").resolve("document");
            if (Files.isRegularFile(candidate.resolve("pom.xml"))
                    && Files.isDirectory(candidate.resolve("src").resolve("main").resolve("java"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("未能从 " + llm + " 上溯定位 tool/document——对照腿无从取证");
    }

    private static String toText(Path file) {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            return read(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
