package com.bidr.gotenberg;

import com.bidr.document.DocumentConvertException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * gotenberg convert 腿单测（离线，本地 HttpServer 假装 gotenberg）——形状自 InkHub
 * {@code OfficeConvertClientTest} 迁入（失败分诊口径一字不动）。
 *
 * <p>要钉住的是<b>失败分诊</b>：转换层面的失败（4xx/未配置/产出不是 PDF）必须
 * {@code retryable=false} 让门面兜底按原件解析；服务不可达/5xx 必须 {@code retryable=true}
 * 走重试预算。分诊反了，要么「暂时没页」固化成「永远没页」，要么一份坏文档把重试预算烧光。</p>
 *
 * @author Sharp
 * @since 2026/10/20
 */
class GotenbergConvertClientTest {

    private HttpServer server;

    private final GotenbergProperties properties = new GotenbergProperties();

    private GotenbergConvertClient client;

    /** 请求观测：路径 / Content-Type / 请求体（multipart 原文） */
    private final AtomicReference<String> path = new AtomicReference<>();

    private final AtomicReference<String> contentType = new AtomicReference<>();

    private final AtomicReference<byte[]> requestBody = new AtomicReference<>();

    /** 可应答的响应（默认 200 + 一份最小 PDF），各用例按需覆盖 */
    private int responseCode = 200;

    private byte[] responseBody = "%PDF-1.7 fake".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forms/libreoffice/convert", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(readAll(exchange.getRequestBody()));
            respond(exchange, responseCode, responseBody);
        });
        server.start();
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new GotenbergConvertClient(properties);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    // ==================== 认领面（腿自报） ====================

    @Test
    void convertibleExtensionMatrix() {
        assertTrue(client.isConvertible("报告.docx"));
        assertTrue(client.isConvertible("报告.DOCX"), "扩展名大小写不敏感");
        assertTrue(client.isConvertible("a.doc"));
        assertTrue(client.isConvertible("台账.xlsx"));
        assertTrue(client.isConvertible("a.xls"));
        assertTrue(client.isConvertible("汇报.ppt"));
        assertTrue(client.isConvertible("汇报.pptx"));
        assertFalse(client.isConvertible("已 Doc 是 PDF.pdf"), "pdf 本身有页，不需要转换");
        assertFalse(client.isConvertible("notes.md"));
        assertFalse(client.isConvertible("图片.png"));
        assertFalse(client.isConvertible("无扩展名"));
        assertFalse(client.isConvertible(null));
        assertEquals("gotenberg", client.legName(), "腿名＝路线留痕里的 convert 腿标识");
    }

    // ==================== 成功路径与请求形态 ====================

    @Test
    void convertPostsMultipartToLibreOfficeEndpointAndReturnsPdf() {
        byte[] docx = "PK\u0003\u0004 假装是 docx 字节".getBytes(StandardCharsets.UTF_8);

        byte[] pdf = client.convertToPdf("会议纪要.docx", docx);

        assertEquals("%PDF-1.7 fake", new String(pdf, StandardCharsets.UTF_8));
        assertEquals("/forms/libreoffice/convert", path.get());
        assertTrue(String.valueOf(contentType.get()).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE),
                "必须显式 multipart（坑 14 同族：内容类型不靠隐式协商）");
        String body = new String(requestBody.get(), StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("filename=\"source.docx\""),
                "上传名固定 source.<ext>（LibreOffice 只看扩展名，规避中文名编码歧义）: " + body);
        assertTrue(body.contains(new String(docx, StandardCharsets.ISO_8859_1)), "原件字节必须完整上传");
    }

    // ==================== 失败分诊（两半不许合并） ====================

    @Test
    void conversionRejectedIsNotRetryable() {
        responseCode = 400;
        responseBody = "could not convert".getBytes(StandardCharsets.UTF_8);

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> client.convertToPdf("损坏.docx", "垃圾字节".getBytes(StandardCharsets.UTF_8)));

        assertFalse(e.isRetryable(), "文档转不动重跑结果不变，必须归为兜底类: " + e.getMessage());
    }

    @Test
    void serverErrorIsRetryable() {
        responseCode = 500;
        responseBody = "boom".getBytes(StandardCharsets.UTF_8);

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> client.convertToPdf("报告.docx", "字节".getBytes(StandardCharsets.UTF_8)));

        assertTrue(e.isRetryable(), "5xx 是临时故障，必须走重试而不是静默兜底: " + e.getMessage());
    }

    @Test
    void serviceUnreachableIsRetryable() {
        properties.setBaseUrl("http://127.0.0.1:1");
        GotenbergConvertClient unreachable = new GotenbergConvertClient(properties);

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> unreachable.convertToPdf("报告.docx", "字节".getBytes(StandardCharsets.UTF_8)));

        assertTrue(e.isRetryable(), "服务不可达是临时故障: " + e.getMessage());
    }

    @Test
    void unconfiguredServiceIsNotRetryable() {
        properties.setBaseUrl("");
        GotenbergConvertClient unconfigured = new GotenbergConvertClient(properties);

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> unconfigured.convertToPdf("报告.docx", "字节".getBytes(StandardCharsets.UTF_8)));

        assertFalse(e.isRetryable(), "未配置转换服务是稳定状态，重试无意义");
        assertTrue(e.getMessage().contains("tool.gotenberg.base-url"), e.getMessage());
    }

    @Test
    void nonPdfResponseIsNotRetryable() {
        responseBody = "<html>不是 PDF</html>".getBytes(StandardCharsets.UTF_8);

        DocumentConvertException e = assertThrows(DocumentConvertException.class,
                () -> client.convertToPdf("报告.docx", "字节".getBytes(StandardCharsets.UTF_8)));

        assertFalse(e.isRetryable(), "产出不是 PDF 重跑也还是不是，按兜底处理: " + e.getMessage());
    }

    // ==================== 装配态（留空＝不装配） ====================

    @Test
    void blankBaseUrlMeansNoAssembly() {
        try (org.springframework.context.annotation.AnnotationConfigApplicationContext blank =
                     runAutoConfigWithBaseUrl("")) {
            org.junit.jupiter.api.Assertions.assertFalse(blank.containsBean("gotenbergConvertStep"),
                    "base-url 留空＝不装配（office 原件直送）");
        }
        try (org.springframework.context.annotation.AnnotationConfigApplicationContext configured =
                     runAutoConfigWithBaseUrl("http://127.0.0.1:13000")) {
            org.junit.jupiter.api.Assertions.assertTrue(configured.containsBean("gotenbergConvertStep"),
                    "base-url 非空＝装配 convert 腿");
        }
    }

    private org.springframework.context.annotation.AnnotationConfigApplicationContext runAutoConfigWithBaseUrl(
            String baseUrl) {
        org.springframework.context.annotation.AnnotationConfigApplicationContext ctx =
                new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        java.util.Map<String, Object> props = new java.util.HashMap<>();
        props.put("tool.gotenberg.base-url", baseUrl);
        ctx.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.MapPropertySource("test", props));
        ctx.register(GotenbergAutoConfiguration.class);
        ctx.refresh();
        return ctx;
    }

    // ==================== 夹具 ====================

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static void respond(HttpExchange exchange, int code, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/pdf");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
