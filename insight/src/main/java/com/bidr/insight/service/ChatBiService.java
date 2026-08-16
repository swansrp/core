package com.bidr.insight.service;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.insight.flow.ChatBiAskFlowDefinition;
import com.bidr.insight.vo.ChatBiAskReq;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowEngine;
import com.bidr.llm.sse.FlowSseSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Title: ChatBiService
 * Description: 智能问数编排入口——组装执行上下文后交 {@link FlowEngine} 跑 ask 链
 * （默认：semantic 语义目录 → llm 流式问答 → extract chart-spec 收尾），
 * 提示词模板与链路结构存库可经管理页（#/insight/chatbi/config）编辑。
 * <p>
 * 模型只输出轻量 chart-spec 指令（指标引用 + 补丁 + 表格条件），
 * 完整图表配置由前端合并语义层得到，后端不感知渲染细节。
 * </p>
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBiService {

    /**
     * 前端编排 systemPrompt 的长度上限，防透传接口被刷 token
     */
    private static final int MAX_FRONTEND_PROMPT_CHARS = 64_000;

    /**
     * 运营扩展段（insight.chatbi.prompt-extra）：别名映射/业务口径/few-shot 示例等，
     * 以 promptExtra 变量注入 start 结点，llm 模板尾部 {@code {{promptExtra}}} 引用，无需改码即可调优
     */
    @Value("${insight.chatbi.prompt-extra:}")
    private String promptExtra;

    private final FlowEngine flowEngine;

    private final ChatBiConversationService chatBiConversationService;

    private final ChatBiSensitiveService chatBiSensitiveService;

    /**
     * 发起智能问数：注入输入变量后执行 ask 链，delta/spec/done/error 事件由链上结点直推 SSE；
     * 发起即失败（透传提示词超长/模型缺失等）时以 error 事件结束连接。
     * 提问即写历史对话（新对话在此创建，id 经 conv 事件回传），助手回复由引擎链路收口时补写
     *
     * @param req     提问请求（tableId + question + 最近对话 + conversationId 续接）
     * @param emitter 前端持有的 SSE 连接
     */
    public void ask(ChatBiAskReq req, SseEmitter emitter) {
        FlowSseSender sender = new FlowSseSender(emitter);
        String conversationId = null;
        try {
            FlowContext context = new FlowContext(sender);
            context.setVariable("question", req.getQuestion());
            context.setVariable("tableId", req.getTableId());
            context.setVariable("portalName", req.getPortalName());
            context.setHistory(req.getHistory());
            // 访问人（人名优先回落工号，轨迹/对话按人隔离记录；SSE 挂起后回调线程读不到 AccountContext，须在发起线程先取）
            String operator = AccountContext.getDisplayName();
            context.setOperator(operator);
            // 前端透传后门：systemPrompt 变量有值时 llm 结点（templateVar）优先取它作模板全文
            context.setVariable("systemPrompt", resolveFrontendPrompt(req));
            // 模板尾部无前缀，注入时补齐原文案"【补充约定】"段落；敏感字段约定段随后拼接（该板有敏感配置时非空）
            String extra = StringUtils.hasText(promptExtra) ? "\n\n【补充约定】\n" + promptExtra.trim() : "";
            context.setVariable("promptExtra", extra + chatBiSensitiveService.buildSensitivePromptNote(req.getTableId()));
            // 历史对话：提问即存（conversationId 空=新对话在此创建），助手回复由引擎收口补写
            conversationId = chatBiConversationService.appendUser(
                    req.getConversationId(), operator, req.getQuestion(), req.getTableId(), req.getPortalName());
            context.setConversationId(conversationId);
            // conv 事件先于 delta 下发：前端拿到对话 id 后续提问续接同一对话
            sender.send(FlowSseSender.EVENT_CONV, conversationId);
            flowEngine.execute(ChatBiAskFlowDefinition.FLOW_KEY, context);
        } catch (Exception e) {
            log.warn("智能问数发起失败, tableId={}", req.getTableId(), e);
            String message = e.getMessage() == null ? "智能问数发起失败" : e.getMessage();
            // 发起即失败：对话已创建则补写一条错误回复收口，避免停在只有提问的半轮；msgid 先于 error 下发供评价定位
            if (conversationId != null) {
                String messageId = chatBiConversationService.appendAssistant(conversationId, message, null,
                        req.getTableId(), req.getPortalName(), "error");
                if (messageId != null) {
                    sender.send(FlowSseSender.EVENT_MSGID, messageId);
                }
            }
            sender.send(FlowSseSender.EVENT_ERROR, message);
            sender.complete();
        }
    }

    /**
     * 前端透传提示词（管理页之外的调试后门，低成本向后兼容）：有值时校验长度后返回，否则 null 走库中模板
     */
    private String resolveFrontendPrompt(ChatBiAskReq req) {
        String frontendPrompt = req.getSystemPrompt();
        if (!StringUtils.hasText(frontendPrompt)) {
            return null;
        }
        if (frontendPrompt.length() > MAX_FRONTEND_PROMPT_CHARS) {
            throw new IllegalArgumentException(
                    "systemPrompt 超长：" + frontendPrompt.length() + " 字符，上限 " + MAX_FRONTEND_PROMPT_CHARS);
        }
        log.debug("chatbi 前端编排 systemPrompt, tableId={}, length={}", req.getTableId(), frontendPrompt.length());
        return frontendPrompt;
    }
}
