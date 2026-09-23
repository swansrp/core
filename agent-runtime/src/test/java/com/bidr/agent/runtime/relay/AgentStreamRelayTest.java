package com.bidr.agent.runtime.relay;

import com.bidr.agent.runtime.client.AgentRuntimeRestClient;
import com.bidr.agent.runtime.client.AgentRuntimeSseClient;
import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.provider.AgentSystemProvider;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.bidr.agent.runtime.support.UpstreamStub;
import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bidr.agent.runtime.support.TestConfig.provider;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentStreamRelayTest
 * Description: 帧泵的四条硬口径：**帧保序原样透传**（含快照帧、注释忽略）、
 * **开流前错误走业务异常（平台码直通）**、**断流 ≠ 取消**（浏览器断开只收上游，绝不调 cancel）、
 * **幂等键透传**。
 * <p>
 * SPI 抽取后被测对象不含上游协议：链由 {@link AgentRuntimeProvider#openTurn} 建，
 * relay 只管 emitter 生命周期与泵 ⇒ 这四条口径对任何 provider 都成立。
 *
 * @author sharp
 * @since 2026/9/22
 */
class AgentStreamRelayTest {

    private static final String SID = "s-1";
    private static final String MID = "m-1";

    private UpstreamStub stub;
    private AgentRuntimeProvider provider;
    private AgentStreamRelay relay;

    @BeforeEach
    void setUp() throws IOException {
        stub = new UpstreamStub();
        AgentRuntimeConfigProvider config = provider(stub.baseUrl());
        provider = new AgentSystemProvider(new AgentRuntimeRestClient(config), new AgentRuntimeSseClient(config));
        relay = new AgentStreamRelay(config);
    }

    @AfterEach
    void tearDown() {
        relay.shutdown();
        stub.close();
    }

    private TurnOpenCmd turnCmd(String idempotencyKey) {
        TurnOpenCmd cmd = new TurnOpenCmd();
        cmd.setSessionId(SID);
        cmd.setContent("hi");
        cmd.setIdempotencyKey(idempotencyKey);
        return cmd;
    }

    @Test
    @DisplayName("帧保序原样透传（含 snapshot；`: ping` 注释被忽略）")
    void passesFramesVerbatimInOrder() throws Exception {
        List<String> frames = UpstreamStub.listOf(
                UpstreamStub.frame("message.accepted", SID, MID, "{}"),
                UpstreamStub.frame("message.snapshot", SID, MID,
                        "{\"frames\":[{\"type\":\"text.delta\",\"session_id\":\"" + SID + "\",\"message_id\":\"" + MID
                                + "\",\"data\":{\"delta\":\"前\"}}],\"truncated\":false}"),
                UpstreamStub.frame("tool.call", SID, MID,
                        "{\"tool_name\":\"web_search\",\"tool_call_id\":\"c-1\"}"),
                UpstreamStub.frame("message.done", SID, MID, "{\"text\":\"全文\"}"));
        stub.onSse("/open/v1/sessions/" + SID + "/messages:stream", frames);

        Collector sink = new Collector();
        RuntimeTurnLink link = provider.openTurn(turnCmd(null));
        try {
            relay.pump(link, sink);
        } finally {
            link.close();
        }

        assertEquals(frames, sink.frames, "帧必须原样保序透传（不解析/不合并/不重排）");
        assertEquals(1, sink.endCount);
    }

    @Test
    @DisplayName("开流前错误：平台码直通（42901 排队已满）")
    void preStreamErrorKeepsPlatformCode() {
        stub.onJson("/open/v1/sessions/" + SID + "/messages:stream",
                UpstreamStub.envelope(42901, "排队已满", "null"), 429);

        ServiceException error = assertThrows(ServiceException.class,
                () -> provider.openTurn(turnCmd(null)));

        assertEquals(42901, error.getErrCode().getErrCode());
        assertEquals("[42901] 排队已满", error.getMessage());
    }

    @Test
    @DisplayName("幂等键透传到上游（X-Idempotency-Key）")
    void forwardsIdempotencyKey() throws Exception {
        stub.onSse("/open/v1/sessions/" + SID + "/messages:stream",
                UpstreamStub.listOf(UpstreamStub.frame("message.accepted", SID, MID, "{}")));

        RuntimeTurnLink link = provider.openTurn(turnCmd("client-key-1"));
        try {
            assertTrue(stub.await(() -> "client-key-1".equals(stub.header("x-idempotency-key")), 3000),
                    "上游未收到幂等键：" + stub.header("x-idempotency-key"));
        } finally {
            link.close();
        }
    }

    @Test
    @DisplayName("断流 ≠ 取消：浏览器断开只收上游连接，绝不调 cancel")
    void browserDisconnectClosesUpstreamWithoutCancel() {
        List<String> frames = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            frames.add(UpstreamStub.frame("text.delta", SID, MID, "{\"delta\":\"x\"}"));
        }
        stub.onSse("/open/v1/sessions/" + SID + "/messages:stream", frames);

        SseEmitter emitter = relay.attach(provider.openTurn(turnCmd(null)));
        assertTrue(stub.await(() -> stub.activeStreams() > 0, 3000), "上游流未建立");

        // 模拟浏览器断开：SseEmitter 完成 → relay 的 onCompletion 收上游
        emitter.complete();

        assertTrue(stub.await(() -> stub.activeStreams() == 0, 3000), "浏览器断开后上游未被收流");
        assertFalse(stub.getRequestPaths().stream().anyMatch(path -> path.contains("cancel")),
                "断流不得触发取消：" + stub.getRequestPaths());
    }

    /** 记录型帧落点 */
    private static final class Collector implements AgentStreamRelay.FrameSink {

        private final List<String> frames = Collections.synchronizedList(new ArrayList<>());
        private int endCount;

        @Override
        public void onFrame(String rawFrameJson) {
            frames.add(rawFrameJson);
        }

        @Override
        public void onEnd() {
            endCount++;
        }
    }
}
