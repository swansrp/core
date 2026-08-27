package com.bidr.insight.smartquery.flow;

import com.bidr.llm.flow.FlowContext;
import com.bidr.llm.flow.FlowDefinitionProvider;
import com.bidr.llm.flow.FlowGraph;
import org.springframework.stereotype.Component;

/**
 * Title: AssetGenFlowDefinition
 * Description: 资产生成链注册（skill=smart-agent）——原写死大流程的 flow 化真源（"重置"即删库记录回到这里）：
 * start → sensitive_gate（实体确认+敏感治理强制闸）→ skeleton（逐表读结构+采样+骨架落盘）
 * → metrics → relations → concepts（LLM 逐类生成）→ finish（补齐落盘+失败汇总收口）。
 * 配对推断结点已下线：码值域改由骨架确定性派生（备注解析+同词干配对）+实体确认页人工补齐，
 * LLM 只产语义三类（旧库中已持久化的含 pair 编排需「重置」回本默认图）。
 * 三模式一条链：skeleton 模式经条件边 {@code mode == 'skeleton'} 从 skeleton 直通 finish
 * （enabled=false 不可用于运行期动态，统一条件边 {@code mode != 'skeleton'} 控制 LLM 段）；
 * autonomous 模式不经本链（B2 接 agent 会话层）。
 * 结点纵向单列布局；estimatedSecs 为各结点预计耗时秒数约定键（AgentStages 预计剩余时间用，可改）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Component
public class AssetGenFlowDefinition implements FlowDefinitionProvider {

    /** 归属 skill 标识（skill 管理平台索引维度） */
    public static final String SKILL_CODE = "smart-agent";

    /** 资产生成链标识 */
    public static final String FLOW_KEY = "asset-gen";

    /** FlowContext 变量：生成模式（skeleton/pipeline；条件边求值依据） */
    public static final String VAR_MODE = "mode";

    /** FlowContext 变量：任务上下文（GenTaskContext，执行器共享骨架容器/连接/失败清单） */
    public static final String VAR_GEN_CTX = "genCtx";

    /** FlowContext 变量：校验通过的选表清单（骨架结点逐表构建） */
    public static final String VAR_TABLES = "tables";

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
        return "资产生成链";
    }

    /**
     * 每次调用构造新实例（graph 可变且会被执行上下文/管理页持有，禁共享防并发污染）
     */
    @Override
    public FlowGraph defaultGraph() {
        FlowGraph graph = new FlowGraph();
        graph.getNodes().add(node("start", "start", "开始", 60d, 0d));
        graph.getNodes().add(node("gate_sensitive", "asset-sensitive-gate", "敏感闸", 60d, 150d));

        FlowGraph.FlowNode skeleton = node("gen_skeleton", "asset-skeleton", "骨架生成", 60d, 300d);
        skeleton.getConfig().put("estimatedSecs", 30);
        graph.getNodes().add(skeleton);

        FlowGraph.FlowNode metrics = node("gen_metrics", "asset-llm", "生成指标", 60d, 450d);
        metrics.getConfig().put("assetType", "metrics");
        metrics.getConfig().put("promptKey", "metricsPrompt");
        metrics.getConfig().put("estimatedSecs", 300);
        graph.getNodes().add(metrics);

        FlowGraph.FlowNode relations = node("gen_relations", "asset-llm", "生成关系", 60d, 750d);
        relations.getConfig().put("assetType", "relations");
        relations.getConfig().put("promptKey", "relationsPrompt");
        relations.getConfig().put("estimatedSecs", 180);
        graph.getNodes().add(relations);

        FlowGraph.FlowNode concepts = node("gen_concepts", "asset-llm", "生成概念", 60d, 900d);
        concepts.getConfig().put("assetType", "concepts");
        concepts.getConfig().put("promptKey", "conceptsPrompt");
        concepts.getConfig().put("estimatedSecs", 180);
        graph.getNodes().add(concepts);

        graph.getNodes().add(node("finish_save", "asset-finish", "落盘收口", 60d, 1050d));

        link(graph, "start", "gate_sensitive", "gen_skeleton");
        link(graph, "gen_metrics", "gen_relations", "gen_concepts", "finish_save");
        // 三模式分流（互斥条件边，顺序无关）：skeleton 直通收口，LLM 段经 mode != 'skeleton' 进入
        // （可改编排但须保留 mode 变量语义）
        conditional(graph, "gen_skeleton", "gen_metrics", "mode != 'skeleton'");
        conditional(graph, "gen_skeleton", "finish_save", "mode == 'skeleton'");
        return graph;
    }

    /** 取任务上下文（执行器统一入口；缺变量即编排调用方编程错误） */
    public static com.bidr.insight.smartquery.service.GenTaskContext genCtx(FlowContext context) {
        com.bidr.insight.smartquery.service.GenTaskContext ctx =
                context.get(VAR_GEN_CTX, com.bidr.insight.smartquery.service.GenTaskContext.class);
        if (ctx == null) {
            throw new IllegalStateException("流程上下文缺少 genCtx 变量（须由 handleTask 注入）");
        }
        return ctx;
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

    /** 顺序连边（无条件直连） */
    private static void link(FlowGraph graph, String... nodeIds) {
        for (int i = 0; i < nodeIds.length - 1; i++) {
            FlowGraph.FlowEdge edge = new FlowGraph.FlowEdge();
            edge.setSource(nodeIds[i]);
            edge.setTarget(nodeIds[i + 1]);
            graph.getEdges().add(edge);
        }
    }

    /** 条件边（唯一源目的对：mode 直通收口的骨架模式捷径） */
    private static void conditional(FlowGraph graph, String source, String target, String condition) {
        FlowGraph.FlowEdge edge = new FlowGraph.FlowEdge();
        edge.setSource(source);
        edge.setTarget(target);
        edge.setCondition(condition);
        graph.getEdges().add(edge);
    }
}
