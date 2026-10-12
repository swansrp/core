package com.bidr.agent.console.manifest;

import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.repository.AgentManifestService;
import com.bidr.llm.agent.AgentToolSurfaceSink;
import com.bidr.llm.agent.ToolSurfaceFacts;
import com.bidr.llm.agent.conversation.AgentConversationLinkSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentManifestRecorder
 * Description: 装配单落库腿（M14 棒2）：接框架的工具面定形回调，补完业务三栏后落一行；
 * 棒 2 增量再接对话定位回调，收口那一刻给同一行补 conversation_id。
 *
 * <p><b>整段兜异常</b>：装配单是复盘链路，写不进（表未建、库不通、贡献者抛错）一律只记 warn，
 * 绝不把工具循环带崩——这条与 {@code AgentRunLedgerService} 同口径。</p>
 *
 * <p><b>一行一会话</b>：同会话重定形（理论上不发生，除非业务在同一 sessionId 上再起一次循环）
 * 按 sessionId 覆盖，贡献者这次没交的栏保留上一版值（MP 的 null 字段不进 SET 段）。</p>
 *
 * <p>开关 {@code agent-console.manifest.enabled}（默认开）：关掉后本 Bean 不存在，
 * 框架侧 {@code BeanUtil.getBean} 拿不到 ⇒ 连回调都不发，回到改前行为。</p>
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.manifest", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentManifestRecorder implements AgentToolSurfaceSink, AgentConversationLinkSink {

    /** JSON 列序列化（只写不读；读腿见 {@code AgentManifestView#of}） */
    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * 提示词全文落库上限（字）：列是 MEDIUMTEXT，撑不破，但一行的体量得有天花板——
     * 整包技能正文内联进提示词时可达几十 KB，永久留存按会话数线性涨。
     * 超限截尾留显式标记（宁可留头部可读的一份，也不要把整列放弃成 NULL）
     */
    static final int PROMPT_TEXT_MAX_CHARS = 100_000;

    private final AgentManifestService repo;

    /** 贡献者可选装配：一个都没有也能落"工具面 + 会话身份"这一半 */
    private final ObjectProvider<AgentManifestContributor> contributors;

    @Override
    public void onToolsResolved(ToolSurfaceFacts facts) {
        if (facts == null || !StringUtils.hasText(facts.getSessionId())) {
            return;
        }
        String sessionId = facts.getSessionId();
        try {
            AgentManifestDraft draft = new AgentManifestDraft();
            for (AgentManifestContributor contributor : contributingFor(facts.getAgentKey())) {
                try {
                    contributor.contribute(facts, draft);
                } catch (Exception e) {
                    // 单个贡献者失败只丢它那一栏，整行照落
                    log.warn("装配单贡献者失败（该栏留空）: contributor={}, session={}, error={}",
                            contributor.getClass().getSimpleName(), sessionId, e.getMessage());
                }
            }
            AgentManifest row = toRow(facts, draft);
            if (repo.findBySession(sessionId) == null) {
                repo.save(row);
            } else {
                repo.overwriteBySession(row);
            }
            log.info("会话装配单落库: session={} agent={} 工具={} 只, 技能={} 个, 段={} 个",
                    sessionId, facts.getAgentKey(), size(row.getToolCount()),
                    draft.getSkills().size(), draft.getSections().size());
        } catch (Exception e) {
            log.warn("会话装配单落库失败（不影响会话）: session={}, error={}", sessionId, e.getMessage());
        }
    }

    /** 负责本 agent 的贡献者（agentKeys 空清单＝对所有 agent 生效） */
    private List<AgentManifestContributor> contributingFor(String agentKey) {
        List<AgentManifestContributor> matched = new ArrayList<>();
        for (AgentManifestContributor contributor : contributors) {
            List<String> keys = contributor.agentKeys();
            if (keys == null || keys.isEmpty() || keys.contains(agentKey)) {
                matched.add(contributor);
            }
        }
        return matched;
    }

    private static int size(Integer value) {
        return value == null ? 0 : value;
    }

    /** 事实 + 草稿 → 落库行（三栏 JSON 序列化失败留 NULL，不整行放弃） */
    static AgentManifest toRow(ToolSurfaceFacts facts, AgentManifestDraft draft) {
        AgentManifest row = new AgentManifest();
        row.setSessionId(facts.getSessionId());
        row.setAgentKey(facts.getAgentKey());
        row.setDisplayName(facts.getDisplayName());
        row.setAskedBy(facts.getOperator());
        List<String> toolNames = facts.getToolNames() == null ? new ArrayList<>() : facts.getToolNames();
        row.setToolNames(json(toolNames));
        row.setToolCount(toolNames.size());
        row.setModelTier(draft.getModelTier());
        row.setSkillRefs(json(draft.getSkills()));
        row.setPromptSections(json(draft.getSections()));
        row.setPromptText(truncatePrompt(facts.getPromptText()));
        row.setExtras(draft.getExtras().isEmpty() ? null : json(draft.getExtras()));
        row.setValid("1");
        return row;
    }

    /**
     * 对话定位落列（M14 棒2 增量）：收口时框架/业务广播 sessionId→conversationId，
     * 这里只给已存在的行补那一列。<b>不新建行</b>——装配单的行的定义是"工具面定形过"，
     * 没有那场定形记录就凭一条对话凭空造一行，会让"最近装配单"列表混进半空行。
     * 异常一律吞掉：定位写不进最多是页面少一个入口，绝不击穿对话归档
     */
    @Override
    public void onConversationLinked(String sessionId, String conversationId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(conversationId)) {
            return;
        }
        try {
            repo.linkConversation(sessionId.trim(), conversationId.trim());
        } catch (Exception e) {
            log.warn("装配单对话定位补写失败（不影响对话）: session={}, error={}", sessionId, e.getMessage());
        }
    }

    /** 超长截尾（留显式标记，让读的人知道看到的是半份）；空/空白按 NULL 存＝"没有 system 槽"。
     *  不 trim：这一列的卖点是"当时那份逐字"，改一个空格就没法跟模型收到的比对了 */
    static String truncatePrompt(String promptText) {
        if (!StringUtils.hasText(promptText)) {
            return null;
        }
        if (promptText.length() <= PROMPT_TEXT_MAX_CHARS) {
            return promptText;
        }
        return promptText.substring(0, PROMPT_TEXT_MAX_CHARS)
                + "\n……（原文 " + promptText.length() + " 字，已截尾）";
    }

    private static String json(Object value) {
        try {
            return OM.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("装配单 JSON 列序列化失败（该栏留空）: error={}", e.getMessage());
            return null;
        }
    }
}
