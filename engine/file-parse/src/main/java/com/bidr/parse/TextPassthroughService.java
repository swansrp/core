package com.bidr.parse;

import com.bidr.blob.BlobStore;
import com.bidr.blob.Digests;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Title: TextPassthroughService
 * Description: 纯文本直通腿（M17 棒 3 自 InkHub {@code TextPassthroughService} 迁入，
 * D-5 直通腿内置化——扩展名清单从业务词变引擎文件语义）：md/txt 直接读文本、zip 连带包
 * 就地解开，把正文里的图片引用换成稳定占位（{@code BlobStore.IMAGE_PLACEHOLDER_SCHEME}，
 * {@code {sha}/{name}} 形态）后落解析产物——绕开
 * 外部解析引擎（它按内容嗅探不收纯文本，改名也绕不过，实测提交那一刻就 HTTP 400
 * {@code Unsupported file type}）。
 *
 * <p><b>为什么产物要落在同一套 key 上</b>：正文仍写 {@code parsed/{解析锚}/v{n}.md}、图仍写
 * {@code image/{解析锚}/{name}}，下游三件事一行代码都不用改——图注（按占位正则扫正文）、
 * 读取边缘现签、删除回收。文本档没有物理页，所以<b>不落 pagemap</b>：下游按该键判空降级，
 * 与"office 转换失败按原件解析"同一形态。</p>
 *
 * <p><b>zip 连带包是相对路径图唯一的来路</b>：用户手上典型的 md 是别的工具导出的，正文写
 * {@code ![](images/x.png)}，而上传端点是单文件——图片字节根本进不来。按包传：zip 里放入口
 * md 与它引的图，服务端按路径尾部配对。包是不可信输入，四道闸：条目名越界（zip-slip／
 * 绝对路径）直接丢、条目数与解压总字节上限、单图字节上限（超限只丢那张、不废整包）、
 * 入口文件必须存在。</p>
 *
 * <p><b>外链图片默认不拉</b>：拉取＝让服务端按用户给的地址去发请求（SSRF 面）。
 * 只有主机命中 {@code engine.parse.passthrough-remote-image-hosts} 白名单才拉，缺省空＝
 * 一条都不拉，引用原样留在正文（宁可留坏链让人看见，也不静默删图——与内置产物腿的
 * {@code rewriteImages} 同一取舍）。data URI 与包内图没有这个问题，直接落库。</p>
 *
 * <p><b>只处理 markdown 引用形态</b>（{@code ![alt](target "title")}）：HTML {@code <img>} 里的图
 * 不改写也不生成图注（图注服务认的是占位正则），混用 HTML 的导出件请按图注那条轨重新导出。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Slf4j
public class TextPassthroughService {

    /** 直通来源：裸文本（md/markdown/txt） */
    public static final String SOURCE_TEXT = "TEXT";

    /** 直通来源：zip 连带包（入口文本件＋图片件） */
    public static final String SOURCE_BUNDLE = "BUNDLE";

    /** 直通文本扩展名（引擎文件语义，D-5：清单正源从业务词变 core） */
    private static final Set<String> TEXT_EXTS = new HashSet<>(Arrays.asList("md", "markdown", "txt"));

    /** 连带包扩展名（与 {@link #isPassthroughExt} 一起构成直通判据） */
    private static final String EXT_ZIP = "zip";

    /** 正文图片引用：与 {@code DefaultParseArtifactWriter.MD_IMAGE} 同一把尺（group2＝引用目标，group3＝可选 title） */
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^)\\s]+)([^)]*)\\)");

    /** data URI：{@code data:image/png;base64,....}（大小写与空格宽容，base64 体允许换行） */
    private static final Pattern DATA_URI =
            Pattern.compile("^data:image/([a-zA-Z0-9.+-]+);\\s*base64,(.*)$", Pattern.DOTALL);

    /** 正文里已经是我们的占位（自家导出件），原样保留（scheme 常量正源在 BlobStore） */
    private static final String PLACEHOLDER_SCHEME = BlobStore.IMAGE_PLACEHOLDER_SCHEME;

    /** 可入库图片扩展名（与图片上传/MinerU 客户端认领面同词汇表） */
    private static final Set<String> IMAGE_EXTS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff"));

    /** 单图字节上限（与裸图上传同尺，10MB：超了只丢这张图，不废整包） */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    /** 连带包条目数上限（挡"一目录十万张碎图"这类误传，正常导出件远到不了） */
    private static final int MAX_BUNDLE_ENTRIES = 2000;

    /** 连带包解压总字节上限（zip bomb 闸；原件本身还受上传闸管） */
    private static final long MAX_BUNDLE_BYTES = 64L * 1024 * 1024;

    /** 单条目读入上限：入口文本件够用，图片另有 {@link #MAX_IMAGE_BYTES} */
    private static final long MAX_ENTRY_BYTES = 64L * 1024 * 1024;

    /** Windows 自带"压缩到 ZIP 文件夹"写的条目名不带 UTF-8 标志位，中文按 GBK 才解得对 */
    private static final Charset ZIP_NAME_CHARSET = Charset.forName("GBK");

    private final BlobStore storage;

    /** 外链图片主机白名单（{@code engine.parse.passthrough-remote-image-hosts}，缺省空＝不拉） */
    private final String remoteImageHosts;

    /** 外链取字节器：真形态走 HTTP，单测注入假件（离线不碰网络） */
    RemoteFetcher remoteFetcher = this::httpFetch;

    public TextPassthroughService(BlobStore storage, String remoteImageHosts) {
        this.storage = storage;
        this.remoteImageHosts = remoteImageHosts;
    }

    /** 直通判据：纯文本三档＋连带包 zip（{@code ext} 小写扩展名） */
    public static boolean isPassthroughExt(String ext) {
        return ext != null && (TEXT_EXTS.contains(ext) || EXT_ZIP.equals(ext));
    }

    /**
     * 读一份直通正文并把能弄到字节的图落库、引用改写为占位，产物落 {@code parsed/{锚}/v{n}.md}。
     *
     * @param anchorSha   解析锚（文本/包未转换，{@code parse_sha} 为 NULL 时即原件 sha）
     * @param variant     键代数段（提交方给的透传值，与外部引擎轨同 key 规则）
     * @param ext         小写扩展名
     * @param anchorBytes 锚字节（裸文本＝文件本身，zip＝连带包）
     */
    public Result passthrough(String anchorSha, int variant, String ext, byte[] anchorBytes) {
        Bundle bundle = EXT_ZIP.equals(ext) ? readBundle(anchorBytes) : readPlain(anchorBytes);
        Inlined inlined = inlineImages(anchorSha, bundle.markdown, bundle.images);
        String markdown = DefaultParseArtifactWriter.rewriteImages(inlined.markdown, inlined.refs);
        String markdownKey = BlobStore.parsedMarkdownKey(anchorSha, variant);
        storage.put(markdownKey, markdown.getBytes(StandardCharsets.UTF_8),
                BlobStore.contentTypeOf("x.md"));
        Result result = Result.of(bundle.entryName == null ? SOURCE_TEXT : SOURCE_BUNDLE, bundle.entryName,
                markdownKey, markdown, inlined.stored, inlined.kept, inlined.unresolved);
        log.info("直通产物已落库: 锚={} v={} 来源={} 入口={} 字符={} 内联图={} 沿用占位={} 未解析引用={}",
                anchorSha, variant, result.getSource(), result.getEntryName(), markdown.length(),
                result.getStoredImages(), result.getKeptPlaceholders(), result.getUnresolvedRefs());
        return result;
    }

    // ==================== 入口正文与连带包 ====================

    private Bundle readPlain(byte[] bytes) {
        String markdown = decodeText(bytes);
        if (!StringUtils.hasText(markdown)) {
            throw new IllegalStateException("这份文本没有内容");
        }
        Bundle bundle = new Bundle();
        bundle.markdown = markdown;
        return bundle;
    }

    /**
     * 解连带包：条目名越界即丢，闸住条目数与解压总字节，入口取"最浅最短"的文本件，其余图片件按路径留着配对。
     *
     * <p>非图片条目只丢不报（zip 里常有 {@code __MACOSX/}、{@code .DS_Store} 这类伴生件）；
     * 入口找不到才人话失败——这时整包没意义，静默成功只会让用户以为传上去了。</p>
     */
    private Bundle readBundle(byte[] zipBytes) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        List<String> textNames = new ArrayList<>();
        int count = 0;
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes), ZIP_NAME_CHARSET)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++count > MAX_BUNDLE_ENTRIES) {
                    throw new IllegalStateException(
                            "连带包条目过多（上限 " + MAX_BUNDLE_ENTRIES + "），请精简后再上传");
                }
                String name = normalizeEntry(entry.getName());
                if (name.isEmpty()) {
                    log.warn("连带包条目名越界，已丢弃: {}", entry.getName());
                    continue;
                }
                byte[] bytes = readEntry(zis, name);
                total += bytes.length;
                if (total > MAX_BUNDLE_BYTES) {
                    throw new IllegalStateException(
                            "连带包解压后过大（上限 " + MAX_BUNDLE_BYTES / 1024 / 1024 + "MB），请拆分后再上传");
                }
                entries.put(name, bytes);
                if (isTextExt(extOf(name))) {
                    textNames.add(name);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("连带包读取失败：" + e.getMessage());
        }
        String entryName = pickEntry(textNames);
        if (entryName == null) {
            throw new IllegalStateException(
                    "连带包里没有找到 Markdown/文本入口文件（.md/.markdown/.txt）");
        }
        Bundle bundle = new Bundle();
        bundle.entryName = entryName;
        bundle.markdown = decodeText(entries.get(entryName));
        if (!StringUtils.hasText(bundle.markdown)) {
            throw new IllegalStateException("入口文件 " + entryName + " 没有内容");
        }
        bundle.images = new HashMap<>();
        for (Map.Entry<String, byte[]> item : entries.entrySet()) {
            if (!item.getKey().equals(entryName) && IMAGE_EXTS.contains(extOf(item.getKey()))) {
                bundle.images.put(item.getKey(), item.getValue());
            }
        }
        log.info("连带包已解开: 入口={} 文本件={} 图片件={}", entryName, textNames.size(), bundle.images.size());
        return bundle;
    }

    private byte[] readEntry(ZipInputStream zis, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        long read = 0;
        while ((len = zis.read(buf)) > 0) {
            read += len;
            if (read > MAX_ENTRY_BYTES) {
                throw new IllegalStateException(
                        "连带包里的 " + name + " 过大（上限 " + MAX_ENTRY_BYTES / 1024 / 1024 + "MB）");
            }
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }

    /** 直通文本扩展名判定（引擎文件语义正源） */
    public static boolean isTextExt(String ext) {
        return ext != null && TEXT_EXTS.contains(ext);
    }

    /**
     * 入口选择：路径最浅优先、再取名字最短、最后按字典序（同一份包每次必须选到同一件，
     * 否则重跑会把另一件当正文、产物 sha 也跟着漂）。
     */
    static String pickEntry(List<String> textNames) {
        String best = null;
        for (String name : textNames) {
            if (best == null || depth(name) < depth(best)
                    || (depth(name) == depth(best) && name.length() < best.length())
                    || (depth(name) == depth(best) && name.length() == best.length()
                    && name.compareTo(best) < 0)) {
                best = name;
            }
        }
        return best;
    }

    private static int depth(String name) {
        int count = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '/') {
                count++;
            }
        }
        return count;
    }

    /**
     * 条目名归一＋越界判定：反斜杠归一成斜杠、去掉 {@code ./}，出现 {@code ..} 段或以 {@code /} 开头
     * 一律判越界（返回空串由调用方丢弃）。连带包来自用户桌面，{@code ../../} 这类路径不能信。
     */
    static String normalizeEntry(String rawName) {
        if (rawName == null) {
            return "";
        }
        String name = rawName.replace('\\', '/');
        if (name.startsWith("/")) {
            return "";
        }
        for (String segment : name.split("/")) {
            if ("..".equals(segment)) {
                return "";
            }
        }
        return name.startsWith("./") ? name.substring(2) : name;
    }

    /**
     * 文本解码：UTF-8 优先（BOM 剥掉），解不动回落 GBK。
     *
     * <p>回落腿不是装饰：Windows 记事本存的 .txt 多为 GBK，只按 UTF-8 硬解会得到一串
     * {@code \uFFFD}，正文进了知识库也检索不出词，而流水线一路绿——这种坏最难被发现。</p>
     */
    static String decodeText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        // BOM（码点 0xFEFF）不是正文字符：留着会让首行标题带上不可见前缀，检索与展示都难对齐
        if (!utf8.isEmpty() && utf8.charAt(0) == 0xFEFF) {
            utf8 = utf8.substring(1);
        }
        return utf8.indexOf(0xFFFD) >= 0 ? new String(bytes, ZIP_NAME_CHARSET) : utf8;
    }

    // ==================== 图片内联化 ====================

    /**
     * 扫正文里的图片引用，逐条决定"字节从哪来"：包内相对路径 → 配对；data URI → 就地解码；
     * 命中白名单的外链 → 拉取；已经是占位 → 原样保留；余下（越界路径、非图片、拉不到、包里没有）
     * 一律<b>原样保留并计数</b>——坏链看得见，比静默删图好。
     */
    private Inlined inlineImages(String anchorSha, String markdown, Map<String, byte[]> images) {
        Inlined inlined = new Inlined();
        inlined.markdown = markdown;
        Matcher matcher = MD_IMAGE.matcher(markdown);
        while (matcher.find()) {
            String target = matcher.group(2);
            if (target.startsWith(PLACEHOLDER_SCHEME)) {
                inlined.kept++;
                continue;
            }
            Resolved resolved = resolve(target, images, inlined);
            if (resolved == null) {
                continue;
            }
            String objectName = objectName(resolved.bytes, resolved.name, resolved.ext);
            storage.put(BlobStore.imageKey(anchorSha, objectName), resolved.bytes,
                    BlobStore.contentTypeOf(objectName));
            inlined.refs.put(target, BlobStore.imagePlaceholder(anchorSha, objectName));
            inlined.stored++;
        }
        return inlined;
    }

    /**
     * 图片对象名＝{@code 内容sha前8位-清洗后的原名}。
     *
     * <p>前缀不是可有可无：连带包里 {@code a/x.png} 与 {@code b/x.png} 是两张不同的图，
     * 而 {@link BlobStore#imageObjectName} 只留最后一段、还会把中文与空格洗成下划线，
     * 不加水印就会互相覆盖；同内容同名的两张图则收敛成同一个对象（覆盖写幂等）。</p>
     */
    static String objectName(byte[] bytes, String target, String ext) {
        String base = BlobStore.imageObjectName(target);
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        if (!StringUtils.hasText(stem)) {
            stem = "image";
        }
        return Digests.sha256Hex(bytes).substring(0, 8) + "-" + stem + "." + ext;
    }

    /**
     * 一条引用的字节从哪来，连同落库用的名字与扩展名；拿不到字节返回 null。
     *
     * <p>每条失败路径都只计一次 {@code unresolved}（{@link #reject}），且都保留原引用——
     * 计数是给"为什么这篇的图点不开"归因用的，不是给用户报错用的。</p>
     */
    private Resolved resolve(String target, Map<String, byte[]> images, Inlined inlined) {
        if (target.startsWith("data:")) {
            Matcher matcher = DATA_URI.matcher(target);
            if (!matcher.matches()) {
                return reject(inlined, target, "data URI 形态不认识");
            }
            String ext = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!IMAGE_EXTS.contains(ext)) {
                return reject(inlined, target, "data URI 不是可入库的图片格式");
            }
            byte[] bytes = decodeBase64(matcher.group(2));
            if (bytes == null) {
                return reject(inlined, target, "data URI 图片解不出为空或超单图上限");
            }
            // data URI 没有可用文件名，落库名靠内容 sha 前缀撑开（objectName 补 stem）
            return new Resolved(bytes, "inline", ext);
        }
        if (target.startsWith("http://") || target.startsWith("https://")) {
            // 扩展名与落库名都取自去掉 ?/# 尾巴后的目标：外链常带签名参数，按原串取后缀会得到 "png?X-Amz=…"
            String clean = normalizeTarget(target);
            String ext = extOf(clean);
            if (!IMAGE_EXTS.contains(ext)) {
                return reject(inlined, target, "外链目标不是图片格式");
            }
            byte[] bytes = fetchRemote(target, inlined);
            return bytes == null ? null : new Resolved(bytes, clean, ext);
        }
        byte[] bytes = matchBundle(target, images, inlined);
        if (bytes == null) {
            return null;
        }
        String ext = extOf(target);
        if (!IMAGE_EXTS.contains(ext)) {
            return reject(inlined, target, "包内配对到的条目不是图片格式");
        }
        return new Resolved(bytes, target, ext);
    }

    private Resolved reject(Inlined inlined, String target, String reason) {
        inlined.unresolved++;
        log.warn("{}，原样保留: {}", reason, brief(target));
        return null;
    }

    /** MIME 解码器容忍折行与空白（导出件常把长 base64 换行写进正文）；空或超单图上限都算失败 */
    private static byte[] decodeBase64(String body) {
        try {
            byte[] bytes = Base64.getMimeDecoder().decode(body);
            return bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES ? null : bytes;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private byte[] fetchRemote(String target, Inlined inlined) {
        if (!hostAllowed(target)) {
            inlined.unresolved++;
            log.warn("外链图片主机不在白名单（engine.parse.passthrough-remote-image-hosts），原样保留: {}",
                    brief(target));
            return null;
        }
        byte[] bytes;
        try {
            bytes = remoteFetcher.fetch(target);
        } catch (Exception e) {
            inlined.unresolved++;
            log.warn("外链图片拉取失败，原样保留: {} {}", brief(target), e.getMessage());
            return null;
        }
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            inlined.unresolved++;
            log.warn("外链图片为空或过大，原样保留: {}", brief(target));
            return null;
        }
        return bytes;
    }

    /** 主机精确匹配白名单（scheme 只认 http/https，其余一律不拉） */
    private boolean hostAllowed(String url) {
        if (!StringUtils.hasText(remoteImageHosts)) {
            return false;
        }
        String host;
        try {
            java.net.URI uri = new java.net.URI(url);
            host = uri.getHost();
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (host == null || !("http".equals(scheme) || "https".equals(scheme))) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        String wanted = host.toLowerCase(Locale.ROOT);
        for (String allowed : remoteImageHosts.split(",")) {
            if (wanted.equals(allowed.trim().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 包内配对：先按整路径，再按"尾段路径"，最后按文件名（唯一才认）。
     * 多义不猜——猜错就是把 A 图的字节写到 B 图的引用上，图注与召回都会跟着错。
     */
    static byte[] findBundleImage(String target, Map<String, byte[]> images) {
        String key = normalizeTarget(target);
        if (images.containsKey(key)) {
            return images.get(key);
        }
        byte[] hit = null;
        String suffix = "/" + key;
        for (Map.Entry<String, byte[]> item : images.entrySet()) {
            if (item.getKey().endsWith(suffix)) {
                if (hit != null) {
                    return null;
                }
                hit = item.getValue();
            }
        }
        if (hit != null) {
            return hit;
        }
        String base = key.substring(key.lastIndexOf('/') + 1);
        for (Map.Entry<String, byte[]> item : images.entrySet()) {
            if (item.getKey().substring(item.getKey().lastIndexOf('/') + 1).equals(base)) {
                if (hit != null) {
                    return null;
                }
                hit = item.getValue();
            }
        }
        return hit;
    }

    /** 引用目标归一：反斜杠归斜杠、去 {@code ./}、去 {@code #}/{@code ?} 尾巴、能百分号解码就解一次 */
    static String normalizeTarget(String target) {
        String value = target.replace('\\', '/');
        if (value.startsWith("./")) {
            value = value.substring(2);
        }
        int cut = value.indexOf('#');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        cut = value.indexOf('?');
        if (cut >= 0) {
            value = value.substring(0, cut);
        }
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private byte[] matchBundle(String target, Map<String, byte[]> images, Inlined inlined) {
        byte[] bytes = findBundleImage(target, images);
        if (bytes == null) {
            inlined.unresolved++;
            log.warn("引用在连带包里没配到图片，原样保留: {}", brief(target));
            return null;
        }
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
            inlined.unresolved++;
            log.warn("包内图片为空或过大（上限 {}MB），原样保留: {}",
                    MAX_IMAGE_BYTES / 1024 / 1024, brief(target));
            return null;
        }
        return bytes;
    }

    private byte[] httpFetch(String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return new RestTemplate(factory).getForObject(url, byte[].class);
    }

    private static String extOf(String name) {
        if (name == null) {
            return "";
        }
        String value = name;
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        int dot = value.lastIndexOf('.');
        return dot < 0 ? "" : value.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 日志里的引用目标：只留头部，data URI 的 base64 体不许刷进日志 */
    private static String brief(String target) {
        return target.length() <= 80 ? target : target.substring(0, 80) + "…";
    }

    /** 连带包读出的入口正文与可配对图片（{@code entryName} 非空＝来自 zip） */
    private static final class Bundle {

        private String markdown;

        private String entryName;

        private Map<String, byte[]> images = new HashMap<>();
    }

    /** 内联化中间结果（{@code refs}＝引用目标 → 占位，交给 rewriteImages 逐字替换） */
    private static final class Inlined {

        private String markdown;

        private final Map<String, String> refs = new HashMap<>();

        private int stored;

        private int kept;

        private int unresolved;
    }

    /** 一条引用解析出的落库材料（字节＋命名依据＋扩展名） */
    private static final class Resolved {

        private final byte[] bytes;

        /** 取对象名的依据（包内路径或外链 URL；data URI 没有名字，传 {@code inline}） */
        private final String name;

        private final String ext;

        private Resolved(byte[] bytes, String name, String ext) {
            this.bytes = bytes;
            this.name = name;
            this.ext = ext;
        }
    }

    /** 外链取字节口（真形态是 HTTP GET，单测给假件） */
    interface RemoteFetcher {

        byte[] fetch(String url);
    }

    /** 直通结果（供流水线落观测快照：图源、张数、没配上的引用数） */
    @Getter
    public static final class Result {

        /** 观测快照工厂（流水线与单测共用一条构造路径，字段语义见各字段注释） */
        public static Result of(String source, String entryName, String markdownKey, String markdown,
                                int storedImages, int keptPlaceholders, int unresolvedRefs) {
            Result result = new Result();
            result.source = source;
            result.entryName = entryName;
            result.markdownKey = markdownKey;
            result.markdown = markdown;
            result.storedImages = storedImages;
            result.keptPlaceholders = keptPlaceholders;
            result.unresolvedRefs = unresolvedRefs;
            return result;
        }

        /** {@link #SOURCE_TEXT}/{@link #SOURCE_BUNDLE} */
        private String source;

        /** zip 里选中的入口条目名（裸文本为 null） */
        private String entryName;

        private String markdownKey;

        /** 改写后的入库正文（调用方不再使用也可留作观测，长度即字数） */
        private String markdown;

        /** 本次落库的图片数 */
        private int storedImages;

        /** 正文里原本就是占位、直接沿用的引用数 */
        private int keptPlaceholders;

        /** 没弄到字节、原样留在正文里的引用数（坏链可见，不静默删图） */
        private int unresolvedRefs;
    }
}
