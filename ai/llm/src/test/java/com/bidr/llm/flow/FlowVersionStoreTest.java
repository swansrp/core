package com.bidr.llm.flow;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.flow.executor.OutputNodeExecutor;
import com.bidr.llm.flow.executor.StartNodeExecutor;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: FlowVersionStoreTest
 * Description: 流程定义版本化用例（T4.1 门面）：版本存取与 latest 递增 / 覆盖指定版本 /
 * 按版本运行真的执行"该版本"graph（不同版本输出映射不同，产出可区分）/
 * 未接入 FlowDefinitionVersionStore 时整体回落关闭（清单空、读写明确报 ServiceException）。
 * 引擎用真实 FlowEngine（start+output 执行器直连），轨迹记录器降级形态（无 Redis 全空操作）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class FlowVersionStoreTest {

    private static final String FLOW_KEY = "test-flow";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 测试版版本存储：flowKey → (version → record)，TreeMap 保序
     */
    static class MapVersionStore implements FlowDefinitionVersionStore {

        final Map<String, TreeMap<Integer, FlowDefinitionRecord>> store = new ConcurrentHashMap<>();

        @Override
        public List<Integer> versions(String flowKey) {
            TreeMap<Integer, FlowDefinitionRecord> versions = store.get(flowKey);
            return versions == null ? new ArrayList<>() : new ArrayList<>(versions.keySet());
        }

        @Override
        public FlowDefinitionRecord load(String flowKey, int version) {
            TreeMap<Integer, FlowDefinitionRecord> versions = store.get(flowKey);
            return versions == null ? null : versions.get(version);
        }

        @Override
        public void save(String flowKey, String name, String graphJson, int version) {
            store.computeIfAbsent(flowKey, key -> new TreeMap<>())
                    .put(version, record(flowKey, name, graphJson));
        }

        @Override
        public int latestVersion(String flowKey) {
            TreeMap<Integer, FlowDefinitionRecord> versions = store.get(flowKey);
            return versions == null || versions.isEmpty() ? 0 : versions.lastKey();
        }

        private FlowDefinitionRecord record(String flowKey, String name, String graphJson) {
            FlowDefinitionRecord record = new FlowDefinitionRecord();
            record.setFlowKey(flowKey);
            record.setName(name);
            record.setGraph(graphJson);
            return record;
        }
    }

    /**
     * 已注册流程 Provider（封闭集成员；defaultGraph 不会被版本化路径用到，给空图即可）
     */
    private static FlowDefinitionProvider provider() {
        return new FlowDefinitionProvider() {
            @Override
            public String skillCode() {
                return "test-skill";
            }

            @Override
            public String flowKey() {
                return FLOW_KEY;
            }

            @Override
            public String displayName() {
                return "测试链";
            }

            @Override
            public FlowGraph defaultGraph() {
                return new FlowGraph();
            }
        };
    }

    /**
     * 真实引擎（start+output 执行器）+ 降级轨迹记录器（无 Redis，全空操作不报错）
     */
    private static FlowEngine realEngine() {
        return new FlowEngine(null, degradedRecorder(),
                Arrays.asList(new StartNodeExecutor(), new OutputNodeExecutor()),
                new ArrayList<>(), Arrays.asList(provider()));
    }

    private static FlowTraceRecorder degradedRecorder() {
        return new FlowTraceRecorder(new StaticObjectProvider<>(null), new StaticObjectProvider<>(null));
    }

    private FlowManagerService managerWithStore(FlowDefinitionVersionStore store) {
        return new FlowManagerService(new StaticObjectProvider<>(null), realEngine(),
                Arrays.asList(provider()), new StaticObjectProvider<>(store), degradedRecorder());
    }

    /**
     * 未启用版本化的管理服务（旧构造路径，versionStoreProvider/轨迹均为空）
     */
    private FlowManagerService managerWithoutStore() {
        return new FlowManagerService(new StaticObjectProvider<>(null), realEngine(),
                Arrays.asList(provider()));
    }

    private String graphJson(String outputField) throws Exception {
        FlowGraph graph = new FlowGraph();
        FlowGraph.FlowNode start = new FlowGraph.FlowNode();
        start.setId("s1");
        start.setType("start");
        start.setName("开始");
        FlowGraph.FlowNode output = new FlowGraph.FlowNode();
        output.setId("o1");
        output.setType("output");
        output.setName("输出");
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put(outputField, "question");
        output.getConfig().put("outputs", outputs);
        FlowGraph.FlowEdge edge = new FlowGraph.FlowEdge();
        edge.setSource("s1");
        edge.setTarget("o1");
        graph.setNodes(Arrays.asList(start, output));
        graph.setEdges(Arrays.asList(edge));
        return objectMapper.writeValueAsString(graph);
    }

    /** 版本存取：缺省新版本号递增、清单升序、详情可回读（builtin=false、缺省名回落显示名）、覆盖指定版本 */
    @Test
    public void saveLoadAndListVersions() throws Exception {
        MapVersionStore store = new MapVersionStore();
        FlowManagerService manager = managerWithStore(store);

        int v1 = manager.saveFlowVersion(req(graphJson("answer"), null));
        int v2 = manager.saveFlowVersion(req(graphJson("echo"), "定制版"));
        Assert.assertEquals("缺省版本号 = 最新+1", 1, v1);
        Assert.assertEquals(2, v2);
        Assert.assertEquals("版本清单升序", Arrays.asList(1, 2), manager.flowVersions(FLOW_KEY));
        Assert.assertEquals("latest 递增", 2, store.latestVersion(FLOW_KEY));

        FlowDetailRes detail = manager.getFlowVersion(FLOW_KEY, 1);
        Assert.assertEquals("版本详情 flowKey", FLOW_KEY, detail.getFlowKey());
        Assert.assertEquals("未显式命名回落注册显示名", "测试链", detail.getName());
        Assert.assertEquals("版本记录非内置链", Boolean.FALSE, detail.getBuiltin());
        Assert.assertEquals(2, detail.getGraph().getNodes().size());
        Assert.assertEquals("v1 输出字段映射（answer=question）", "question",
                ((Map) detail.getGraph().getNodes().get(1).getConfig().get("outputs")).get("answer"));

        // 同版本重复保存 = 覆盖（清单不变，内容更新）
        manager.saveFlowVersion(req(graphJson("answer2"), null, 1));
        Assert.assertEquals("覆盖不新增版本", Arrays.asList(1, 2), manager.flowVersions(FLOW_KEY));
        Assert.assertEquals("覆盖后读到新内容", "question",
                ((Map) manager.getFlowVersion(FLOW_KEY, 1).getGraph()
                        .getNodes().get(1).getConfig().get("outputs")).get("answer2"));
    }

    /** 按版本运行：产出来自被指定的版本 graph（v1/v2 输出映射不同，结果可区分），graph 也注入上下文 */
    @Test
    public void runSpecifiedVersionExecutesThatGraph() throws Exception {
        FlowManagerService manager = managerWithStore(new MapVersionStore());
        manager.saveFlowVersion(req(graphJson("answer"), "v1"));
        manager.saveFlowVersion(req(graphJson("echo"), "v2"));

        FlowContext ctxV1 = new FlowContext(null);
        ctxV1.setVariable("question", "hello");
        String traceIdV1 = manager.runFlowVersion(FLOW_KEY, 1, ctxV1);
        Assert.assertNotNull("轨迹 id 应返回（记录器降级但仍发 id）", traceIdV1);
        Assert.assertEquals("v1 版本产出字段=answer", "hello", ctxV1.getOutput().get("answer"));
        Assert.assertFalse("v1 不该产出 v2 的字段", ctxV1.getOutput().containsKey("echo"));
        Assert.assertNotNull("执行 graph 注入上下文", ctxV1.getGraph());

        FlowContext ctxV2 = new FlowContext(null);
        ctxV2.setVariable("question", "world");
        manager.runFlowVersion(FLOW_KEY, 2, ctxV2);
        Assert.assertEquals("v2 版本产出字段=echo", "world", ctxV2.getOutput().get("echo"));
        Assert.assertFalse("v2 不该产出 v1 的字段", ctxV2.getOutput().containsKey("answer"));
    }

    /** 回落：未接入版本存储时整体关闭——清单空、读写运行统一 ServiceException 明示未启用 */
    @Test
    public void fallbackWhenVersionStoreAbsent() throws Exception {
        FlowManagerService manager = managerWithoutStore();

        Assert.assertTrue("未启用版本化时清单为空", manager.flowVersions(FLOW_KEY).isEmpty());

        try {
            manager.getFlowVersion(FLOW_KEY, 1);
            Assert.fail("getFlowVersion 应报未启用");
        } catch (ServiceException e) {
            Assert.assertTrue("提示未启用版本化: " + e.getMessage(), e.getMessage().contains("未启用"));
        }
        try {
            manager.runFlowVersion(FLOW_KEY, 1, new FlowContext(null));
            Assert.fail("runFlowVersion 应报未启用");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("未启用"));
        }
        try {
            manager.saveFlowVersion(req(graphJson("answer"), null));
            Assert.fail("saveFlowVersion 应报未启用");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("未启用"));
        }
    }

    /** 缺陷防线：版本不存在 / graph 坏记录 / 非法入参与未注册 flowKey 的封闭集校验 */
    @Test
    public void guards() throws Exception {
        MapVersionStore store = new MapVersionStore();
        FlowManagerService manager = managerWithStore(store);

        try {
            manager.getFlowVersion(FLOW_KEY, 9);
            Assert.fail("不存在的版本应报错");
        } catch (ServiceException e) {
            Assert.assertTrue("提示版本不存在: " + e.getMessage(), e.getMessage().contains("不存在"));
        }
        try {
            manager.runFlowVersion(FLOW_KEY, 9, new FlowContext(null));
            Assert.fail("运行不存在的版本应报错");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("不存在"));
        }
        try {
            manager.saveFlowVersion(req("{这不是 JSON", null));
            Assert.fail("坏 graphJson 应报解析失败");
        } catch (ServiceException e) {
            Assert.assertTrue("提示解析失败: " + e.getMessage(), e.getMessage().contains("解析失败"));
        }
        try {
            manager.flowVersions("not-registered");
            Assert.fail("未注册 flowKey 应被封闭集拒识");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("未注册"));
        }
        try {
            manager.saveFlowVersion(req(null, null));
            Assert.fail("空 graphJson 应报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("graph"));
        }
        Assert.assertEquals("空版本号拒绝", 0, store.latestVersion(FLOW_KEY));
        try {
            manager.saveFlowVersion(req(graphJson("answer"), null, -1));
            Assert.fail("非正版本号应报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("版本号"));
        }
    }

    private FlowVersionSaveReq req(String graphJson, String name) {
        return req(graphJson, name, null);
    }

    private FlowVersionSaveReq req(String graphJson, String name, Integer version) {
        FlowVersionSaveReq req = new FlowVersionSaveReq();
        req.setFlowKey(FLOW_KEY);
        req.setName(name);
        req.setGraphJson(graphJson);
        req.setVersion(version);
        return req;
    }
}
