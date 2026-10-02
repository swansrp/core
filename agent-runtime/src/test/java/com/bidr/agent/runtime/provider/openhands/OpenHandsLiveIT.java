package com.bidr.agent.runtime.provider.openhands;

import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;
import com.bidr.llm.agent.runtime.dto.*;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.bidr.llm.agent.runtime.spi.SessionCreateCmd;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: OpenHandsLiveIT
 * Description: 打真 OpenHands agent-server 的端到端样例（**不进 CI**，需显式开关）。
 * <p>
 * 跑法：先把上游端口隧道到本机（{@code ssh -N -L 13100:127.0.0.1:13100 <host>}），
 * 再 {@code OH_IT=1 OH_ENV_FILE=<只含一行 KEY=... 的文件> mvn -pl epc-ai-runtime -am test
 * -Dtest=OpenHandsLiveIT -DfailIfNoTests=false}。密钥只从文件读，**不进命令行、不进代码库、不打印**。
 * <p>
 * 验的是单测夹具验不了的东西：真握手（含 {@code X-Session-API-Key} 头鉴权）、真帧序与时延、
 * {@code after_seq} 重放、以及**两路同形**（直播 done 的全文 == 历史 reply.content）。
 * <p>
 * Order 9"沙箱自产 zip"那条腿额外要 {@code OH_ZIP_IT=1}：它真跑 LLM 手活、分钟级，
 * 与其余握手/帧序腿分开开关，避免日常复验为一把手活等几分钟。
 *
 * @author sharp
 * @since 2026/9/23
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenHandsLiveIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenHandsProvider provider;
    private String baseUrl;
    /** 密钥只从 OH_ENV_FILE 读，不进命令行、不进代码库、不打印 */
    private String apiKey;

    private String sessionId;
    private String turnId;
    private String doneText;
    private JsonNode doneUsage;

    @BeforeAll
    void setUp() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("OH_IT")), "未设 OH_IT=1，跳过真机样例");
        String envFile = System.getenv("OH_ENV_FILE");
        assertNotNull(envFile, "需给出 OH_ENV_FILE（内容为 KEY=xxx，可选 BASE_URL=xxx）");
        String key = null;
        baseUrl = "http://127.0.0.1:13100";
        for (String line : Files.readAllLines(new File(envFile).toPath(), StandardCharsets.UTF_8)) {
            if (line.startsWith("KEY=")) {
                key = line.substring(4).trim();
            } else if (line.startsWith("BASE_URL=")) {
                baseUrl = line.substring(9).trim();
            }
        }
        assertNotNull(key, "OH_ENV_FILE 里没有 KEY=");
        apiKey = key;
        provider = providerWith(8);
    }

    /**
     * 造 provider：只有步数上限可变。
     * 🔴 上游 agent 的单轮步数上限＝我方这里传的 max-iterations（实测默认 8 步会整单 FAILED，
     * 见资产清单 §2.3 坑⑤），"让沙箱产一个多文件包"这类手活必须放宽，故按腿给值而非全局放大。
     */
    private OpenHandsProvider providerWith(int maxIterations) {
        AgentRuntimeProperties properties = new AgentRuntimeProperties();
        properties.setBaseUrl(baseUrl);
        properties.setApiKey(apiKey);
        properties.setConnectTimeoutMs(8000);
        properties.setIdleTimeoutMs(180000);
        properties.setHeartbeatSeconds(15);
        properties.setRelayThreads(4);
        properties.setWorkspaceRoot("/workspace");
        properties.setAgentProfile("default");
        properties.setMaxIterations(maxIterations);
        AgentRuntimeConfigProvider config = new AgentRuntimeConfigProvider(properties, null);
        return new OpenHandsProvider(config, properties, new OpenHandsClient(config));
    }

    @Test
    @Order(1)
    @DisplayName("Agent 清单 = agent profiles")
    void listsAgents() {
        List<AgentInfo> agents = provider.listAgents();
        assertFalse(agents.isEmpty(), "上游应至少有一个 agent profile");
        System.out.println("[IT] agents=" + agents.stream().map(AgentInfo::getAgentCode)
                .reduce((a, b) -> a + "," + b).orElse(""));
    }

    @Test
    @Order(2)
    @DisplayName("建会话：我方指定 conversation_id + 每会话独立工作目录 + tags 冗余")
    void createsSession() {
        SessionCreateCmd cmd = new SessionCreateCmd();
        cmd.setAgentCode("default");
        cmd.setBusinessType("supervision");
        cmd.setBusinessId("PRJ-IT-1");
        cmd.setGroupKey("it-group");
        SessionInfo info = provider.createSession(cmd);

        sessionId = info.getSessionId();
        assertNotNull(sessionId, "上游应回会话 id");
        System.out.println("[IT] session=" + sessionId);

        SessionInfo validated = provider.validateSession(sessionId);
        assertEquals(sessionId, validated.getSessionId(), "建完即可读");
    }

    @Test
    @Order(3)
    @DisplayName("建轮即流：真帧序 accepted → started → … → done")
    void streamsNewTurn() throws Exception {
        TurnOpenCmd cmd = new TurnOpenCmd();
        cmd.setSessionId(sessionId);
        cmd.setAgentCode("default");
        cmd.setContent("用 shell 执行 echo it-ok，然后只回答：完成");
        long t0 = System.currentTimeMillis();
        List<String> frames = collect(provider.openTurn(cmd), 180);
        long cost = System.currentTimeMillis() - t0;

        List<String> types = types(frames);
        System.out.println("[IT] 直播帧序 " + cost + "ms：" + types);
        assertEquals("message.accepted", types.get(0), "首帧必须是 accepted");
        assertEquals("message.done", types.get(types.size() - 1), "末帧必须是终局 done");
        assertTrue(types.contains("tool.call") && types.contains("tool.result"),
                "该任务会调 terminal：" + types);

        JsonNode accepted = MAPPER.readTree(frames.get(0));
        turnId = accepted.path("message_id").asText();
        assertFalse(turnId.isEmpty(), "accepted 必须给出轮次 id");
        assertEquals(sessionId, accepted.path("session_id").asText());

        JsonNode done = MAPPER.readTree(frames.get(frames.size() - 1));
        doneText = done.path("data").path("text").asText();
        assertFalse(doneText.isEmpty(), "done 必须带全文");
        doneUsage = done.path("data").path("usage");
        System.out.println("[IT] turn=" + turnId + " text=" + doneText + " usage=" + doneUsage);
        for (String frame : frames) {
            JsonNode node = MAPPER.readTree(frame);
            assertEquals(sessionId, node.path("session_id").asText(), "每帧都要带 session_id");
            assertEquals(turnId, node.path("message_id").asText(), "每帧都要带本轮 message_id");
        }
    }

    @Test
    @Order(4)
    @DisplayName("挂流恢复：after_seq=-1 重放 → accepted + snapshot（+ 已终局则合成终局）")
    void attachesFinishedTurn() throws Exception {
        List<String> frames = collect(provider.attachTurn(sessionId, turnId), 60);
        List<String> types = types(frames);
        System.out.println("[IT] 挂流帧序：" + types);

        assertEquals("message.accepted", types.get(0));
        assertEquals("message.snapshot", types.get(1), "重放必须归约成快照");
        JsonNode snapshot = MAPPER.readTree(frames.get(1));
        List<String> inner = new ArrayList<>();
        for (JsonNode frame : snapshot.path("data").path("frames")) {
            inner.add(frame.path("type").asText());
        }
        System.out.println("[IT] 快照内帧：" + inner);
        assertTrue(inner.contains("tool.call") && inner.contains("tool.result"),
                "已落盘的工具步应在快照里：" + inner);
        assertTrue(inner.contains("text.delta"), "正文应在快照里（历史无增量，单帧给全）：" + inner);
        assertEquals("message.done", types.get(types.size() - 1), "晚挂已终局 → 合成终局帧后收流");
        assertEquals(doneText, MAPPER.readTree(frames.get(frames.size() - 1))
                .path("data").path("text").asText(), "快照路径的全文必须与直播一致");
    }

    @Test
    @Order(5)
    @DisplayName("两路同形：历史 reply 与直播 done 对得上（同一装配出口）")
    void historyMatchesLiveStream() {
        TurnPage page = provider.history(sessionId, null, 50);
        assertEquals(1, page.getItems().size(), "本样例只发过一轮");

        TurnItem turn = page.getItems().get(0);
        assertEquals(turnId, turn.getMessageId(), "历史轮次 id 必须等于直播 accepted 的 id");
        assertEquals("completed", turn.getReply().getStatus());
        assertEquals(doneText, turn.getReply().getContent(), "历史正文 == 直播 done 全文");
        List<String> blockTypes = new ArrayList<>();
        for (TurnBlock block : turn.getReply().getBlocks()) {
            blockTypes.add(block.getType());
        }
        System.out.println("[IT] 历史 blocks=" + blockTypes + " usage=" + turn.getReply().getUsage());
        assertTrue(blockTypes.contains("tool_call") && blockTypes.contains("tool_result")
                && blockTypes.contains("text"), "块类型齐：" + blockTypes);

        JsonNode historyUsage = (JsonNode) turn.getReply().getUsage();
        assertNotNull(historyUsage, "历史也要给出用量（直播基线取 full_state、历史基线取上一轮末 stats）");
        assertEquals(doneUsage.path("prompt_tokens").asLong(), historyUsage.path("prompt_tokens").asLong(),
                "两路 prompt_tokens 必须一致");
        assertEquals(doneUsage.path("completion_tokens").asLong(),
                historyUsage.path("completion_tokens").asLong(), "两路 completion_tokens 必须一致");

        assertEquals(turn, provider.turn(sessionId, turnId), "单轮查询与列表同源");
        assertFalse(Boolean.TRUE.equals(page.getHasMore()));
    }

    @Test
    @Order(6)
    @DisplayName("取消：interrupt → message.cancelled（终局定格，其后帧不再产出）")
    void cancelsRunningTurn() throws Exception {
        TurnOpenCmd cmd = new TurnOpenCmd();
        cmd.setSessionId(sessionId);
        cmd.setAgentCode("default");
        cmd.setContent("用 shell 执行 sleep 30 然后 echo late，务必真的 sleep 30 秒");
        RuntimeTurnLink link = provider.openTurn(cmd);

        List<String> frames = new ArrayList<>();
        String cancelTurnId = null;
        long deadline = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < deadline) {
            String frame = link.nextFrame();
            if (frame == null) {
                break;
            }
            frames.add(frame);
            JsonNode node = MAPPER.readTree(frame);
            if (cancelTurnId == null) {
                cancelTurnId = node.path("message_id").asText(null);
            }
            // 等它真跑起来（见到工具步或思考增量）再取消
            if ("tool.call".equals(node.path("type").asText())
                    || "thinking.delta".equals(node.path("type").asText())) {
                break;
            }
        }
        assertNotNull(cancelTurnId, "取消前必须先拿到轮次 id");
        CancelResult result = provider.cancel(sessionId, cancelTurnId);
        assertTrue(Boolean.TRUE.equals(result.getCancelled()));
        System.out.println("[IT] 已发取消，继续收流…");

        boolean cancelled = false;
        long end = System.currentTimeMillis() + 60000;
        while (System.currentTimeMillis() < end) {
            String frame = link.nextFrame();
            if (frame == null) {
                break;
            }
            frames.add(frame);
            String type = MAPPER.readTree(frame).path("type").asText();
            if ("message.cancelled".equals(type)) {
                cancelled = true;
                break;
            }
        }
        link.close();
        assertTrue(cancelled, "取消后必须收到 message.cancelled，实收 " + types(frames));
    }

    @Test
    @Order(7)
    @DisplayName("产物回读：写进沙箱工作区的字节能原样读回（上游 file/download 与 upload 对称）")
    void readsBackArtifact() {
        assertTrue(provider.supportsFileDownload(), "OpenHands 应声明支持产物回读");

        // 自带会话，不依赖前序轮次——单跑这条腿即可验证回读通道本身
        SessionCreateCmd cmd = new SessionCreateCmd();
        cmd.setAgentCode("default");
        cmd.setBusinessType("supervision");
        cmd.setBusinessId("PRJ-IT-DL");
        String cid = provider.createSession(cmd).getSessionId();
        try {
            byte[] payload = ("inkhub-m12-step0-" + System.currentTimeMillis())
                    .getBytes(StandardCharsets.UTF_8);
            String absolute = provider.uploadFile(cid, "probe.txt", payload);
            assertTrue(absolute.contains(cid), "写入路径应落在该会话工作目录内：" + absolute);

            byte[] back = provider.downloadFile(cid, "probe.txt");
            assertArrayEquals(payload, back, "回读字节必须与写入字节逐字节相等");
            System.out.println("[IT] 产物回读 OK bytes=" + back.length + " @ " + absolute);

            // 读侧闸门：绝对路径与穿越段在拼进上游之前就得拒
            assertThrows(Exception.class, () -> provider.downloadFile(cid, "/etc/passwd"), "绝对路径必须拒");
            assertThrows(Exception.class, () -> provider.downloadFile(cid, "../probe.txt"), "穿越必须拒");

            // 上游侧不存在的路径：必须映射成平台码 40402——业务要靠它分"沙箱没产出"与"通道坏了"。
            // 🔴 断言到码，不接受"抛了个异常就算绿"（转换器抢错时抛的是不可达 -1，语义完全不同）
            Exception missing = assertThrows(Exception.class,
                    () -> provider.downloadFile(cid, "definitely-not-here.zip"));
            assertTrue(String.valueOf(missing.getMessage()).contains("40402"),
                    "缺失产物应映射为平台码 40402，实得：" + missing.getMessage());
            System.out.println("[IT] 缺失产物错误=" + missing.getMessage());
        } finally {
            provider.deleteSession(cid);
        }
    }

    @Test
    @Order(8)
    @DisplayName("软删 + 会话不存在映射为平台码 40402")
    void deletesSession() {
        DeleteResult result = provider.deleteSession(sessionId);
        assertTrue(Boolean.TRUE.equals(result.getDeleted()));

        Exception error = assertThrows(Exception.class, () -> provider.validateSession(sessionId));
        assertTrue(String.valueOf(error.getMessage()).contains("40402"),
                "删后会话应映射为 40402（前端据此透明新建），实得：" + error.getMessage());
    }

    @Test
    @Order(9)
    @DisplayName("手活腿：沙箱自己产出一个多文件 zip，我方按相对路径整包读回")
    void agentProducedZipComesBack() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("OH_ZIP_IT")),
                "未设 OH_ZIP_IT=1，跳过沙箱产包腿（这条要跑真 LLM 手活，分钟级）");

        // 🔴 放宽步数：产包是"写两个文件 + 打一个 zip"的多步手活，8 步上限会整单 FAILED（坑⑤）
        OpenHandsProvider hand = providerWith(20);
        SessionCreateCmd cmd = new SessionCreateCmd();
        cmd.setAgentCode("default");
        cmd.setBusinessType("supervision");
        cmd.setBusinessId("PRJ-IT-ZIP");
        String cid = hand.createSession(cmd).getSessionId();
        try {
            TurnOpenCmd turn = new TurnOpenCmd();
            turn.setSessionId(cid);
            turn.setAgentCode("default");
            turn.setContent(String.join("\n",
                    "请在当前工作目录里完成以下手工，全部用 shell 执行，不要问我：",
                    "1. mkdir -p out/inkprobe/templates",
                    "2. 写文件 out/inkprobe/SKILL.md，内容（含开头的 --- 三行块）：",
                    "---",
                    "name: inkprobe",
                    "description: 探针技能包，用于验证沙箱产包能力",
                    "---",
                    "# 探针技能",
                    "3. 写文件 out/inkprobe/templates/清单.md，内容为三行中文：设备名称、检查日期、结论",
                    "4. 用 python3 的 zipfile 模块（不要用 shell 的 zip 命令）把 out/inkprobe 整个目录打包成",
                    "   out/inkprobe.zip，压缩包内条目名必须以 inkprobe/ 开头（如 inkprobe/SKILL.md）。",
                    "5. 最后只回答：完成"));
            List<String> frames = collect(hand.openTurn(turn), 420);
            List<String> types = types(frames);
            System.out.println("[IT] 产包帧序：" + types);
            assertEquals("message.done", types.get(types.size() - 1), "产包轮必须正常终局：" + types);

            byte[] zip = hand.downloadFile(cid, "out/inkprobe.zip");
            assertTrue(zip.length > 22, "zip 字节数异常：" + zip.length);
            assertEquals('P', zip[0]);
            assertEquals('K', zip[1]);

            List<String> entries = zipNames(zip);
            System.out.println("[IT] zip 条目=" + entries + " bytes=" + zip.length);
            assertTrue(entries.stream().anyMatch(e -> e.endsWith("SKILL.md")),
                    "包内应含 SKILL.md：" + entries);
            assertTrue(entries.stream().anyMatch(e -> e.contains("inkprobe/")),
                    "条目应带 inkprobe/ 根前缀：" + entries);
        } finally {
            hand.deleteSession(cid);
        }
    }

    // ==================== 工具 ====================

    /**
     * 收流到终局或超时（返回已收帧；超时不失败，由断言判定）
     */
    private List<String> collect(RuntimeTurnLink link, int seconds) throws Exception {
        List<String> frames = new ArrayList<>();
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        try {
            while (System.currentTimeMillis() < deadline) {
                String frame = link.nextFrame();
                if (frame == null) {
                    break;
                }
                frames.add(frame);
                String type = MAPPER.readTree(frame).path("type").asText();
                if (type.startsWith("message.done") || type.startsWith("message.error")
                        || type.startsWith("message.cancelled")) {
                    break;
                }
            }
        } finally {
            link.close();
        }
        return frames;
    }

    private List<String> types(List<String> frames) throws Exception {
        List<String> types = new ArrayList<>();
        for (String frame : frames) {
            types.add(MAPPER.readTree(frame).path("type").asText());
        }
        return types;
    }

    /**
     * 列出 zip 条目名。UTF-8 名按 UTF-8 解，GBK 名解出会带替换字符——本探针只断言前缀与后缀，
     * 中文名能否原样回传属业务侧 SkillPackageParser 的 GBK 找回口径，不在这一腿押注。
     */
    private List<String> zipNames(byte[] zip) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
