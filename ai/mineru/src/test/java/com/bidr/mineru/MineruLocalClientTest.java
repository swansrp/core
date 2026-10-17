package com.bidr.mineru;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Title: MineruLocalClientTest
 * Description: 自建腿协议离线单测（本地 HttpServer 伪端点）。判据全部来自实测约束：
 * multipart 字段名/开关全量核对、状态词表归一、zip 产物归一、缺 md 判失败。
 * CI 可跑、零外部依赖。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class MineruLocalClientTest {

    private HttpServer server;

    private final AtomicReference<String> submitBody = new AtomicReference<>();

    private MineruProperties properties;

    @Before
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tasks", this::handleTasks);
        server.start();
        properties = new MineruProperties();
        properties.getLocal().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setPollIntervalMs(10);
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void 提交表单的实测约束全量在场() {
        MineruLocalClient client = new MineruLocalClient(properties);
        String taskId = client.submit("pdf-bytes".getBytes(StandardCharsets.UTF_8), "a.pdf", null, false);
        Assert.assertEquals("t1", taskId);
        String body = submitBody.get();
        Assert.assertNotNull("伪端点没收到提交", body);
        // 字段名 files（复数）与带 filename 的文件部件
        Assert.assertTrue("multipart 文件部件字段名必须是 files 复数", body.contains("name=\"files\""));
        Assert.assertTrue("文件部件须带 filename（MinerU 据扩展名识别类型）", body.contains("filename=\"a.pdf\""));
        // 必须显式打开的三个开关（默认均 false，缺一个拿不到结果）
        Assert.assertTrue("return_md 必须显式开", body.contains("name=\"return_md\"") && body.contains("true"));
        Assert.assertTrue("return_content_list 必须显式开", body.contains("name=\"return_content_list\""));
        Assert.assertTrue("response_format_zip 必须显式开", body.contains("name=\"response_format_zip\""));
        // 转写场景关图片、其余缺省
        Assert.assertTrue("return_images 应为 false", body.contains("name=\"return_images\"") && body.contains("false"));
        Assert.assertTrue("lang_list 缺省 ch", body.contains("name=\"lang_list\"") && body.contains("ch"));
        Assert.assertTrue("backend 缺省 hybrid-auto-engine", body.contains("name=\"backend\"")
                && body.contains("hybrid-auto-engine"));
        Assert.assertTrue("parse_method=auto", body.contains("name=\"parse_method\"") && body.contains("auto"));
    }

    @Test
    public void 轮询状态词表归一与排队数() {
        MineruLocalClient client = new MineruLocalClient(properties);
        Assert.assertEquals(MineruTaskStatus.COMPLETED, client.poll("t1").getStatus());
        Assert.assertNull("completed 无错误信息", client.poll("t1").getErrorMessage());

        MineruTaskStatus failed = client.poll("t2");
        Assert.assertEquals(MineruTaskStatus.FAILED, failed.getStatus());
        Assert.assertEquals("boom", failed.getErrorMessage());

        MineruTaskStatus pending = client.poll("t3");
        Assert.assertEquals(MineruTaskStatus.PENDING, pending.getStatus());
        Assert.assertEquals(Integer.valueOf(2), pending.getQueueAhead());
        Assert.assertEquals("hybrid-auto-engine", pending.getPhase());
    }

    @Test
    public void 取结果并归一产物() {
        MineruLocalClient client = new MineruLocalClient(properties);
        MineruArtifact artifact = client.fetch("t1");
        Assert.assertEquals("# 标题\n\n正文", artifact.getMarkdown());
        Assert.assertNotNull("content_list 应在场（提交侧显式开了）", artifact.getContentList());
        Assert.assertTrue(artifact.getContentList().contains("\"type\""));
        Assert.assertEquals(1, artifact.getImages().size());
        Assert.assertEquals("images/x.jpg", artifact.getImages().get(0).getName());
        Assert.assertEquals(3, artifact.getImages().get(0).getData().length);
    }

    @Test
    public void 缺markdown的zip判失败() {
        MineruLocalClient client = new MineruLocalClient(properties);
        try {
            client.fetch("t4");
            Assert.fail("缺 md 必须抛");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("缺 markdown"));
        }
    }

    // ---------------- 伪端点 ----------------

    private void handleTasks(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/tasks".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            submitBody.set(new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"task_id\":\"t1\"}");
            return;
        }
        if (path.matches("/tasks/t1") || path.matches("/tasks/t1/result")) {
            if (path.endsWith("/result")) {
                respond(exchange, 200, zipOf("# 标题\n\n正文",
                        "[{\"type\":\"text\",\"text\":\"正文\",\"page_idx\":0,\"bbox\":[1,2,3,4]}]",
                        "img".getBytes(StandardCharsets.UTF_8)));
            } else {
                respond(exchange, 200, "{\"status\":\"completed\",\"backend\":\"hybrid-auto-engine\"}");
            }
            return;
        }
        if ("/tasks/t2".equals(path)) {
            respond(exchange, 200, "{\"status\":\"failed\",\"error\":\"boom\"}");
            return;
        }
        if ("/tasks/t3".equals(path)) {
            respond(exchange, 200, "{\"status\":\"pending\",\"backend\":\"hybrid-auto-engine\",\"queued_ahead\":2}");
            return;
        }
        if ("/tasks/t4/result".equals(path)) {
            // 只回 content_list，缺 md ⇒ 解码必须判失败
            respond(exchange, 200, zipOf(null, "[]", null));
            return;
        }
        respond(exchange, 404, "{}");
    }

    static byte[] zipOf(String markdown, String contentList, byte[] image) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            if (markdown != null) {
                zos.putNextEntry(new ZipEntry("full.md"));
                zos.write(markdown.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            if (contentList != null) {
                zos.putNextEntry(new ZipEntry("t1_content_list.json"));
                zos.write(contentList.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("t1_content_list_v2.json"));
                zos.write("[]".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            if (image != null) {
                zos.putNextEntry(new ZipEntry("extra/images/x.jpg"));
                zos.write(image);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        return out.toByteArray();
    }

    static void respond(HttpExchange exchange, int code, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static void respond(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static String basePath(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
