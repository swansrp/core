package com.bidr.document;

import com.bidr.document.builtin.MineruTranscribeLeg;
import com.bidr.document.spi.TranscribeStep;
import com.bidr.mineru.MineruCloudClient;
import com.bidr.mineru.MineruLocalClient;
import com.bidr.mineru.MineruProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: MineruTranscribeLegTest
 * Description: document 自带的 MinerU 增强腿判据（依赖方向 document → mineru，腿从 mineru 搬进来后
 * 覆盖面一并搬过来）：腿名＝选型键取值、认领面按类型与两腿就绪度、云优先→失败回落本地、
 * 没云只走本地、无本地腿时云失败如实抛（回落决策归门面、腿不吞错）、永跑任务吃满预算抛超时
 * （预算口径＝2026-10-17 定案）；外加装配 gating——{@code tool.document.transcriber=mineru} 才装、
 * 键空或点名别的腿都不装（R-3 腿自 gate）。伪端点用本地 {@link HttpServer}，CI 可跑、零外部依赖。
 *
 * @author Sharp
 * @since 2026/10/21
 */
class MineruTranscribeLegTest {

    private HttpServer cloudServer;

    private HttpServer localServer;

    private final AtomicInteger cloudSubmitCount = new AtomicInteger();

    private final AtomicInteger localSubmitCount = new AtomicInteger();

    /** 云腿状态：done 或 failed（由各用例切换） */
    private volatile String cloudState = "done";

    /** 本地腿状态：completed / failed / running（超时腿用） */
    private volatile String localState = "completed";

    private MineruProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        cloudServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cloudServer.createContext("/", this::handleCloud);
        cloudServer.start();
        localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        localServer.createContext("/", this::handleLocal);
        localServer.start();

        properties = new MineruProperties();
        properties.getCloud().setBaseUrl(basePath(cloudServer));
        properties.getCloud().setToken("tok");
        properties.getLocal().setBaseUrl(basePath(localServer));
        properties.setPollIntervalMs(10);
        properties.setMaxWaitSeconds(3);
    }

    @AfterEach
    void tearDown() {
        cloudServer.stop(0);
        localServer.stop(0);
    }

    // ---------------- 腿语义 ----------------

    @Test
    void 腿名等于选型键取值() {
        assertEquals(MineruTranscribeLeg.LEG_NAME, newLeg(properties).legName());
    }

    @Test
    void 云优先_失败回落本地() throws IOException {
        cloudState = "failed";
        assertEquals("本地腿正文", newLeg(properties).transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf"));
        assertEquals(1, cloudSubmitCount.get(), "云腿必须先被尝试过");
        assertEquals(1, localSubmitCount.get(), "云失败后本地腿接手");
    }

    @Test
    void 没云腿只走本地() throws IOException {
        MineruProperties localOnly = cloneFor(properties);
        localOnly.getCloud().setToken("");
        MineruTranscribeLeg leg = new MineruTranscribeLeg(localOnly, new MineruLocalClient(localOnly), null);
        assertEquals("本地腿正文", leg.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.png"));
        assertEquals(0, cloudSubmitCount.get());
        assertEquals(1, localSubmitCount.get());
    }

    @Test
    void 只有云腿且云失败_如实抛不吞错() {
        cloudState = "failed";
        MineruProperties cloudOnly = cloneFor(properties);
        cloudOnly.getLocal().setBaseUrl("");
        MineruTranscribeLeg leg = new MineruTranscribeLeg(cloudOnly, null, new MineruCloudClient(cloudOnly));
        IOException e = assertThrows(IOException.class,
                () -> leg.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf"));
        assertTrue(e.getMessage().contains("云"), "回落决策归门面，本腿必须把失败原样抛出");
    }

    @Test
    void 两腿都没配_抛配置缺失() {
        MineruTranscribeLeg leg = new MineruTranscribeLeg(new MineruProperties(), null, null);
        IOException e = assertThrows(IOException.class,
                () -> leg.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf"));
        assertTrue(e.getMessage().contains("两腿均未配置"));
    }

    @Test
    void 本地永跑吃满预算报超时() {
        localState = "running";
        properties.setMaxWaitSeconds(1);
        properties.setPollIntervalMs(20);
        MineruTranscribeLeg leg = new MineruTranscribeLeg(properties, new MineruLocalClient(properties), null);
        long start = System.currentTimeMillis();
        IOException e = assertThrows(IOException.class,
                () -> leg.transcribe("x".getBytes(StandardCharsets.UTF_8), "a.pdf"));
        assertTrue(e.getMessage().contains("超时"));
        assertTrue(System.currentTimeMillis() - start >= 900, "等待时长须贴着预算（1s 档）");
    }

    @Test
    void 认领面按类型与腿就绪度() {
        MineruTranscribeLeg both = newLeg(properties);
        assertTrue(both.isAvailable("a.pdf"), "pdf 认领");
        assertTrue(both.isAvailable("b.png"), "图片认领");
        assertFalse(both.isAvailable("a.docx"), "office 不认领（走内置 POI 路径）");
        assertFalse(both.isAvailable("a.txt"), "文本不认领");

        assertFalse(new MineruTranscribeLeg(properties, null, null).isAvailable("a.pdf"), "两腿全空＝不认领");

        MineruProperties disabled = cloneFor(properties);
        disabled.setEnabled(false);
        assertFalse(new MineruTranscribeLeg(disabled, new MineruLocalClient(disabled),
                new MineruCloudClient(disabled)).isAvailable("a.pdf"), "总开关关＝不认领");
    }

    // ---------------- 装配 gating（document 侧） ----------------

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentAutoConfiguration.class));

    @Test
    void 选型键点名mineru时装腿并进门面() {
        runner.withPropertyValues("tool.document.transcriber=mineru", "mineru.local.base-url=http://127.0.0.1:9")
                .run(context -> {
                    assertThat(context).hasSingleBean(TranscribeStep.class);
                    assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep().legName())
                            .isEqualTo("mineru");
                });
    }

    @Test
    void 键空时不装mineru腿() {
        runner.withPropertyValues("mineru.local.base-url=http://127.0.0.1:9")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TranscribeStep.class);
                    assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep()).isNull();
                });
    }

    @Test
    void mineru总开关关时腿与配置都不装() {
        runner.withPropertyValues("tool.document.transcriber=mineru", "mineru.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TranscribeStep.class);
                    assertThat(context).doesNotHaveBean(MineruProperties.class);
                });
    }

    // ---------------- 伪端点 ----------------

    private MineruTranscribeLeg newLeg(MineruProperties props) {
        return new MineruTranscribeLeg(props, new MineruLocalClient(props), new MineruCloudClient(props));
    }

    private void handleCloud(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/file-urls/batch")) {
            cloudSubmitCount.incrementAndGet();
            respondJson(exchange, 200, "{\"code\":0,\"data\":{\"batch_id\":\"b1\","
                    + "\"file_urls\":[\"" + basePath(cloudServer) + "/up\"]}}");
            return;
        }
        if ("/up".equals(path)) {
            respondJson(exchange, 200, "");
            return;
        }
        if (path.contains("/extract-results/batch")) {
            if ("failed".equals(cloudState)) {
                respondJson(exchange, 200, "{\"code\":0,\"data\":{\"extract_result\":[{"
                        + "\"state\":\"failed\",\"err_msg\":\"cloud-boom\"}]}}");
                return;
            }
            respondJson(exchange, 200, "{\"code\":0,\"data\":{\"extract_result\":[{"
                    + "\"state\":\"done\",\"full_zip_url\":\"" + basePath(cloudServer) + "/z.zip\"}]}}");
            return;
        }
        respondBytes(exchange, 200, zipOf("云腿正文"));
    }

    private void handleLocal(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if ("/tasks".equals(path) && "POST".equals(exchange.getRequestMethod())) {
            localSubmitCount.incrementAndGet();
            respondJson(exchange, 200, "{\"task_id\":\"t1\"}");
            return;
        }
        if (path.endsWith("/result")) {
            respondBytes(exchange, 200, zipOf("本地腿正文"));
            return;
        }
        respondJson(exchange, 200, "{\"status\":\"" + localState + "\"}");
    }

    /** 取 minio/local 两条腿共用的最小 zip：只要 full.md（腿判据只看 markdown 有无） */
    private static byte[] zipOf(String markdown) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("full.md"));
            zos.write(markdown.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("t1_content_list.json"));
            zos.write("[]".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    private static String basePath(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respondJson(HttpExchange exchange, int code, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void respondBytes(HttpExchange exchange, int code, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
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
