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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Title: LlmLayeringGuardTest
 * Description: <b>族界守卫</b>（M16 修复轮 2026-10-21 定案：llm ＝纯模型层）——
 * 本模块的源码与 pom 都不许引用 tool 族的两个包名（{@code com.bidr.} ＋ {@code document} /
 * {@code mineru}）。文件解析入口与 MinerU 转录腿住在 core tool/document，依赖方向是
 * <b>document → llm 单向</b>；反向一旦长出来（有人图省事在 llm 里注入 FileMarkdownService），
 * llm 就被拖进 POI/PDFBox/引擎件，模型层再也拆不干净。
 * <p>
 * 三件套齐备（守卫纪律）：
 * <ul>
 *   <li><b>扫描面下限</b>：扫到的 java 文件数必须达地板值且必须含本模块核心类——空扫描/路径漂移不算绿；</li>
 *   <li><b>真实正对照腿</b>：document 侧确实单向引用本模块的 {@code ModelConfigProvider}，
 *       证明这条边界是"引用得到、只是不许反着来"，不是两边都没引用的假平静；</li>
 *   <li><b>负向合成腿</b>：合成含禁用包名的源码喂<b>同一谓词</b>必须判红，喂干净源码与本守卫自身源码必须判绿
 *       （防谓词写成 catch-all）。</li>
 * </ul>
 * 违禁包名一律<b>拼接构造</b>（同 authorization 族界守卫写法）：否则本文件自己就是扫描面里第一条命中。
 * 本模块其余 33 只测试是 JUnit4，本守卫同族写法——不混 jupiter，免 surefire 换 provider 造成 Tests run: 0。
 *
 * @author Sharp
 * @since 2026/10/21
 */
public class LlmLayeringGuardTest {

    private static final String FORBIDDEN_DOCUMENT = "com.bidr." + "document";

    private static final String FORBIDDEN_MINERU = "com.bidr." + "mineru";

    /** tool 族两个 artifactId：pom 面判法＝整串 {@code <artifactId>名</artifactId>} */
    private static final List<String> FORBIDDEN_POM_ARTIFACTS = Arrays.asList("document", "mineru");

    /**
     * java 文件地板数：实测本模块 src 下 161 只（main 125 ＋ test 36），地板取 120
     * ——防目录搬家后扫了个空仓冒充绿，又不至于因正常增删文件天天误报。
     */
    private static final int JAVA_FILE_FLOOR = 120;

    /** 1. 主判据：源码与 pom 零引用 tool 族（扫描面下限自证） */
    @Test
    public void llm源码与pom零引用tool族() throws IOException {
        Path moduleRoot = locateLlmModule();
        List<Path> sources = javaFiles(moduleRoot.resolve("src"));
        Assert.assertTrue("扫描面不足：llm 模块 java 文件 " + sources.size() + " 只（地板 " + JAVA_FILE_FLOOR + "）＝扫描失效",
                sources.size() >= JAVA_FILE_FLOOR);
        Assert.assertTrue("扫描面没覆盖到 provider 包＝扫描路径漂移",
                containsNamed(sources, "ModelConfigProvider.java"));

        List<String> hits = new ArrayList<>();
        for (Path file : sources) {
            if (referencesToolFamily(read(file))) {
                hits.add(moduleRoot.relativize(file).toString());
            }
        }
        String pom = read(moduleRoot.resolve("pom.xml"));
        for (String artifact : FORBIDDEN_POM_ARTIFACTS) {
            if (pom.contains("<artifactId>" + artifact + "</artifactId>")) {
                hits.add("pom.xml 依赖 " + artifact);
            }
        }
        Assert.assertEquals("llm 反向引用了 tool 族（import 或 pom）：" + hits,
                new ArrayList<String>(), hits);
    }

    /** 2. 负向合成腿：同一谓词拦得住脏源码、放得过干净源码与本守卫自身（防瞎、防 catch-all） */
    @Test
    public void 负向合成腿_谓词既不瞎也不是catchall() throws IOException {
        String polluted = "package com.bidr.llm.parse;\nimport " + FORBIDDEN_DOCUMENT
                + ".FileMarkdownService;\nclass X {}\n";
        Assert.assertTrue("合成脏样本没被判出＝守卫是瞎的", referencesToolFamily(polluted));

        Assert.assertTrue("第二个禁用包名没进谓词＝只钉了一半",
                referencesToolFamily("import " + FORBIDDEN_MINERU + ".MineruLocalClient;"));

        Assert.assertFalse("干净样本被判红＝谓词成了 catch-all，扫描结果无意义",
                referencesToolFamily("import com.bidr.llm.provider.ModelConfigProvider;\nclass Y {}\n"));

        // 自证：本守卫自身源码必须在扫描面内且判绿（拼接写法真的生效）
        Path self = locateLlmModule().resolve(
                "src/test/java/com/bidr/llm/config/LlmLayeringGuardTest.java");
        Assert.assertTrue("自证腿找不到本文件＝路径假设已坏", Files.isRegularFile(self));
        Assert.assertFalse("本守卫源码含禁用包名连续串＝拼接写法被破坏，主判据会自撞",
                referencesToolFamily(read(self)));
    }

    /** 3. 真实正对照腿：document 侧单向引用 llm 的 ModelConfigProvider（边界是活的） */
    @Test
    public void 正对照腿_document单向引用llm配置口() throws IOException {
        Path documentRoot = locateToolDocument();
        List<Path> sources = javaFiles(documentRoot.resolve("src/main/java"));
        Assert.assertFalse("document 侧扫描为空＝对照腿失效", sources.isEmpty());

        List<String> referencing = new ArrayList<>();
        for (Path file : sources) {
            if (read(file).contains("import com.bidr.llm.provider.ModelConfigProvider")) {
                referencing.add(file.getFileName().toString());
            }
        }
        Assert.assertTrue("document → llm 的单向引用少于 2 只文件（实测为 FileMarkdownService、"
                + "LlmVisionConfigProvider、DocumentAutoConfiguration）＝对照腿判据漂了，现值 " + referencing,
                referencing.size() >= 2);
    }

    // ---------------- 夹具 ----------------

    /** 守卫谓词：源码文本含任一禁用包名即违规 */
    private static boolean referencesToolFamily(String text) {
        return text.contains(FORBIDDEN_DOCUMENT) || text.contains(FORBIDDEN_MINERU);
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
