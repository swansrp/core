package com.bidr.insight.flow;

import com.bidr.insight.service.ChatBiConversationService;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowExecutionListener;
import com.bidr.llm.sse.FlowSseSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Title: ChatBiFlowConversationListener
 * Description: 链路收口接入——llm flow 引擎 onFinish 回调在此补写历史对话助手回复
 * （成功取 output 的 answer/chartSpec，失败记错误信息）：conversationId 为空（route 链/未开启持久化）
 * 直接跳过；extract 结点已补写（{@link #CONVERSATION_APPENDED}）同样跳过；补写成功经 msgid 事件
 * 回传消息标识（失败回复也可评价，运营侧收集失败反馈）；异常吞掉不影响主流程收口。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBiFlowConversationListener implements FlowExecutionListener {

    /**
     * extract 结点补写历史后置位（收口回调据此跳过重复补写）——业务标记本地化，与引擎解耦
     */
    public static final String CONVERSATION_APPENDED = "__conversation_appended";

    private final ChatBiConversationService conversationService;

    @Override
    public void onFinish(FlowContext context, String error) {
        String conversationId = context.getConversationId();
        if (!StringUtils.hasText(conversationId)
                || Boolean.TRUE.equals(context.getVariable(CONVERSATION_APPENDED))) {
            return;
        }
        try {
            String messageId;
            if (error != null) {
                messageId = conversationService.appendAssistant(conversationId, error, null,
                        context.getString("tableId"), context.getString("portalName"), "error");
            } else {
                Map<String, Object> output = context.getOutput();
                Object answer = output.get("answer");
                messageId = conversationService.appendAssistant(conversationId,
                        answer == null ? "" : String.valueOf(answer), output.get("chartSpec"),
                        context.getString("tableId"), context.getString("portalName"), "done");
            }
            FlowSseSender sender = context.getSseSender();
            if (messageId != null && sender != null) {
                sender.send(FlowSseSender.EVENT_MSGID, messageId);
            }
        } catch (Exception e) {
            log.warn("补写历史对话助手回复失败, conversationId={}, error={}", conversationId, e.getMessage());
        }
    }
}
