package com.bidr.agent.console.manifest;

import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.repository.AgentManifestService;
import com.bidr.agent.console.dto.AgentManifestView;
import com.bidr.llm.agent.ToolSurfaceFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentManifestRecorderTest
 * Description: 装配单落库腿契约测试（M14 棒2）。钉住四条：① 一个会话一行、重定形覆盖不新增；
 * ② <b>贡献者炸只丢它那一栏</b>（工具面与身份栏必须照落，台账缺栏比整场会话没台账更坏）；
 * ③ 没有贡献者时"工具面＋身份"这一半仍然成立；④ 写入格式与读视图解析互为镜像（写进去读不出来等于白记）。
 * 全程 mock 仓储，不连库。
 *
 * @author Sharp
 * @since 2026-10-12
 */
class AgentManifestRecorderTest {

    private static ToolSurfaceFacts facts(String sessionId, String agentKey, List<String> toolNames) {
        ToolSurfaceFacts facts = new ToolSurfaceFacts();
        facts.setSessionId(sessionId);
        facts.setAgentKey(agentKey);
        facts.setDisplayName("文档写作");
        facts.setOperator("bidrAdmin");
        facts.setToolNames(toolNames);
        facts.setPayload(new HashMap<String, Object>());
        return facts;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<AgentManifestContributor> providers(AgentManifestContributor... contributors) {
        ObjectProvider<AgentManifestContributor> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.iterator()).thenReturn(Arrays.asList(contributors).iterator());
        return provider;
    }

    private static AgentManifest captured(AgentManifestService repo) {
        ArgumentCaptor<AgentManifest> captor = ArgumentCaptor.forClass(AgentManifest.class);
        Mockito.verify(repo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("会话标识缺失：整段跳过，既不查也不写")
    void skipsWhenSessionIdBlank() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        new AgentManifestRecorder(repo, providers()).onToolsResolved(facts("", "inkhub-doc-writer", Arrays.asList("echo")));

        Mockito.verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("无贡献者：工具面与身份栏照落，业务三栏留空")
    void writesIdentityHalfWithoutContributor() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        Mockito.when(repo.findBySession("s-1")).thenReturn(null);

        new AgentManifestRecorder(repo, providers())
                .onToolsResolved(facts("s-1", "inkhub-doc-writer", Arrays.asList("echo", "add")));

        AgentManifest row = captured(repo);
        assertEquals("s-1", row.getSessionId());
        assertEquals("inkhub-doc-writer", row.getAgentKey());
        assertEquals("bidrAdmin", row.getAskedBy());
        assertEquals(Integer.valueOf(2), row.getToolCount());
        assertEquals("[\"echo\",\"add\"]", row.getToolNames());
        assertNull(row.getModelTier());
        assertEquals("[]", row.getSkillRefs());
        assertEquals("[]", row.getPromptSections());
        assertNull(row.getExtras());
    }

    @Test
    @DisplayName("贡献者填三栏：模型档/技能带版本/段序原样入库并可读回")
    void contributorFillsBusinessColumns() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        Mockito.when(repo.findBySession("s-2")).thenReturn(null);

        AgentManifestContributor contributor = (f, draft) -> {
            draft.setModelTier("deepseek-v3");
            draft.getSkills().add(new AgentManifestSkillRef("doc-writing", 3, null));
            draft.getSections().add("红线骨架");
            draft.getSections().add("## Skills 清单");
            draft.getExtras().put("spaceId", "39");
        };
        new AgentManifestRecorder(repo, providers(contributor))
                .onToolsResolved(facts("s-2", "inkhub-doc-writer", Arrays.asList("echo")));

        AgentManifest row = captured(repo);
        AgentManifestView view = AgentManifestView.of(row);
        assertEquals("deepseek-v3", view.getModelTier());
        assertEquals(Arrays.asList("红线骨架", "## Skills 清单"), view.getPromptSections());
        assertEquals(1, view.getSkills().size());
        assertEquals("doc-writing", view.getSkills().get(0).getName());
        assertEquals(Integer.valueOf(3), view.getSkills().get(0).getVersion());
        assertEquals("39", view.getExtras().get("spaceId"));
        assertEquals(Arrays.asList("echo"), view.getToolNames());
    }

    /** 🔴 本棒最容易出的事故形态：贡献者读库/读引擎抛错，把整行带走 ⇒ 这场会话彻底查不到手里有什么 */
    @Test
    @DisplayName("贡献者抛错：整行照落，没写完的栏留空")
    void contributorFailureLosesOnlyItsColumns() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        Mockito.when(repo.findBySession("s-3")).thenReturn(null);

        AgentManifestContributor broken = (f, draft) -> {
            draft.setModelTier("deepseek-v3");
            throw new IllegalStateException("技能清单读库失败");
        };
        new AgentManifestRecorder(repo, providers(broken))
                .onToolsResolved(facts("s-3", "inkhub-doc-writer", Arrays.asList("echo", "read_file")));

        AgentManifest row = captured(repo);
        assertEquals(Integer.valueOf(2), row.getToolCount());
        assertEquals("[\"echo\",\"read_file\"]", row.getToolNames());
        assertEquals("1", row.getValid(), "整行不得因贡献者抛错而放弃入库");
        assertEquals("[]", row.getSkillRefs(), "技能栏只记到抛错前的部分");
    }

    @Test
    @DisplayName("贡献者只负责点名的 agent：agentKey 不匹配时不回调")
    void contributorScopedByAgentKey() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        Mockito.when(repo.findBySession("s-4")).thenReturn(null);

        List<String> called = new ArrayList<>();
        AgentManifestContributor scoped = new AgentManifestContributor() {
            @Override
            public List<String> agentKeys() {
                return Collections.singletonList("inkhub-log-report");
            }

            @Override
            public void contribute(ToolSurfaceFacts facts, AgentManifestDraft draft) {
                called.add(facts.getAgentKey());
            }
        };
        new AgentManifestRecorder(repo, providers(scoped))
                .onToolsResolved(facts("s-4", "inkhub-doc-writer", Arrays.asList("echo")));

        assertTrue(called.isEmpty());
        Mockito.verify(repo).save(Mockito.any(AgentManifest.class));
    }

    @Test
    @DisplayName("同会话重定形：按 sessionId 覆盖，不再插一行")
    void overwritesExistingRow() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        Mockito.when(repo.findBySession("s-5")).thenReturn(new AgentManifest());

        new AgentManifestRecorder(repo, providers())
                .onToolsResolved(facts("s-5", "inkhub-doc-writer", Arrays.asList("echo")));

        Mockito.verify(repo).overwriteBySession(Mockito.any(AgentManifest.class));
        Mockito.verify(repo, Mockito.never()).save(Mockito.any(AgentManifest.class));
    }

    @Test
    @DisplayName("脏 JSON 只脏那一栏：读视图回落空集合，整场会话仍可读")
    void viewToleratesDirtyJson() {
        AgentManifest row = new AgentManifest();
        row.setSessionId("s-6");
        row.setToolNames("{不是 json");
        row.setSkillRefs(null);

        AgentManifestView view = AgentManifestView.of(row);
        assertEquals("s-6", view.getSessionId());
        assertTrue(view.getToolNames().isEmpty());
        assertTrue(view.getSkills().isEmpty());
        assertNull(AgentManifestView.of(null));
    }
}
