package com.bidr.llm.agent.conversation;

import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.agent.registry.AgentDescriptor;
import com.bidr.llm.agent.registry.AgentRegistryService;
import com.bidr.llm.skill.SkillRatingRecord;
import com.bidr.llm.skill.SkillRatingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: AgentConversationController
 * Description: agent 通用历史对话端点（llm 基础框架，业务零绑定）——列表（按访问人跨 agent 聚合，
 * 可选 agentCode 过滤）/ 详情 / 删除 / 评价（对话正文内嵌 + skill 评价底座双写，运营统计可筛）。
 * 访问人经 {@link OperatorResolver} SPI 注入（同 {@link com.bidr.llm.agent.AgentSessionController}），
 * 鉴权沿用平台 /web/api 拦截链，不加 @IgnoreAuth。
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Slf4j
@RestController
@RequestMapping("/web/api/agent")
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
@ConditionalOnProperty(prefix = "llm.agent-api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentConversationController {

    /** 评价快照保留天数（与对话同量级缺省；评价运营统计另有 skill 底座索引） */
    private static final int RATING_RETENTION_DAYS = 30;

    private final AgentConversationService agentConversationService;

    /** 注册中心（评价双写时按 agentCode 解析归属 skill） */
    private final AgentRegistryService agentRegistryService;

    /** skill 评价底座（运营统计；Redis 不可用时静默降级） */
    private final SkillRatingService skillRatingService;

    private final ObjectProvider<OperatorResolver> operatorResolver;

    /**
     * 历史对话列表（新→旧）：默认本人跨 agent 聚合（统一历史视图），
     * agentCode 非空时过滤单 agent（前端按 agent 页签展示）；
     * scope=all 时跨发起人聚合（管理类页面看全量，列表项带 operator 区分归属；
     * 删除/评价仍限本人对话，服务端越权拦截不变）
     */
    @GetMapping("/conversations")
    public List<AgentConversation> list(@RequestParam(name = "agentCode", required = false) String agentCode,
                                        @RequestParam(name = "scope", required = false) String scope) {
        return agentConversationService.listConversations(operator(), agentCode, "all".equalsIgnoreCase(scope));
    }

    /**
     * 对话详情（含全部消息与业务负载，前端恢复渲染用；不存在返回空响应体）
     */
    @GetMapping("/conversations/{conversationId}")
    public AgentConversation detail(@PathVariable("conversationId") String conversationId) {
        return agentConversationService.getConversation(conversationId);
    }

    /**
     * 删除历史对话（仅能删自己的）
     */
    @PostMapping("/conversations/{conversationId}/delete")
    public void delete(@PathVariable("conversationId") String conversationId) {
        agentConversationService.deleteConversation(conversationId, operator());
    }

    /**
     * 评价助手回答（like/dislike，空=取消）：对话正文内嵌（恢复回显）+ skill 评价底座双写
     * （运营统计）。skillCode 经注册中心按对话归属 agentCode 解析（未注册回落 agentCode 原样），
     * ratingId=conversationId:messageId，ext 携 agentCode 供筛选
     */
    @PostMapping("/conversations/{conversationId}/rate")
    public AgentConversation rate(@PathVariable("conversationId") String conversationId,
                                  @RequestBody ConversationRateReq req) {
        String messageId = req == null ? null : req.getMessageId();
        String rating = req == null || req.getRating() == null ? "" : req.getRating().trim();
        String operator = operator();
        AgentConversation conversation = agentConversationService.rateMessage(
                conversationId, messageId, StringUtils.hasText(rating) ? rating : null, operator);
        AgentConversationMessage target = locate(conversation, messageId);
        if (target != null) {
            String ratingId = conversation.getConversationId() + ":" + target.getMessageId();
            String skillCode = resolveSkillCode(conversation.getAgentCode());
            if (!StringUtils.hasText(rating)) {
                skillRatingService.remove(skillCode, ratingId);
            } else {
                SkillRatingRecord record = new SkillRatingRecord();
                record.setRatingId(ratingId);
                record.setConversationId(conversation.getConversationId());
                record.setMessageId(target.getMessageId());
                record.setOperator(conversation.getOperator());
                record.setQuestion(findQuestionBefore(conversation, target));
                record.setAnswer(target.getContent());
                record.setRating(rating);
                record.setMessageTime(target.getTime());
                record.setRatingTime(System.currentTimeMillis());
                record.getExt().put("agentCode", conversation.getAgentCode());
                skillRatingService.save(skillCode, record, RATING_RETENTION_DAYS);
            }
        }
        return conversation;
    }

    // ==================== 私有工具 ====================

    /**
     * 当前访问人（OperatorResolver SPI；无实现回落 null → 存储侧 anonymous）
     */
    private String operator() {
        OperatorResolver resolver = operatorResolver.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.currentOperator();
        } catch (Exception e) {
            log.warn("访问人解析失败，回落 anonymous, error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 按 agentCode 经注册中心解析归属 skill（未注册/无 skill 回落 agentCode 原样，评价仍可按其聚合）
     */
    private String resolveSkillCode(String agentCode) {
        if (!StringUtils.hasText(agentCode)) {
            return "unknown";
        }
        AgentDescriptor descriptor = agentRegistryService.get(agentCode);
        if (descriptor != null && StringUtils.hasText(descriptor.getSkillCode())) {
            return descriptor.getSkillCode();
        }
        return agentCode;
    }

    /**
     * 定位被评价消息（messageId 空=最后一条 assistant）
     */
    private AgentConversationMessage locate(AgentConversation conversation, String messageId) {
        for (int i = conversation.getMessages().size() - 1; i >= 0; i--) {
            AgentConversationMessage item = conversation.getMessages().get(i);
            if (!"assistant".equals(item.getRole())) {
                continue;
            }
            if (!StringUtils.hasText(messageId) || messageId.equals(item.getMessageId())) {
                return item;
            }
        }
        return null;
    }

    /**
     * 被评价回答前最近一条用户提问（评价快照的 question 维度）
     */
    private String findQuestionBefore(AgentConversation conversation, AgentConversationMessage target) {
        int index = conversation.getMessages().indexOf(target);
        for (int i = index - 1; i >= 0; i--) {
            AgentConversationMessage item = conversation.getMessages().get(i);
            if ("user".equals(item.getRole())) {
                return item.getContent();
            }
        }
        return conversation.getTitle();
    }

    /**
     * 对话评价请求：rating 空=取消；messageId 空=最后一条 assistant 回答
     */
    @Data
    public static class ConversationRateReq {

        /** 被评价消息标识（空=最后一条 assistant 回答） */
        private String messageId;

        /** 评价：like-点赞 / dislike-点踩 / 空-取消 */
        private String rating;
    }
}
