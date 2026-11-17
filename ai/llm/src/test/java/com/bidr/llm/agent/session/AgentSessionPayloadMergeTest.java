package com.bidr.llm.agent.session;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Title: AgentSessionPayloadMergeTest
 * Description: 「建后未起跑」会话 resume payload 补丁的合并口径钉桩（JUnit4）：
 * 补丁键覆盖、null 值跳过、空补丁不复制原表（身份保持不变，避免无意义对象）。
 *
 * @author Sharp
 * @since 2026/11/17
 */
public class AgentSessionPayloadMergeTest {

    @Test
    public void patchOverridesAndKeepsOtherKeys() {
        Map<String, Object> base = new HashMap<>();
        base.put("logDate", "2026-11-17");
        base.put("instruction", null);
        Map<String, Object> patch = new HashMap<>();
        patch.put("instruction", "生成今天的日志");
        patch.put("baseDraft", "旧底稿");
        patch.put("ignored", null);

        Map<String, Object> merged = AgentSessionService.mergePayload(base, patch);

        assertEquals("2026-11-17", merged.get("logDate"));
        assertEquals("生成今天的日志", merged.get("instruction"));
        assertEquals("旧底稿", merged.get("baseDraft"));
        assertNull("null 值不得覆盖", merged.get("ignored"));
    }

    @Test
    public void emptyPatchKeepsBaseIdentity() {
        Map<String, Object> base = Collections.singletonMap("logDate", "2026-11-17");

        assertSame(base, AgentSessionService.mergePayload(base, null));
        assertSame(base, AgentSessionService.mergePayload(base, Collections.emptyMap()));
        assertSame(base, AgentSessionService.mergePayload(base,
                Collections.singletonMap("instruction", null)));
    }

    @Test
    public void nullBaseTreatedAsEmpty() {
        Map<String, Object> merged = AgentSessionService.mergePayload(null,
                Collections.singletonMap("instruction", "补语"));

        assertEquals(1, merged.size());
        assertEquals("补语", merged.get("instruction"));
    }
}