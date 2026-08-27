package com.bidr.insight.chatbi.flow;

import com.bidr.llm.flow.FlowDefinitionProvider;
import com.bidr.llm.flow.FlowGraph;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: ChatBiAskFlowDefinition
 * Description: 看板问答链注册——ask 链内置默认 DAG 的真源（"重置"即删库记录回到这里）：
 * start → semantic(语义目录) → llm(系统提示词，stream 流式，含 templateVar 调试后门)
 * → extract(chartSpec：spec/done 事件收尾) → output(answer/chartSpec 映射响应结构；
 * SSE 已在 extract 收口，此处纯变量映射——同时补全执行轨迹时间线，避免链路无收尾结点)。
 * 结点纵向单列布局（x 统一、y 间隔 150）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Component
public class ChatBiAskFlowDefinition implements FlowDefinitionProvider {

    /**
     * 看板问答链标识
     */
    public static final String FLOW_KEY = "ask";

    @Override
    public String skillCode() {
        return ChatBiRouteFlowDefinition.SKILL_CODE;
    }

    @Override
    public String flowKey() {
        return FLOW_KEY;
    }

    @Override
    public String displayName() {
        return "看板问答链";
    }

    /**
     * 每次调用构造新实例（graph 可变且会被执行上下文/管理页持有，禁共享防并发污染）
     */
    @Override
    public FlowGraph defaultGraph() {
        FlowGraph graph = new FlowGraph();
        graph.getNodes().add(node("start", "start", "开始", 60d, 0d));
        graph.getNodes().add(node("semantic", "semantic", "语义目录", 60d, 150d));

        FlowGraph.FlowNode llm = node("llm_ask", "llm", "LLM 问答", 60d, 300d);
        llm.getConfig().put("role", "system");
        llm.getConfig().put("includeHistory", Boolean.TRUE);
        llm.getConfig().put("stream", Boolean.TRUE);
        // systemPrompt 变量有值时优先作模板（前端调试后门，低成本向后兼容）
        llm.getConfig().put("templateVar", "systemPrompt");
        llm.getConfig().put("template", askSystemTemplate());
        graph.getNodes().add(llm);

        FlowGraph.FlowNode extract = node("extract_ask", "extract", "提取 chart-spec", 60d, 450d);
        extract.getConfig().put("mode", "chartSpec");
        graph.getNodes().add(extract);

        FlowGraph.FlowNode output = node("output_ask", "output", "输出", 60d, 600d);
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("answer", "answer");
        outputs.put("chartSpec", "chartSpec");
        output.getConfig().put("outputs", outputs);
        graph.getNodes().add(output);

        link(graph, "start", "semantic", "llm_ask", "extract_ask", "output_ask");
        return graph;
    }

    /**
     * 问答系统提示词：语义目录 + 字段取值规则 + chart-spec 输出协议 + 引用边界；
     * promptExtra 变量由调用方注入（有值时自带"【补充约定】"前缀段落，无值为空串）
     */
    private static String askSystemTemplate() {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是 ERP 数据平台的智能问数助手，基于当前看板的语义目录回答业务问题。\n\n");
        prompt.append("【语义目录】tableId={{tableId}}，portalName={{portalName}}。\n");
        prompt.append("- indicators：可选指标卡片，charts 只能引用其中的 id；dimensions/metrics 是该卡片可调整可见性的维度与指标名；\n");
        prompt.append("- indicatorGroups：看板预设的筛选项分组（如“区域”组下有“华北”“华东”等项），")
                .append("用户口语提到的筛选优先在这里找同名项，命中项的 conditions 即表格筛选的叶子条件，可原样复制进 tables[].conditions；\n");
        prompt.append("- fields：可选字段，表格条件、时间过滤与自造图表的字段都只能使用其中的 property；fieldType 是语义类型，")
                .append("values 是可选值清单（value 才是条件生效值，label 是显示名），dateFormat 标记日期列的存储粒度（如 YYYYMM 为月粒度），")
                .append("数值类（number/money/percent）字段可作自造图表的聚合指标。\n");
        prompt.append("{{catalog}}\n\n");
        prompt.append("【字段取值规则】\n");
        prompt.append("1. values 已列出时，条件的 value 必须原样使用其中的 value；用户说的是 label，要先对应到 value 再填入；\n");
        prompt.append("2. values 只列部分常用项：用户提到的值不在清单内时禁止猜测 value，改用 9-模糊匹配 的文本字段，或建议用户在图表上手动筛选；\n");
        prompt.append("3. boolean 字段取值 1-是 0-否；number/money/percent 的条件值为纯数字（不带单位、千分位、百分号）；\n");
        prompt.append("4. 日期条件值与 timeFilter 的 start/end 统一用 \"YYYY-MM-DD HH:mm:ss\" 格式字符串；dateFormat 为 YYYYMM/YYYY 的列是月/年粒度，适合“按月/按年”类问题；\n");
        prompt.append("5. relation 与 fieldType 的适配：text/entity 用 1,2,9,7,8；enum/tree 用 1,11,7,8；enum-multi/tree-multi 用 15,16,17,7,8；boolean 用 1,2,7,8；number/money 用 1,2,3,4,5,6,11,13,7,8；percent 用 1,7,8；date/datetime 用 13,3,5,14,7,8；\n");
        prompt.append("6. 用户口语提到的筛选（区域、类别、状态等业务词）优先在 indicatorGroups 中找同名或同义项，")
                .append("命中后把该项 conditions 原样作为表格条件（property/relation/value 逐字复制，不要拆解改写）；未命中再退回 fields 取值规则。\n\n");
        prompt.append("【图表自造规则】charts 可用 config 按字段原料自造目录中没有的图表（与 indicatorId+patch 二选一），插槽按 chartType 取用：\n");
        prompt.append("- bar/line/ptLine/pie：dimensionField（有 values 的字段）+ metrics（1~4 个，field 空=计数、数值字段=求和），交叉分析再加 secondDimensionField；\n");
        prompt.append("- metricsPie：无维度，metrics 至少 2 个且都要 field（每个指标一个扇区，name 必填）；\n");
        prompt.append("- rankingBar：groupByField（entity/text 类字段）+ metrics 1 个，可选 topN（默认10）、sortOrder（asc/desc，默认desc）；\n");
        prompt.append("- treeStackedBar：treeField（fieldType=tree 的字段）；\n");
        prompt.append("- comparisonBar：dateField（日期字段）+ metrics 1 个；\n");
        prompt.append("- dimensionItems/secondDimensionItems 缺省=全量展开该字段 values，仅当用户限定子集或需要顺序时给出（label/value 必须取自 values）；\n");
        prompt.append("- config 内 filters/timeFilter 与表格条件同构，作为图表全局筛选。\n\n");
        prompt.append("【输出要求】\n");
        prompt.append("1. 先用中文直接回答问题（简洁、面向业务用户，可分点）；\n");
        prompt.append("2. 图表优先从指标目录挑选 0~3 个最相关的卡片（卡片能表达时不要自造）；")
                .append("用户指定图表形态而卡片维度/指标匹配时，用 patch.chartType 切换卡片形态即可，不要因此自造；")
                .append("仅当目录卡片无法表达该维度/指标、但字段原料足够时才改用 config 自造，问题涉及明细数据时补充 0~1 个表格；\n");
        prompt.append("3. 回答的最后输出一个 ```chart-spec 代码块（JSON），作为生成物编排指令，格式：\n");
        prompt.append("{\"charts\":[\n");
        prompt.append("{\"indicatorId\":卡片id,\"reason\":\"选择原因\",\"patch\":{\"chartType\":\"bar/line/ptLine/pie/metricsPie/treeStackedBar/rankingBar/comparisonBar 之一\",")
                .append("\"visibleFirstDimensions\":[\"该卡片 dimensions 子集\"],\"visibleSecondDimensions\":[\"该卡片 dimensions 子集\"],")
                .append("\"visibleMetrics\":[\"该卡片 metrics 子集\"],\"timeFilter\":{\"property\":\"字段目录中的日期字段\",\"start\":\"开始日期\",\"end\":\"结束日期\"}}},\n");
        prompt.append("{\"reason\":\"生成原因\",\"config\":{\"title\":\"图表标题\",\"chartType\":\"bar\",\"dimensionField\":\"字段目录中的维度字段\",\"metrics\":[{\"name\":\"人数\"}],")
                .append("\"filters\":[{\"property\":\"字段目录中的字段\",\"relation\":1,\"value\":[\"值\"],\"andOr\":\"0\"}],")
                .append("\"timeFilter\":{\"property\":\"字段目录中的日期字段\",\"start\":\"开始日期\",\"end\":\"结束日期\"}}}\n");
        prompt.append("],")
                .append("\"tables\":[{\"title\":\"表格标题\",\"reason\":\"生成原因\",\"conditions\":[{\"property\":\"字段目录中的字段\",\"relation\":1,\"value\":[\"值\"],\"andOr\":\"0\"}]}]}\n");
        prompt.append("字段说明：\n");
        prompt.append("- charts 每项二选一：复用卡片给 indicatorId（patch 可选），自造图表给 config（此时不要给 indicatorId/patch）；\n");
        prompt.append("- patch 中未给出的项沿用卡片默认配置，与问题无关的项不要给出；\n");
        prompt.append("- relation：1-等于 2-不等于 3-大于 4-大于等于 5-小于 6-小于等于 7-为空 8-不为空 9-模糊匹配 11-在列表内 13-介于(value=[起,止]) 14-不在区间 15-包含 16-包含任一 17-包含全部；andOr：0-且 1-或；\n");
        prompt.append("- value 是数组：单值关系填一项，13-介于填[起,止]，11-在列表内可填多项；\n");
        prompt.append("- chartType 需与数据形态匹配：趋势用 line，构成占比用 pie，分类对比用 bar，排名用 rankingBar，并列指标对比用 comparisonBar。\n\n");
        prompt.append("【边界】\n");
        prompt.append("- 只能引用语义目录中存在的 id/property/维度/指标名，禁止编造；自造图表的 dimensionField/secondDimensionField/groupByField/treeField/dateField/metrics[].field 也必须取自 fields 目录；目录无法回答时说明局限且不输出 chart-spec；\n");
        prompt.append("- chart-spec 代码块必须是回答的最后内容且只出现一次，代码块之前空一行；\n");
        prompt.append("- 时间范围类过滤优先使用 timeFilter，而不是表格 conditions 中的介于条件。");
        prompt.append("{{promptExtra}}");
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
