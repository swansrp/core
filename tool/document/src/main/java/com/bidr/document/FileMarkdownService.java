package com.bidr.document;

import com.bidr.llm.provider.ModelConfigProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件 → Markdown 转换服务（文档加工统一入口，零装配可用）。
 * <p>
 * 外部可传入 HTTP URL、本地文件、文件路径或 InputStream，返回文件内容的 Markdown 文本，
 * 供后续直接拼入 LLM 提示词或进 RAG 切分。加工本体走 {@link DocumentProcessor}
 * （格式分发、文本直读/POI/PDFBox/多模态内置腿、硬参数 {@code tool.document.*}），本类只保留：
 * 入口重载、远程下载、显式视觉配置到 {@link VisionEndpoint} 的转换。
 * <p>
 * 多模态模型配置解析顺序：调用时显式传入的 {@link VisionModelConfig} →
 * {@link VisionConfigProvider}（默认适配器＝llm 的 {@link ModelConfigProvider}，
 * purpose = {@link #PURPOSE_VISION}，数据库 sys_config 优先、{@code llm.vision.*} 回落）。
 * <p>
 * 增强转写腿（如 {@link com.bidr.document.builtin.MineruTranscribeLeg}）由装配按
 * {@code tool.document.transcriber} 选型挂进 {@link DocumentProcessor}，挂不挂对本类调用方不可见；
 * 未装配门面时（业务自建 / 纯离线 new）以 {@link DocumentProcessor#defaults} 纯内置档兜底。
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
     * 模型配置提供者，可为 null（为 null 时多模态配置只能调用时显式传入）
     */
    private final ModelConfigProvider configProvider;

    /**
     * 多模态调用 HTTP 代理：复用 core {@code llm.proxy.*} 口径（与文本模型同一份配置），
     * 为 null 表示直连
     */
    private final Proxy proxy;

    /**
     * 加工门面：内置腿集＋选型后的增强腿＋硬参数都在那边
     */
    private final DocumentProcessor processor;

    public FileMarkdownService(ModelConfigProvider configProvider) {
        this(configProvider, null, null);
    }

    public FileMarkdownService(ModelConfigProvider configProvider, Proxy proxy) {
        this(configProvider, proxy, null);
    }

    /**
     * 委托构造（自动装配走这档）：显式传入 {@link DocumentProcessor}
     * （缺席传 null＝纯内置档默认门面，视觉配置经 {@link LlmVisionConfigProvider} 适配 llm 模型机制）
     */
    public FileMarkdownService(ModelConfigProvider configProvider, Proxy proxy, DocumentProcessor processor) {
        this.configProvider = configProvider;
        this.proxy = proxy;
        this.processor = processor != null ? processor
                : DocumentProcessor.defaults(new LlmVisionConfigProvider(configProvider, proxy));
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
        long maxFileSize = processor.getProperties().getMaxFileSizeBytes();
        if (file.length() > maxFileSize) {
            throw new IllegalArgumentException("文件超过大小限制 " + (maxFileSize / 1024 / 1024) + "MB");
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
        return parseBytes(processor.readCapped(in), filename, vision);
    }

    /**
     * 有没有腿认领该文件（业务侧「能不能入库」判据用，与 {@link #toMarkdown} 同一套路由）
     */
    public boolean isProcessable(String filename) {
        return processor.isProcessable(filename);
    }

    // ---------------- 工具方法 ----------------

    private String parseBytes(byte[] bytes, String filename, VisionModelConfig vision) throws IOException {
        return processor.process(bytes, filename, toEndpoint(vision)).getMarkdown();
    }

    /**
     * http(s) 远程文件下载，带连接/读取超时与大小上限
     */
    private byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(60_000);
        connection.setInstanceFollowRedirects(true);
        try (InputStream in = connection.getInputStream()) {
            return processor.readCapped(in);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 显式视觉配置 → 门面端点形状（代理随本类的 {@code llm.proxy.*} 口径注入）
     */
    private VisionEndpoint toEndpoint(VisionModelConfig vision) {
        if (vision == null) {
            return null;
        }
        return VisionEndpoint.builder()
                .baseUrl(vision.getBaseUrl())
                .apiKey(vision.getApiKey())
                .modelName(vision.getModelName())
                .timeoutSeconds(vision.getTimeoutSeconds())
                .proxy(proxy)
                .build();
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
}
