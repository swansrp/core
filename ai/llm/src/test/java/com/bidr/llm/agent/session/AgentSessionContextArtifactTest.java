package com.bidr.llm.agent.session;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Title: AgentSessionContextArtifactTest
 * Description: 通用产物查询 {@link AgentSessionContext#latestArtifactUrl(String)} 钉桩（JUnit4）——
 * 产物以 ARTIFACT 事件流为唯一正源（无旁路缓存）：按 seq 升序取同格式最后一条 url；
 * 大小写不敏感；无匹配/null 格式回 null。业务侧（如 save_supervision_log 带 Word 地址）据此消费。
 *
 * @author Sharp
 * @since 2026/11/17
 */
public class AgentSessionContextArtifactTest {

    private AgentSessionContext ctx(String sessionId) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(sessionId);
        return new AgentSessionContext(state, new InMemoryAgentSessionStore(), new HashMap<>());
    }

    private static Map<String, Object> artifact(String format, String url) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("file_id", "f-" + format);
        payload.put("name", "监理日志." + format);
        payload.put("size", 100L);
        payload.put("mime", "application/octet-stream");
        payload.put("format", format);
        payload.put("url", url);
        return payload;
    }

    @Test
    public void latestArtifactUrl取同格式最后一条且大小写不敏感() {
        AgentSessionContext c = ctx("s-art-1");
        Assert.assertNull("无产物回 null", c.latestArtifactUrl("docx"));

        c.emit(AgentEvent.ARTIFACT, artifact("docx", "http://old.docx"));
        c.emit(AgentEvent.ARTIFACT, artifact("pdf", "http://x.pdf"));
        c.emit(AgentEvent.ARTIFACT, artifact("docx", "http://new.docx"));

        Assert.assertEquals("同格式取最后一条", "http://new.docx", c.latestArtifactUrl("docx"));
        Assert.assertEquals("pdf 独立", "http://x.pdf", c.latestArtifactUrl("pdf"));
        Assert.assertEquals("格式大小写不敏感", "http://new.docx", c.latestArtifactUrl("DOCX"));
        Assert.assertNull("无该格式回 null", c.latestArtifactUrl("xlsx"));
        Assert.assertNull("null 格式回 null", c.latestArtifactUrl(null));
        Assert.assertNull("空格式回 null", c.latestArtifactUrl("  "));
    }

    @Test
    public void 脏事件不干扰产物查询() {
        AgentSessionContext c = ctx("s-art-2");
        c.emit(AgentEvent.LOG, "普通日志");
        c.emit(AgentEvent.ARTIFACT, "非 Map payload 脏帧");
        c.emit(AgentEvent.ARTIFACT, artifact("docx", "http://ok.docx"));
        Assert.assertEquals("http://ok.docx", c.latestArtifactUrl("docx"));
    }
}