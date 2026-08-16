package com.bidr.insight.flow;

import com.bidr.llm.flow.FlowDefinitionProvider;
import com.bidr.llm.flow.FlowGraph;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: ChatBiRouteFlowDefinition
 * Description: 看板路由链注册——route 链内置默认 DAG 的真源（"重置"即删库记录回到这里）：
 * start → route_catalog(看板目录) → llm(路由提示词，含当前看板/对话上下文段)
 * → extract(tableId) → output(tableId/portalName)。结点纵向单列布局（x 统一、y 间隔 150）——
 * 竖排结点框可放宽放大，比横排更易读。新增链的唯一途径 = 注册一个新的
 * {@link FlowDefinitionProvider} Bean（封闭集，前端画布仅编辑已注册链）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Component
public class ChatBiRouteFlowDefinition implements FlowDefinitionProvider {

    /**
     * 智能问数 skill 标识（工作台按 skill 聚合链清单/轨迹/评价）
     */
    public static final String SKILL_CODE = "chatbi";

    /**
     * 看板路由链标识
     */
    public static final String FLOW_KEY = "route";

    @Override
    public String skillCode() {
        return SKILL_CODE;
    }

    @Override
    public String flowKey() {
        return FLOW_KEY;
    }

    @Override
    public String displayName() {
        return "看板路由链";
    }

    /**
     * 每次调用构造新实例（graph 可变且会被执行上下文/管理页持有，禁共享防并发污染）
     */
    @Override
    public FlowGraph defaultGraph() {
        FlowGraph graph = new FlowGraph();
        graph.getNodes().add(node("start", "start", "开始", 60d, 0d));
        graph.getNodes().add(node("route_catalog", "route_catalog", "看板目录", 60d, 150d));

        FlowGraph.FlowNode llm = node("llm_route", "llm", "LLM 选板", 60d, 300d);
        llm.getConfig().put("role", "user");
        llm.getConfig().put("template", routePromptTemplate());
        graph.getNodes().add(llm);

        FlowGraph.FlowNode extract = node("extract_route", "extract", "提取 tableId", 60d, 450d);
        extract.getConfig().put("mode", "tableId");
        graph.getNodes().add(extract);

        FlowGraph.FlowNode output = node("output_route", "output", "输出", 60d, 600d);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("tableId", "tableId");
        outputs.put("portalName", "portalName");
        output.getConfig().put("outputs", outputs);
        graph.getNodes().add(output);

        link(graph, "start", "route_catalog", "llm_route", "extract_route", "output_route");
        return graph;
    }

    /**
     * 路由提示词：候选清单 + 当前看板 + 对话上下文（每次提问结合上下文重新路由），NONE 兜底
     */
    private static String routePromptTemplate() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ERP 数据平台的智能问数路由器，任务：根据用户问题与对话上下文，从候选看板目录中选出最能回答该问题的一个看板。\n\n");
        prompt.append("【候选看板目录】\n");
        prompt.append("{{routeCatalog}}\n");
        prompt.append("【当前看板】{{currentTableId}}\n\n");
        prompt.append("【对话上下文】（最近对话，用于理解指代与话题延续）\n");
        prompt.append("{{history}}\n\n");
        prompt.append("【输出要求】\n");
        prompt.append("- 只输出最相关看板的 tableId，单独一行，不带任何解释、标点或代码块标记；\n");
        prompt.append("- 对话上下文连续、且当前看板能继续回答时，优先输出当前看板的 tableId；\n");
        prompt.append("- 没有看板能回答该问题时只输出：NONE。\n\n");
        prompt.append("【用户问题】\n");
        prompt.append("{{question}}");
        return prompt.toString();
    }

    private static FlowGraph.FlowNode node(String id, String type, String name, double x, double y) {
        FlowGraph.FlowNode node = new FlowGraph.FlowNode();
        node.setId(id);
        node.setType(type);
        node.setName(name);
        node.setX(x);
        node.setY(y);
        return node;
    }

    /**
     * 顺序连边（默认链均为无条件直连）
     */
    private static void link(FlowGraph graph, String... nodeIds) {
        for (int i = 0; i < nodeIds.length - 1; i++) {
            FlowGraph.FlowEdge edge = new FlowGraph.FlowEdge();
            edge.setSource(nodeIds[i]);
            edge.setTarget(nodeIds[i + 1]);
            graph.getEdges().add(edge);
        }
    }
}
