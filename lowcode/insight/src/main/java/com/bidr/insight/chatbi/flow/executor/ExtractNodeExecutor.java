package com.bidr.insight.chatbi.flow.executor;

import com.bidr.insight.chatbi.flow.ChatBiFlowConversationListener;
import com.bidr.insight.chatbi.service.ChatBiConversationService;
import com.bidr.insight.chatbi.service.ChatBiSensitiveService;
import com.bidr.insight.chatbi.sse.ChartSpecExtractor;
import com.bidr.insight.chatbi.sse.ChartSpecSensitiveGuard;
import com.bidr.insight.chatbi.vo.ChatBiRouteItem;
import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowGraph;
import com.bidr.llm.flow.FlowNodeMeta;
import com.bidr.llm.flow.FlowNodeMeta.ConfigField;
import com.bidr.llm.flow.executor.FlowNodeExecutor;
import com.bidr.llm.sse.SseEventSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Title: ExtractNodeExecutor
 * Description: extract 结点——从 LLM 输出中提取结构化结果，两种模式：
 * <ul>
 *     <li>mode=tableId（route 链）：对 routeCatalogItems 做精确→包含两级匹配，
 *         命中写 tableId/portalName 变量（未命中均写 null，供条件边与 output 判别）；</li>
 *     <li>mode=chartSpec（ask 链，默认）：提取 {@code ```chart-spec} JSON——合法则发 spec 事件，
 *         剔除代码块后的正文发 done 事件并写入 answer 变量，随后关闭 SSE 连接。</li>
 * </ul>
 * config：inputVar（默认 llmAnswer）、mode。
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExtractNodeExecutor implements FlowNodeExecutor {

    private final ChatBiConversationService conversationService;

    private final ChatBiSensitiveService chatBiSensitiveService;

    @Override
    public String type() {
        return "extract";
    }

    /**
     * 工作台元数据：模式与输入变量（defaultValue 即画布新增结点的初始 config）
     */
    @Override
    public FlowNodeMeta nodeMeta() {
        FlowNodeMeta meta = FlowNodeMeta.of(type(), type(), "从模型回答提取 tableId 或 chart-spec");
        meta.setHint("tableId 模式按 route_catalog 注入的候选目录精确匹配 tableId=xx 并写回 tableId/portalName；"
                + "chartSpec 模式提取成功即下发 spec 事件并结束 SSE。");
        meta.setFields(Arrays.asList(
                ConfigField.select("mode", "提取模式",
                                new FlowNodeMeta.Option("chartSpec", "chartSpec（从回答提取 ```chart-spec）"),
                                new FlowNodeMeta.Option("tableId", "tableId（从回答提取看板编号）"))
                        .defaultValue("chartSpec"),
                ConfigField.text("inputVar", "输入变量名", "默认 answer").defaultValue("answer")
        ));
        return meta;
    }

    @Override
    public boolean execute(FlowGraph.FlowNode node, FlowContext context) {
        Map<String, Object> config = node.getConfig();
        String mode = strConfig(config, "mode", "chartSpec");
        Object input = context.getVariable(strConfig(config, "inputVar", "llmAnswer"));
        String text = input == null ? "" : String.valueOf(input);
        // 轨迹调试变量：输入文本全文（LLM 原始输出）
        context.setVariable(FlowContext.TRACE_EXTRACT_INPUT, text);
        if ("tableId".equals(mode)) {
            extractTableId(text, context);
        } else {
            extractChartSpec(text, context);
        }
        return true;
    }

    /**
     * 轨迹摘要：直接复用提取结果摘要（tableId 模式=命中结果，chartSpec 模式=提取结论）
     */
    @Override
    public String traceSummary(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        String result = context.getString(FlowContext.TRACE_EXTRACT_RESULT);
        return StringUtils.hasText(result) ? result : "执行完成";
    }

    /**
     * 轨迹全文：输入（LLM 原始输出）与提取结果对照
     */
    @Override
    public String traceDetail(FlowGraph.FlowNode node, FlowContext context, boolean suspended) {
        String input = context.getString(FlowContext.TRACE_EXTRACT_INPUT);
        String result = context.getString(FlowContext.TRACE_EXTRACT_RESULT);
        if (!StringUtils.hasText(input) && !StringUtils.hasText(result)) {
            return null;
        }
        return "【输入】" + input + "\n【结果】" + result;
    }

    /**
     * 埋点读取后清除调试变量，避免全文残留变量池
     */
    @Override
    public void clearTraceVars(FlowContext context) {
        context.removeVariable(FlowContext.TRACE_EXTRACT_INPUT);
        context.removeVariable(FlowContext.TRACE_EXTRACT_RESULT);
    }

    /**
     * tableId 两级匹配（整串精确→包含兜底），命中回填 portalName，未命中两个变量均置 null
     */
    private void extractTableId(String answer, FlowContext context) {
        List<?> catalog = context.get("routeCatalogItems", List.class);
        String tableId = matchTableId(answer, catalog);
        String portalName = null;
        if (tableId != null) {
            for (Object item : catalog) {
                if (item instanceof ChatBiRouteItem && tableId.equals(((ChatBiRouteItem) item).getTableId())) {
                    portalName = ((ChatBiRouteItem) item).getPortalName();
                    break;
                }
            }
        } else {
            log.warn("智能问数路由未命中, answer={}", answer);
        }
        context.setVariable("tableId", tableId);
        context.setVariable("portalName", portalName);
        // 轨迹调试变量：提取结果摘要
        context.setVariable(FlowContext.TRACE_EXTRACT_RESULT,
                tableId == null ? "未命中（tableId=null）" : "tableId=" + tableId + "（" + portalName + "）");
    }

    /**
     * 先整串精确匹配，再包含匹配兜底（模型偶带解释文本），均不中视为路由失败
     */
    private String matchTableId(String answer, List<?> catalog) {
        if (!StringUtils.hasText(answer) || catalog == null) {
            return null;
        }
        String text = answer.trim();
        for (Object item : catalog) {
            if (item instanceof ChatBiRouteItem && text.equals(((ChatBiRouteItem) item).getTableId())) {
                return ((ChatBiRouteItem) item).getTableId();
            }
        }
        for (Object item : catalog) {
            if (item instanceof ChatBiRouteItem && text.contains(((ChatBiRouteItem) item).getTableId())) {
                return ((ChatBiRouteItem) item).getTableId();
            }
        }
        return null;
    }

    /**
     * chart-spec 提取下发：spec（合法 JSON 时）→ msgid（助手回复补写后回传消息标识，前端评价按它定位）
     * → done（剔除代码块后的正文）→ 关闭连接。
     * 补写从引擎收口前移到这里：msgid 需在 done 之前送达，且 complete 后写入会被断连保护吞掉
     */
    private void extractChartSpec(String fullContent, FlowContext context) {
        SseEventSender sender = context.getSseSender();
        if (sender == null) {
            throw new IllegalStateException("mode=chartSpec 的 extract 结点需要 SSE 上下文（ask 链路）");
        }
        StringBuilder cleaned = new StringBuilder();
        String specJson = ChartSpecExtractor.extractSpecJson(fullContent, cleaned);
        Object chartSpec = null;
        if (specJson != null) {
            // 出向最后防线：语义目录已清空敏感值域，模型仍幻觉引用敏感列时剔除违规引用后再下发
            String guarded = ChartSpecSensitiveGuard.stripSensitiveReferences(
                    specJson, chatBiSensitiveService.getSensitiveReplaceMap(context.getString("tableId")));
            boolean stripped = !guarded.equals(specJson);
            specJson = guarded;
            context.setVariable("chartSpec", specJson);
            // 轨迹调试变量：提取结果摘要
            context.setVariable(FlowContext.TRACE_EXTRACT_RESULT, "chartSpec 提取成功（" + specJson.length()
                    + " 字" + (stripped ? "，敏感列引用已剔除" : "") + "）");
            sender.send(SseEventSender.EVENT_SPEC, specJson);
            chartSpec = specJson;
        } else {
            log.warn("chatbi 回答中未提取到有效的 chart-spec 代码块");
            context.setVariable(FlowContext.TRACE_EXTRACT_RESULT, "未提取到 chart-spec 代码块");
        }
        String answer = cleaned.toString().trim();
        context.setVariable("answer", answer);
        // 助手回复补写 + msgid 事件（conversationId 空即 route 链，appendAssistant 返回 null 不发）；
        // 置已补写标记，引擎 finishConversation 检测到后跳过，避免双写
        String messageId = conversationService.appendAssistant(context.getConversationId(), answer, chartSpec,
                context.getString("tableId"), context.getString("portalName"), "done");
        if (messageId != null) {
            context.setVariable(ChatBiFlowConversationListener.CONVERSATION_APPENDED, Boolean.TRUE);
            sender.send(SseEventSender.EVENT_MSGID, messageId);
        }
        sender.send(SseEventSender.EVENT_DONE, answer);
        sender.complete();
    }
}
