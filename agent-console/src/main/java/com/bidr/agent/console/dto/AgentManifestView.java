package com.bidr.agent.console.dto;

import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.manifest.AgentManifestSkillRef;
import com.bidr.llm.agent.conversation.AgentConversation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AgentManifestView
 * Description: 装配单读视图（M14 棒2）：把三个 JSON 列解回结构化清单给前端。
 * 解不动的列一律按空处理并记 warn——诊断页宁可少显示一栏，也不能因为一栏脏了整场会话读不出来。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@ApiModel(description = "agent 会话装配单读视图")
@Data
@Slf4j
public class AgentManifestView {

    private static final ObjectMapper OM = new ObjectMapper();

    @ApiModelProperty(value = "框架会话标识")
    private String sessionId;

    @ApiModelProperty(value = "agent 注册键")
    private String agentKey;

    @ApiModelProperty(value = "agent 展示名")
    private String displayName;

    @ApiModelProperty(value = "发起人")
    private String askedBy;

    @ApiModelProperty(value = "工具面定形时刻")
    private Date createAt;

    @ApiModelProperty(value = "工具名清单（＝发给模型的那份）")
    private List<String> toolNames = new ArrayList<>();

    @ApiModelProperty(value = "工具只数")
    private Integer toolCount;

    @ApiModelProperty(value = "模型档（业务贡献者未交则 null）")
    private String modelTier;

    @ApiModelProperty(value = "注入的技能包与版本")
    private List<AgentManifestSkillRef> skills = new ArrayList<>();

    @ApiModelProperty(value = "提示词段清单（按拼装顺序）")
    private List<String> promptSections = new ArrayList<>();

    /**
     * 提示词全文（M14 棒2 增量）：<b>默认不下发</b>——它是行内最重的一列，且是"当时那份指令"的原文。
     * 仅当读口带 {@code include=prompt} 且调用人有权展开（发起人本人／平台管理员）时由端点填入
     */
    @ApiModelProperty(value = "提示词全文（默认 null；按 include=prompt 展开才有值）")
    private String promptText;

    /** 有没有全文可展开（不拉全文也能知道"这行存了"，避免前端为了判断先发一次大请求） */
    @ApiModelProperty(value = "该行是否存了提示词全文（false＝会话在无 system 槽下发起、或早于棒 2 增量）")
    private boolean hasPromptText;

    @ApiModelProperty(value = "历史对话定位（NULL＝该会话没接对话层；有值但读不回正文＝已过保留期）")
    private String conversationId;

    /**
     * 对话正文（按 {@code include=conversation} 且有权展开时填入）：走框架通用对话层现读 Redis，
     * 过期即 null——页面据此给"已过保留期"而不是"没有对话"
     */
    @ApiModelProperty(value = "对话正文（默认 null；按 include=conversation 展开才有值，原文过期亦 null）")
    private AgentConversation conversation;

    @ApiModelProperty(value = "业务标注（featureId/spaceId 等）")
    private Map<String, String> extras = new LinkedHashMap<>();

    /** 落库行 → 读视图（null 入参返回 null，由端点渲染成"没有这场的装配单"）；重载荷列不在此填 */
    public static AgentManifestView of(AgentManifest row) {
        if (row == null) {
            return null;
        }
        AgentManifestView view = new AgentManifestView();
        view.setSessionId(row.getSessionId());
        view.setAgentKey(row.getAgentKey());
        view.setDisplayName(row.getDisplayName());
        view.setAskedBy(row.getAskedBy());
        view.setCreateAt(row.getCreateAt());
        view.setToolCount(row.getToolCount());
        view.setModelTier(row.getModelTier());
        view.setConversationId(row.getConversationId());
        view.setHasPromptText(StringUtils.hasText(row.getPromptText()));
        view.setToolNames(read(row.getToolNames(), new TypeReference<List<String>>() {
        }, new ArrayList<>()));
        view.setSkills(read(row.getSkillRefs(), new TypeReference<List<AgentManifestSkillRef>>() {
        }, new ArrayList<>()));
        view.setPromptSections(read(row.getPromptSections(), new TypeReference<List<String>>() {
        }, new ArrayList<>()));
        view.setExtras(read(row.getExtras(), new TypeReference<Map<String, String>>() {
        }, new LinkedHashMap<>()));
        return view;
    }

    private static <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.trim().isEmpty()) {
            return fallback;
        }
        try {
            return OM.readValue(json, type);
        } catch (Exception e) {
            log.warn("装配单 JSON 列解析失败（该栏按空处理）: error={}", e.getMessage());
            return fallback;
        }
    }
}
