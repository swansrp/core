package com.bidr.llm.parse;

import com.bidr.llm.parse.converter.DocxMarkdownConverter;
import com.bidr.llm.parse.converter.ExcelMarkdownConverter;
import com.bidr.llm.parse.converter.HtmlMarkdownConverter;
import com.bidr.llm.parse.converter.PptxMarkdownConverter;
import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.extractor.PowerPointExtractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * 文件 → Markdown 转换服务（llm 模块文档解析统一入口）。
 * <p>
 * 外部可传入 HTTP URL、本地文件、文件路径或 InputStream，返回文件内容的 Markdown 文本，
 * 供后续直接拼入 LLM 提示词或进 RAG 切分。格式分发：
 * <ul>
 * <li>md/txt/csv/json/xml/sql 等文本：按 UTF-8 严格解码，失败回落 GBK；</li>
 * <li>html/htm：Jsoup 结构转 Markdown；</li>
 * <li>docx/doc、xlsx/xls、pptx/ppt：POI 解析，标题/列表/表格保留 Markdown 结构；</li>
 * <li>pdf：PDFBox 逐页提取文本，文本量极少的页判定为扫描页，渲染为图片交多模态模型转录；</li>
 * <li>png/jpg/jpeg/gif/webp/bmp：直接交多模态模型转录。</li>
 * </ul>
 * 多模态模型配置解析顺序：调用时显式传入的 {@link VisionModelConfig} →
 * {@link ModelConfigProvider}（purpose = {@link #PURPOSE_VISION}，数据库 sys_config 优先），
 * 与现有 LLM 调用同一套配置机制。
 * </p>
 *
 * @author Sharp
 */
@Slf4j
public class FileMarkdownService {

    /**
     * 多模态模型用途标识：自定义 ModelConfigProvider 按此 purpose 返回视觉模型配置
     */
    public static final String PURPOSE_VISION = "VISION";

    /**
     * 单文件大小上限（100MB），防御超大文件与压缩包解包类资源消耗
     */
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    /**
     * 单次解析送多模态模型的扫描页上限，防止长扫描件调用成本失控
     */
    private static final int MAX_VISION_PAGES = 50;

    /**
     * 扫描页渲染 DPI：150 兼顾识别精度与图片体积
     */
    private static final float RENDER_DPI = 150f;

    /**
     * 单页可提取文本低于该字数即判定为扫描页（正常文字页远不止此量）
     */
    private static final int MIN_TEXT_LENGTH_PER_PAGE = 20;

    /**
     * 多模态转录提示词：约束输出为 Markdown 且不增删内容
     */
    private static final String VISION_PROMPT = "请把图片中的内容完整转录为 Markdown 格式："
            + "保留标题层级与列表结构，表格使用 Markdown 表格语法输出；"
            + "不要遗漏图片中的文字，也不要添加图片中不存在的内容；直接输出 Markdown 正文。";

    /**
     * 模型配置提供者，可为 null（为 null 时多模态配置只能调用时显式传入）
     */
    private final ModelConfigProvider configProvider;

    public FileMarkdownService(ModelConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    // ---------------- 对外入口：url / File / Path / InputStream ----------------

    /**
     * 按地址解析：http(s) 开头视为远程 URL 下载，否则视为本地文件路径
     */
    public String toMarkdown(String urlOrPath) throws IOException {
        return toMarkdown(urlOrPath, null);
    }

    public String toMarkdown(String urlOrPath, VisionModelConfig vision) throws IOException {
        if (urlOrPath == null || urlOrPath.trim().isEmpty()) {
            throw new IllegalArgumentException("文件地址不能为空");
        }
        String location = urlOrPath.trim();
        if (location.startsWith("http://") || location.startsWith("https://")) {
            byte[] bytes = download(location);
            return parseBytes(bytes, filenameFromUrl(location), vision);
        }
        return toMarkdown(new File(location), vision);
    }

    public String toMarkdown(File file) throws IOException {
        return toMarkdown(file, null);
    }

    public String toMarkdown(File file, VisionModelConfig vision) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在：" + (file == null ? "null" : file.getPath()));
        }
        if (file.length() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过大小限制 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }
        try (InputStream in = new FileInputStream(file)) {
            return toMarkdown(in, file.getName(), vision);
        }
    }

    public String toMarkdown(Path path) throws IOException {
        return toMarkdown(path, null);
    }

    public String toMarkdown(Path path, VisionModelConfig vision) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("文件不存在：" + path);
        }
        return toMarkdown(path.toFile(), vision);
    }

    public String toMarkdown(InputStream in, String filename) throws IOException {
        return toMarkdown(in, filename, null);
    }

    /**
     * 流式入口：MultipartFile 场景传 {@code file.getInputStream()} + 原始文件名即可
     */
    public String toMarkdown(InputStream in, String filename, VisionModelConfig vision) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("输入流不能为空");
        }
        return parseBytes(readAllCapped(in), filename, vision);
    }

    // ---------------- 格式分发 ----------------

    private String parseBytes(byte[] bytes, String filename, VisionModelConfig vision) throws IOException {
        String ext = extensionOf(filename);
        switch (ext) {
            case "md":
            case "markdown":
            case "txt":
            case "log":
            case "csv":
            case "tsv":
            case "json":
            case "xml":
            case "yml":
            case "yaml":
            case "properties":
            case "sql":
                return decodeText(bytes);
            case "html":
            case "htm":
                return HtmlMarkdownConverter.convert(decodeText(bytes));
            case "docx":
                return DocxMarkdownConverter.convert(bytes);
            case "doc":
                return extractLegacyDoc(bytes);
            case "xlsx":
            case "xls":
                return ExcelMarkdownConverter.convert(bytes);
            case "pptx":
                return PptxMarkdownConverter.convert(bytes);
            case "ppt":
                return extractLegacyPpt(bytes);
            case "pdf":
                return convertPdf(bytes, vision);
            case "png":
            case "jpg":
            case "jpeg":
            case "gif":
            case "webp":
            case "bmp":
                return visionTranscribe(bytes, mimeTypeOf(ext), vision);
            default:
                return parseUnknown(bytes, filename);
        }
    }

    /**
     * 未知扩展名：按二进制特征判断，非二进制尝试当文本解码，二进制直接拒绝
     */
    private String parseUnknown(byte[] bytes, String filename) {
        if (looksBinary(bytes)) {
            throw new IllegalArgumentException("暂不支持的文件类型：" + filename);
        }
        return decodeText(bytes);
    }

    // ---------------- PDF：文本页直提 + 扫描页走多模态 ----------------

    private String convertPdf(byte[] bytes, VisionModelConfig vision) throws IOException {
        try (PDDocument document = PDDocument.load(bytes)) {
            int total = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder sb = new StringBuilder();
            int visionPages = 0;
            for (int page = 1; page <= total; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (text.length() >= MIN_TEXT_LENGTH_PER_PAGE) {
                    sb.append(text).append("\n\n");
                    continue;
                }
                // 扫描页：渲染为图片交多模态模型转录
                if (visionPages >= MAX_VISION_PAGES) {
                    sb.append("> 扫描页数量超过上限 ").append(MAX_VISION_PAGES).append(" 页，其余页面已跳过\n\n");
                    break;
                }
                visionPages++;
                BufferedImage image = renderer.renderImageWithDPI(page - 1, RENDER_DPI);
                sb.append(visionTranscribe(toPngBytes(image), "image/png", vision)).append("\n\n");
            }
            return sb.toString().trim();
        }
    }

    // ---------------- 旧格式 Office（doc/ppt）文本提取 ----------------

    private String extractLegacyDoc(byte[] bytes) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bytes));
             WordExtractor extractor = new WordExtractor(fs)) {
            return extractor.getText().trim();
        }
    }

    private String extractLegacyPpt(byte[] bytes) throws IOException {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bytes));
             PowerPointExtractor extractor = new PowerPointExtractor(fs)) {
            return extractor.getText().trim();
        }
    }

    // ---------------- 多模态转录 ----------------

    /**
     * 图片交多模态模型转录为 Markdown。配置解析：显式传入 → ModelConfigProvider（数据库优先），
     * 均不可用时抛出带操作指引的异常
     */
    private String visionTranscribe(byte[] imageBytes, String mimeType, VisionModelConfig explicit) {
        VisionModelConfig config = resolveVisionConfig(explicit);
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 180L))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .build();
        UserMessage message = UserMessage.from(
                TextContent.from(VISION_PROMPT),
                ImageContent.from(Base64.getEncoder().encodeToString(imageBytes), mimeType));
        log.info("多模态模型转录扫描内容：model={}, imageSize={}KB", config.getModelName(), imageBytes.length / 1024);
        Response<AiMessage> response = model.generate(message);
        return response.content().text();
    }

    /**
     * 解析多模态模型配置：显式传入优先；否则按 PURPOSE_VISION 从 ModelConfigProvider 取
     * （DbAware 实现为数据库 sys_config 优先、yaml 回落、再回落默认模型配置）
     */
    private VisionModelConfig resolveVisionConfig(VisionModelConfig explicit) {
        if (explicit != null && explicit.isUsable()) {
            return explicit;
        }
        if (configProvider != null) {
            String baseUrl = configProvider.getBaseUrl(PURPOSE_VISION);
            String modelName = configProvider.getModelName(PURPOSE_VISION);
            if (hasText(baseUrl) && hasText(modelName)) {
                return VisionModelConfig.builder()
                        .baseUrl(baseUrl)
                        .apiKey(configProvider.getApiKey(PURPOSE_VISION, null))
                        .modelName(modelName)
                        .timeoutSeconds(configProvider.getTimeoutSeconds(PURPOSE_VISION))
                        .build();
            }
        }
        throw new IllegalStateException("扫描件/图片解析需要多模态（视觉）模型：请调用时传入 VisionModelConfig，"
                + "或在系统参数管理页配置「多模态模型服务地址/多模态模型」，或配置应用 llm.vision.* 项");
    }

    // ---------------- 工具方法 ----------------

    /**
     * http(s) 远程文件下载，带连接/读取超时与大小上限
     */
    private byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        try (InputStream in = connection.getInputStream()) {
            return readAllCapped(in);
        } finally {
            connection.disconnect();
        }
    }

    private byte[] readAllCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件超过大小限制 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * 文本解码：UTF-8 严格解码，失败回落 GBK；剥离 UTF-8 BOM
     */
    private String decodeText(byte[] bytes) {
        String text;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        return text;
    }

    /**
     * 二进制特征判断：前 8KB 内出现 NUL 字节即视为二进制
     */
    private boolean looksBinary(byte[] bytes) {
        int limit = Math.min(bytes.length, 8192);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String filenameFromUrl(String url) {
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : "download.bin";
    }

    private String mimeTypeOf(String ext) {
        switch (ext) {
            case "png":
                return "image/png";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "gif":
                return "image/gif";
            case "webp":
                return "image/webp";
            case "bmp":
                return "image/bmp";
            default:
                return "application/octet-stream";
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
