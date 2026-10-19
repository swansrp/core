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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: AgentManifestRecorderTest
 * Description: 装配单落库腿契约测试（M14 棒2）。钉住五条：① 一个会话一行、重定形覆盖不新增；
 * ② <b>贡献者炸只丢它那一栏</b>（工具面与身份栏必须照落，台账缺栏比整场会话没台账更坏）；
 * ③ 没有贡献者时"工具面＋身份"这一半仍然成立；④ 写入格式与读视图解析互为镜像（写进去读不出来等于白记）；
 * ⑤ 棒 2 增量：提示词全文随行落（超长截尾、空白落 NULL），对话定位只补已有行的那一列、绝不凭空建行。
 * 全程 mock 仓储，不连库。
 *
 * @author Sharp
 * @since 2026-10-12
 */
class AgentManifestRecorderTest {

    private static ToolSurfaceFacts facts(String sessionId, String agentKey, List<String> toolNames) {
        return facts(sessionId, agentKey, toolNames, null);
    }

    private static ToolSurfaceFacts facts(String sessionId, String agentKey, List<String> toolNames,
                                          String promptText) {
        ToolSurfaceFacts facts = new ToolSurfaceFacts();
        facts.setSessionId(sessionId);
        facts.setAgentKey(agentKey);
        facts.setDisplayName("文档写作");
        facts.setOperator("bidrAdmin");
        facts.setToolNames(toolNames);
        facts.setPromptText(promptText);
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

    /**
     * 🔴 棒 2 增量的两条腿之一：全文随行落库，但<b>默认读视图不给</b>——只报"有没有"。
     * 这条钉的是权限闸的下半句：漏在端点上，视图这层必须先默认关。
     */
    @Test
    @DisplayName("提示词全文随行落：逐字一份，默认视图只报有没有")
    void promptTextStoredButNotInDefaultView() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);
        Mockito.when(repo.findBySession("s-7")).thenReturn(null);
        String prompt = "  红线骨架\n\n风格段（运行期可改）  \n";

        new AgentManifestRecorder(repo, providers())
                .onToolsResolved(facts("s-7", "inkhub-doc-writer", Arrays.asList("echo"), prompt));

        AgentManifest row = captured(repo);
        assertEquals(prompt, row.getPromptText(), "逐字一份：不 trim 不改写");
        AgentManifestView view = AgentManifestView.of(row);
        assertNull(view.getPromptText(), "默认不下发全文");
        assertTrue(view.isHasPromptText(), "不拉全文也能知道这行存了");
        assertNull(view.getConversationId(), "没收口过的会话没有对话定位");
    }

    @Test
    @DisplayName("全文的两种边界：空白落 NULL，超长截尾留显式标记")
    void promptTextBlankStoredAsNullAndOversizeTruncated() {
        assertNull(AgentManifestRecorder.truncatePrompt(null));
        assertNull(AgentManifestRecorder.truncatePrompt("   \n  "),
                "无 system 槽不得存成空串（读口要能区分\"没存\"与\"存了个空的\"）");

        StringBuilder sb = new StringBuilder();
        while (sb.length() <= AgentManifestRecorder.PROMPT_TEXT_MAX_CHARS + 1000) {
            sb.append("整包技能正文内联时提示词可以很长。");
        }
        String huge = sb.toString();
        String out = AgentManifestRecorder.truncatePrompt(huge);
        assertTrue(out.length() < huge.length(), "截到上限内");
        assertTrue(out.startsWith(huge.substring(0, 100)), "头部逐字保留");
        assertTrue(out.endsWith("字，已截尾）"), "尾部写明截了几字");
    }

    @Test
    @DisplayName("对话定位：给已有行补那一列，绝不凭空建行")
    void linkConversationOnlyUpdatesExistingRow() {
        AgentManifestService repo = Mockito.mock(AgentManifestService.class);

        new AgentManifestRecorder(repo, providers()).onConversationLinked("s-8", "  c-conv-8  ");

        Mockito.verify(repo).linkConversation("s-8", "c-conv-8");
        Mockito.verify(repo, Mockito.never()).save(Mockito.any(AgentManifest.class));
        Mockito.verify(repo, Mockito.never()).overwriteBySession(Mockito.any(AgentManifest.class));
    }

    /** 🔴 定位是复盘链路的旁证：任一边缺值或仓储炸都只该丢这一列，不得把对话归档带崩 */
    @Test
    @DisplayName("对话定位：空参不查库，写失败不外溢")
    void linkConversationSkipsBlankAndSwallowsFailure() {
        AgentManifestService quiet = Mockito.mock(AgentManifestService.class);
        AgentManifestRecorder recorder = new AgentManifestRecorder(quiet, providers());
        recorder.onConversationLinked("", "c-1");
        recorder.onConversationLinked("s-2", null);
        recorder.onConversationLinked("s-3", "  ");
        Mockito.verifyNoInteractions(quiet);

        AgentManifestService broken = Mockito.mock(AgentManifestService.class);
        Mockito.doThrow(new IllegalStateException("库不通")).when(broken).linkConversation("s-4", "c-4");
        new AgentManifestRecorder(broken, providers()).onConversationLinked("s-4", "c-4");
    }

    @Test
    @DisplayName("定位列随行读回：视图带 id、正文仍按 include 才给")
    void viewCarriesConversationIdWithoutBody() {
        AgentManifest row = new AgentManifest();
        row.setSessionId("s-9");
        row.setConversationId("c-conv-9");

        AgentManifestView view = AgentManifestView.of(row);
        assertEquals("c-conv-9", view.getConversationId());
        assertNull(view.getConversation(), "正文不在默认视图里");
    }
}
