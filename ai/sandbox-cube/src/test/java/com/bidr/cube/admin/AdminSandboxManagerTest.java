package com.bidr.cube.admin;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.bidr.cube.client.CubeHttpClient;
import com.bidr.cube.config.CubeSandboxProperties;
import com.bidr.cube.support.CubeFakeServer;
import com.bidr.kernel.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AdminSandboxManagerTest
 * Description: 管理沙箱生命周期 fake 单测（本地回环 {@link CubeFakeServer}，不打外网）。钉：
 * 懒建（首次 ensureDir 才建：先 role 对账复用——M-2 重启零 createAdmin，列表 500 降级
 * createAdmin＋warn；body 带 role=cube-admin 标记＋{hostRoot}→{hostRoot} 内外一致挂载）；
 * mkdir -p 命令经管理沙箱 exec 下发；exec 故障自愈（重建管理沙箱重试一次成功）；
 * 自愈后再失败抛 ServiceException 且只重建一次；destroy 幂等（未建/已删不二次 DELETE）。
 *
 * @author Sharp
 * @since 2026/11/16
 */
class AdminSandboxManagerTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private CubeFakeServer fake;
    private CubeHttpClient client;
    private AdminSandboxManager manager;

    @BeforeEach
    void startFake() throws IOException {
        fake = new CubeFakeServer();
        client = new CubeHttpClient(fake.properties);
        manager = new AdminSandboxManager(client, fake.properties);
    }

    @AfterEach
    void stopFake() {
        fake.stop();
    }

    @Test
    @DisplayName("ensureDir 懒建：首次才 createAdmin（body=role 标记＋{hostRoot}→{hostRoot} 内外一致挂载）＋mkdir -p 会话目录；再次不重建")
    void ensureDirLazyCreatesAdminSandboxAndMkdirs() throws IOException {
        manager.ensureDir("sess-1");
        assertEquals(1, fake.adminCreateBodies.size(), "首次 ensureDir 懒建管理沙箱");
        assertTrue(fake.execCommands.get(0).startsWith("mkdir -p "),
                "首条 exec 应为 mkdir：" + fake.execCommands.get(0));
        assertEquals("mkdir -p '/data/sandbox/sess-1'", fake.execCommands.get(0),
                "🔴 目录即业务沙箱 hostPath（内外路径一致，无需换算）");

        // 管理沙箱 create body 形状
        JsonNode body = OM.readTree(fake.adminCreateBodies.get(0));
        assertEquals("tpl-test", body.path("templateID").asText());
        assertEquals(fake.properties.getAdminTimeout(), body.path("timeout").asLong());
        assertEquals("cube-admin", body.path("metadata").path("role").asText(),
                "标记键 role=cube-admin（对账/清理不误伤业务会话）");
        String hostMountText = body.path("metadata").path("host-mount").asText();
        JsonNode hostMount = OM.readTree(hostMountText);
        assertTrue(hostMount.isArray(), "host-mount 应为 JSON 字符串承载的数组：" + hostMountText);
        assertEquals("/data/sandbox", hostMount.get(0).path("hostPath").asText());
        assertEquals("/data/sandbox", hostMount.get(0).path("mountPath").asText(),
                "内外路径一致：mkdir 的路径即业务沙箱 hostPath");
        assertEquals(false, hostMount.get(0).path("readOnly").asBoolean());
        assertEquals(false, body.has("network"), "管理沙箱不投喂 network.allowOut");

        // 再次 ensureDir（不同会话）：复用同一管理沙箱，只多发一条 mkdir
        manager.ensureDir("sess-2");
        assertEquals(1, fake.adminCreateBodies.size(), "常驻管理沙箱不重复建");
        assertEquals("mkdir -p '/data/sandbox/sess-2'", fake.execCommands.get(1));
    }

    @Test
    @DisplayName("自愈：exec 失败一次 ⇒ 重建管理沙箱（DELETE 旧＋create 新）后重试成功")
    void ensureDirSelfHealsByRecreatingAdminSandbox() {
        fake.execFailuresRemaining = 1;
        manager.ensureDir("sess-heal");
        assertEquals(2, fake.adminCreateBodies.size(), "重建＝恰好多一次 createAdmin");
        assertEquals(1, fake.adminDeletes, "旧管理沙箱 best-effort 清理一次");
        assertEquals(2, fake.execCommands.size(), "mkdir 恰好重试一次");
        assertEquals("mkdir -p '/data/sandbox/sess-heal'", fake.execCommands.get(1),
                "重试命令与首次一致");
    }

    @Test
    @DisplayName("自愈后再失败：抛 ServiceException，且只重建一次（不无限循环）")
    void ensureDirThrowsAfterHealRetryFails() {
        fake.execStatus = 500;
        ServiceException e = assertThrows(ServiceException.class, () -> manager.ensureDir("sess-dead"));
        assertTrue(e.getMessage().contains("预建宿主目录失败"), e.getMessage());
        assertEquals(2, fake.adminCreateBodies.size(), "自愈只重建一次");
        assertEquals(2, fake.execCommands.size(), "mkdir 恰好试两次（原始＋自愈重试）");
    }

    @Test
    @DisplayName("createAdmin 失败直接上抛（平台不可用不可自愈，不吞）")
    void ensureDirPropagatesAdminCreateFailure() {
        // fake 未提供 create 失败开关 ⇒ 走未接线路径（apiUrl 置空）：createAdmin 直接抛，不进自愈
        fake.properties.setApiUrl("");
        ServiceException e = assertThrows(ServiceException.class, () -> manager.ensureDir("sess-x"));
        assertTrue(e.getMessage().contains("apiUrl"), e.getMessage());
        assertEquals(0, fake.execCommands.size(), "管理沙箱都没建起来，不应发 exec");
    }

    @Test
    @DisplayName("M-2 重启对账：管理沙箱在册 ⇒ 新实例懒建先 role 对账复用（零 createAdmin），mkdir 照常")
    void ensureDirReusesExistingAdminSandboxAfterRestart() {
        manager.ensureDir("sess-first");
        assertEquals(1, fake.adminCreateBodies.size(), "首建 createAdmin 一次");

        // 模拟进程重启：内存 id 丢失；平台侧管理沙箱仍在册（fake 列表按 live 集回 role=cube-admin 项）
        AdminSandboxManager restarted = new AdminSandboxManager(client, fake.properties);
        restarted.ensureDir("sess-after-restart");

        assertEquals(1, fake.adminCreateBodies.size(), "role 对账命中 ⇒ 零 createAdmin");
        assertEquals("mkdir -p '/data/sandbox/sess-after-restart'", fake.execCommands.get(1),
                "复用的管理沙箱照常执行 mkdir");
    }

    @Test
    @DisplayName("M-2 对账降级：role 列表 500 ⇒ warn 一行＋降级 createAdmin 建成功（行为不断）")
    void ensureDirDegradesToCreateWhenRoleListFails() {
        Logger logger = (Logger) LoggerFactory.getLogger(AdminSandboxManager.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            fake.listStatus = 500;
            manager.ensureDir("sess-degrade");
        } finally {
            logger.detachAppender(appender);
        }
        assertEquals(1, fake.adminCreateBodies.size(), "对账失败 ⇒ 降级直接 createAdmin，行为不断");
        assertEquals("mkdir -p '/data/sandbox/sess-degrade'", fake.execCommands.get(0),
                "降级建成的管理沙箱照常完成 mkdir");
        assertTrue(appender.list.stream().anyMatch(event ->
                        event.getLevel().toString().equals("WARN")
                                && event.getFormattedMessage().contains("对账失败")),
                "应有 warn 日志一行（管理沙箱对账失败，降级直接新建）");
    }

    @Test
    @DisplayName("destroy 幂等：未建时静默（零请求）；已建销毁一次后再调不再 DELETE")
    void destroyIsIdempotent() {
        manager.destroy();
        assertEquals(0, fake.adminDeletes, "未建管理沙箱 ⇒ destroy 静默，零请求");

        manager.ensureDir("sess-d");
        manager.destroy();
        assertEquals(1, fake.adminDeletes, "已建 ⇒ 一次 DELETE");
        manager.destroy();
        assertEquals(1, fake.adminDeletes, "已销毁（id 清空）⇒ 不再发 DELETE");
    }
}
