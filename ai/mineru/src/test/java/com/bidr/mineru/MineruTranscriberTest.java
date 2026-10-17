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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: MineruTranscriberTest
 * Description: 同步门面的腿选择与预算判据（腿选择口径＝2026-10-17 用户定案）：
 * 云优先→失败回落本地；没云走本地；两腿全空＝不认领；超时预算内终局失败立即抛、
 * 永跑任务吃满预算抛超时。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class MineruTranscriberTest {

    private HttpServer cloudServer;

    private HttpServer localServer;

    private final AtomicInteger cloudSubmitCount = new AtomicInteger();

    private final AtomicInteger localSubmitCount = new AtomicInteger();

    /** 云腿状态：done 或 failed（由各用例切换） */
    private volatile String cloudState = "done";

    /** 本地腿状态：completed / failed / running（超时腿用） */
    private volatile String localState = "completed";

    private MineruProperties properties;

    @Before
    public void setUp() throws IOException {
        cloudServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cloudServer.createContext("/", this::handleCloud);
        cloudServer.start();
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        localServer.createContext("/", this::handleLocal);
        localServer.start();

        properties = new MineruProperties();
        properties.getCloud().setBaseUrl(MineruLocalClientTest.basePath(cloudServer));
        properties.getCloud().setToken("tok");
        properties.getLocal().setBaseUrl(MineruLocalClientTest.basePath(localServer));
        properties.setPollIntervalMs(10);
        properties.setMaxWaitSeconds(3);
    }

    @After
    public void tearDown() {
        cloudServer.stop(0);
        localServer.stop(0);
    }

    @Test
    public void 云优先_失败回落本地() throws IOException {
        cloudState = "failed";
        MineruTranscriber transcriber = new MineruTranscriber(properties,
                new MineruLocalClient(properties), new MineruCloudClient(properties));
        Assert.assertEquals("云腿失败必须回落本地腿", "# 本地腿正文",
                transcriber.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf"));
        Assert.assertEquals("云腿必须先被尝试过", 1, cloudSubmitCount.get());
        Assert.assertEquals("云失败后本地腿接手", 1, localSubmitCount.get());
    }

    @Test
    public void 没云腿只走本地() throws IOException {
        MineruProperties localOnly = cloneFor(properties);
        localOnly.getCloud().setToken("");
        MineruTranscriber transcriber = new MineruTranscriber(localOnly, new MineruLocalClient(localOnly), null);
        Assert.assertEquals("# 本地腿正文",
                transcriber.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.png"));
        Assert.assertEquals(0, cloudSubmitCount.get());
        Assert.assertEquals(1, localSubmitCount.get());
    }

    @Test
    public void 只有云腿且云失败_如实抛() {
        cloudState = "failed";
        MineruProperties cloudOnly = cloneFor(properties);
        cloudOnly.getLocal().setBaseUrl("");
        MineruTranscriber transcriber = new MineruTranscriber(cloudOnly, null, new MineruCloudClient(cloudOnly));
        try {
            transcriber.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf");
            Assert.fail("无本地腿时云失败必须抛（门面回落 VLM）");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("云腿解析失败") || e.getMessage().contains("云"));
        }
    }

    @Test
    public void 本地永跑吃满预算报超时() {
        localState = "running";
        properties.setMaxWaitSeconds(1);
        properties.setPollIntervalMs(20);
        MineruTranscriber transcriber = new MineruTranscriber(properties,
                new MineruLocalClient(properties), null);
        long start = System.currentTimeMillis();
        try {
            transcriber.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf");
            Assert.fail("永跑任务必须吃满预算抛超时");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage().contains("超时"));
            Assert.assertTrue("等待时长须贴着预算（1s 档）", System.currentTimeMillis() - start >= 900);
        }
    }

    @Test
    public void 认领面按类型与腿就绪度() {
        MineruTranscriber both = new MineruTranscriber(properties,
                new MineruLocalClient(properties), new MineruCloudClient(properties));
        Assert.assertTrue("pdf 认领", both.isAvailable("a.pdf"));
        Assert.assertTrue("png 认领", both.isAvailable("b.png"));
        Assert.assertFalse("office 不认领（走门面 POI 路径）", both.isAvailable("a.docx"));
        Assert.assertFalse("文本不认领", both.isAvailable("a.txt"));

        MineruTranscriber noLegs = new MineruTranscriber(properties, null, null);
        Assert.assertFalse("两腿全空＝不认领", noLegs.isAvailable("a.pdf"));

        MineruProperties disabled = cloneFor(properties);
        disabled.setEnabled(false);
        MineruTranscriber off = new MineruTranscriber(disabled,
                new MineruLocalClient(disabled), new MineruCloudClient(disabled));
        Assert.assertFalse("总开关关＝不认领", off.isAvailable("a.pdf"));
    }

    // ---------------- 伪端点 ----------------

    private void handleCloud(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/file-urls/batch")) {
            cloudSubmitCount.incrementAndGet();
            MineruLocalClientTest.respond(exchange, 200, "{\"code\":0,\"data\":{\"batch_id\":\"b1\","
                    + "\"file_urls\":[\"" + MineruLocalClientTest.basePath(cloudServer) + "/up\"]}}");
            return;
        }
        if ("/up".equals(path)) {
            MineruLocalClientTest.respond(exchange, 200, "");
            return;
        }
        if (path.contains("/extract-results/batch")) {
            if ("failed".equals(cloudState)) {
                MineruLocalClientTest.respond(exchange, 200,
                        "{\"code\":0,\"data\":{\"extract_result\":[{\"state\":\"failed\",\"err_msg\":\"cloud-boom\"}]}}");
                return;
            }
            MineruLocalClientTest.respond(exchange, 200, "{\"code\":0,\"data\":{\"extract_result\":[{"
                    + "\"state\":\"done\",\"full_zip_url\":\"" + MineruLocalClientTest.basePath(cloudServer)
                    + "/z.zip\"}]}}");
            return;
        }
        MineruLocalClientTest.respond(exchange, 200, MineruLocalClientTest.zipOf("# 云腿正文", "[]", null));
    }

    private void handleLocal(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/tasks".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            localSubmitCount.incrementAndGet();
            MineruLocalClientTest.respond(exchange, 200, "{\"task_id\":\"t1\"}");
            return;
        }
        if (path.endsWith("/result")) {
            MineruLocalClientTest.respond(exchange, 200,
                    MineruLocalClientTest.zipOf("# 本地腿正文", "[]", null));
            return;
        }
        MineruLocalClientTest.respond(exchange, 200, "{\"status\":\"" + localState + "\"}");
    }

    private static MineruProperties cloneFor(MineruProperties source) {
        MineruProperties copy = new MineruProperties();
        copy.getCloud().setBaseUrl(source.getCloud().getBaseUrl());
        copy.getCloud().setToken(source.getCloud().getToken());
        copy.getLocal().setBaseUrl(source.getLocal().getBaseUrl());
        copy.setPollIntervalMs(source.getPollIntervalMs());
        copy.setMaxWaitSeconds(source.getMaxWaitSeconds());
        return copy;
    }
}
