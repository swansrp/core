package com.bidr.mineru;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Title: MineruCloudClientTest
 * Description: 公网腿（mineru.net v4）协议离线单测。三个实测坑各有正反腿：
 * ①PUT 直传<b>一个 Content-Type 都不发</b>（发了＝OSS 403 SignatureDoesNotMatch）；
 * ②结果 zip 直链用 {@code URI.create} 传（字符串重载会当模板展开改写 % 与 {}）；
 * ③业务失败＝HTTP 200＋非 0 code＝终局 FAILED，不空转。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class MineruCloudClientTest {

    private HttpServer server;

    private final AtomicReference<String> batchBody = new AtomicReference<>();

    private final AtomicReference<String> batchContentType = new AtomicReference<>();

    private final AtomicReference<String> uploadContentType = new AtomicReference<>();

    private final AtomicReference<String> uploadPath = new AtomicReference<>();

    private final AtomicReference<byte[]> uploadBytes = new AtomicReference<>();

    private final AtomicReference<String> zipPathRequested = new AtomicReference<>();

    private MineruProperties properties;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = MineruLocalClientTest.basePath(server);
        server.createContext("/api/v4/file-urls/batch", this::handleBatch);
        server.createContext("/upload", this::handleUpload);
        server.createContext("/api/v4/extract-results/batch", this::handleResults);
        server.createContext("/", this::handleZip);
        server.start();

        properties = new MineruProperties();
        properties.getCloud().setBaseUrl(base);
        properties.getCloud().setToken("tok-123");
        properties.setPollIntervalMs(10);
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void 两段式提交的请求面() {
        MineruCloudClient client = new MineruCloudClient(properties);
        String batchId = client.submit("pdf-bytes".getBytes(StandardCharsets.UTF_8), "a.pdf", null);
        Assert.assertEquals("b1", batchId);

        String body = batchBody.get();
        Assert.assertNotNull("伪端点没收到申请直传地址请求", body);
        Assert.assertTrue("申请直传地址必须显式 application/json（实测 text/plain 回 type mismatch）",
                "application/json".equals(batchContentType.get()));
        Assert.assertTrue("模型档必须在场", body.contains("\"model_version\":\"vlm\""));
        Assert.assertTrue("公式开关必须在场", body.contains("\"enable_formula\":true"));
        Assert.assertTrue("表格开关必须在场", body.contains("\"enable_table\":true"));
        Assert.assertTrue("文件名必须在 files[0].name", body.contains("\"name\":\"a.pdf\""));
        Assert.assertTrue("is_ocr 必须在场", body.contains("\"is_ocr\":true"));
        Assert.assertTrue("缺省语言 ch", body.contains("\"language\":\"ch\""));
    }

    @Test
    public void 直传不发ContentType且字节原样到达() {
        MineruCloudClient client = new MineruCloudClient(properties);
        byte[] content = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        client.submit(content, "a.pdf", null);

        Assert.assertNull("🔴 直传必须一个 Content-Type 都不发（预签名按空 content-type 计算，实测带头即 403）",
                uploadContentType.get());
        Assert.assertArrayEquals("原件字节须原样到达", content, uploadBytes.get());
        Assert.assertEquals("/upload", uploadPath.get());
    }

    @Test
    public void 轮询状态词表归一() {
        MineruCloudClient client = new MineruCloudClient(properties);
        Assert.assertEquals(MineruTaskStatus.COMPLETED, client.poll("b1").getStatus());
        Assert.assertEquals(MineruTaskStatus.FAILED, client.poll("b2").getStatus());
        Assert.assertEquals(MineruTaskStatus.PENDING, client.poll("b3").getStatus());
        Assert.assertEquals("waiting-file 归一 PENDING", MineruTaskStatus.PENDING, client.poll("b4").getStatus());
        Assert.assertEquals("converting 归一 RUNNING", MineruTaskStatus.RUNNING, client.poll("b5").getStatus());
    }

    @Test
    public void 业务码非零是终局失败() {
        MineruCloudClient client = new MineruCloudClient(properties);
        MineruTaskStatus status = client.poll("b6");
        Assert.assertEquals(MineruTaskStatus.FAILED, status.getStatus());
        Assert.assertTrue("错误信息须带业务码与 msg（令牌过期 A0211 类）",
                status.getErrorMessage().contains("code=401") && status.getErrorMessage().contains("A0211"));
    }

    @Test
    public void 取结果走URI直链不遭模板展开() {
        MineruCloudClient client = new MineruCloudClient(properties);
        MineruArtifact artifact = client.fetch("b1");
        Assert.assertEquals("# 云腿标题", artifact.getMarkdown());
        // 直链含 % 编码字符：字符串重载的模板展开会改写它，URI.create 必须 1:1 到达
        Assert.assertEquals("zip 直链须原样到达（不遭模板展开）", "/zi%70.zip", zipPathRequested.get());
    }

    @Test
    public void 未配令牌提交即拒() {
        MineruProperties noToken = new MineruProperties();
        noToken.getCloud().setBaseUrl(MineruLocalClientTest.basePath(server));
        MineruCloudClient client = new MineruCloudClient(noToken);
        try {
            client.submit("x".getBytes(StandardCharsets.UTF_8), "a.pdf", null);
            Assert.fail("未配令牌必须拒");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("未配置"));
        }
    }

    // ---------------- 伪端点 ----------------

    private void handleBatch(HttpExchange exchange) throws IOException {
        batchBody.set(new String(MineruLocalClientTest.readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
        batchContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        Assert.assertTrue("申请直传地址必须带 Bearer 令牌", auth != null && auth.startsWith("Bearer "));
        String base = MineruLocalClientTest.basePath(server);
        MineruLocalClientTest.respond(exchange, 200,
                "{\"code\":0,\"data\":{\"batch_id\":\"b1\",\"file_urls\":[\"" + base + "/upload\"]}}");
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        uploadContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
        uploadPath.set(exchange.getRequestURI().getPath());
        uploadBytes.set(MineruLocalClientTest.readAll(exchange.getRequestBody()));
        MineruLocalClientTest.respond(exchange, 200, "");
    }

    private void handleResults(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String base = MineruLocalClientTest.basePath(server);
        String state = "done";
        if (path.endsWith("/b2")) {
            state = "failed";
        } else if (path.endsWith("/b3")) {
            state = "pending";
        } else if (path.endsWith("/b4")) {
            state = "waiting-file";
        } else if (path.endsWith("/b5")) {
            state = "converting";
        } else if (path.endsWith("/b6")) {
            // 业务失败＝HTTP 200＋非 0 code（A0211 令牌过期的真实形态）
            MineruLocalClientTest.respond(exchange, 200,
                    "{\"code\":401,\"msg\":\"A0211 invalid token\"}");
            return;
        }
        MineruLocalClientTest.respond(exchange, 200, "{\"code\":0,\"data\":{\"extract_result\":[{"
                + "\"state\":\"" + state + "\","
                + ("failed".equals(state) ? "\"err_msg\":\"cloud-boom\"," : "")
                + "\"full_zip_url\":\"" + base + "/zi%70.zip\"}]}}");
    }

    private void handleZip(HttpExchange exchange) throws IOException {
        // getRawPath 不解码：%70 必须原样到达＝客户端没做模板展开、也没抢先解码
        zipPathRequested.set(exchange.getRequestURI().getRawPath());
        MineruLocalClientTest.respond(exchange, 200, MineruLocalClientTest.zipOf(
                "# 云腿标题", "[{\"type\":\"text\",\"text\":\"标题\",\"page_idx\":0}]", null));
    }
}
