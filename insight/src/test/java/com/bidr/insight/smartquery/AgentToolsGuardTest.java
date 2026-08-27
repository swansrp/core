package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.exec.SmartQueryJdbcExecutor;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.semantic.SmartQueryParser;
import com.bidr.insight.smartquery.service.AgentAssetCacheService;
import com.bidr.insight.smartquery.service.ProposalService;
import com.bidr.insight.smartquery.service.SmartQueryService;
import com.bidr.insight.smartquery.service.tools.AssetProposalTools;
import com.bidr.insight.smartquery.service.tools.SemanticQueryTools;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentToolsGuardTest
 * Description: 自主维护 agent 工具守卫用例（计划 Test Plan 5）：propose_asset
 * 非法类型/非法 JSON/结构缺失拒绝 + 本批次去重；build_query/execute_query
 * 引用不存在资产拒绝 + 非法 JSON 拒绝。纯构造（同 SmartQueryEndpointFlowTest
 * 范式），不依赖外部数据源与 Spring 容器
 *
 * @author Sharp
 * @since 2026/8/20
 */
public class AgentToolsGuardTest {

    private static SemanticQueryTools queryTools;

    @BeforeClass
    public static void setup() {
        SemanticLayer layer = new SemanticLayer();
        layer.init();
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public java.util.Map<String, String> assetsFor(String agentCode) {
                return java.util.Collections.emptyMap();
            }
        });
        SmartQueryService service = new SmartQueryService(new SmartQueryParser(),
                new SemanticQueryValidator(registry), new SqlGenerator(registry),
                new SmartQueryJdbcExecutor(), new com.bidr.insight.smartquery.adapter.StatisticPayloadAdapter(registry));
        queryTools = new SemanticQueryTools(service, "default", null, null);
    }

    // ────────────────────────── propose_asset 守卫 ──────────────────────────

    /** 非法资产类型拒绝（不触提案入库） */
    @Test
    public void proposeAssetBadTypeRejected() {
        AssetProposalTools tools = new AssetProposalTools(null, "default", "b", "q", "", null, null, null);
        String r = tools.proposeAsset("bad_type", "{\"name\":\"x\"}", "r");
        Assert.assertTrue("非法类型应被拒绝: " + r, r.contains("非法资产类型"));
    }

    /** 非法 JSON 拒绝 */
    @Test
    public void proposeAssetBadJsonRejected() {
        AssetProposalTools tools = new AssetProposalTools(null, "default", "b", "q", "", null, null, null);
        Assert.assertTrue(tools.proposeAsset("metrics", "not json", "r").contains("不是合法 JSON"));
    }

    /** 非对象 JSON 拒绝（数组/字符串） */
    @Test
    public void proposeAssetNonObjectRejected() {
        AssetProposalTools tools = new AssetProposalTools(null, "default", "b", "q", "", null, null, null);
        Assert.assertTrue(tools.proposeAsset("metrics", "[1,2]", "r").contains("必须是 JSON 对象"));
    }

    /** 结构缺失拒绝：metrics 缺 name / value-domains 缺 field */
    @Test
    public void proposeAssetMissingKeyRejected() {
        AssetProposalTools tools = new AssetProposalTools(null, "default", "b", "q", "", null, null, null);
        Assert.assertTrue(tools.proposeAsset("metrics", "{}", "r").contains("必须包含非空 name"));
        Assert.assertTrue(tools.proposeAsset("value-domains", "{\"entity\":\"e\"}", "r")
                .contains("必须包含非空 entity 与 field"));
    }

    /** 正常提案 ok + 本批次 (type,itemKey) 去重（saveOne 只被调一次）+ 语义层判 add */
    @Test
    public void proposeAssetDedupWithinBatch() {
        SemanticLayer layer = new SemanticLayer();
        layer.init();
        final List<String> saved = new ArrayList<>();
        ProposalService stub = new ProposalService(null, null, null) {
            @Override
            public int saveOne(String agentCode, String batchNo, String question, String sqJson,
                               String assetType, String itemKey, String op, String contentJson, String reason) {
                saved.add(assetType + "|" + itemKey + "|" + op);
                return 7;
            }
        };
        AssetProposalTools tools = new AssetProposalTools(stub, "default", "batch-1", "q", "", layer, null, null);
        String ok = tools.proposeAsset("metrics", "{\"name\":\"guard_new_metric_x\"}", "理由");
        Assert.assertTrue("正常提案应成功: " + ok, ok.contains("\"ok\":true"));
        Assert.assertTrue("默认层不存在该指标应判 add: " + ok, ok.contains("\"op\":\"add\""));
        Assert.assertTrue(ok.contains("\"proposalId\":7"));
        String dup = tools.proposeAsset("metrics", "{\"name\":\"guard_new_metric_x\"}", "再提");
        Assert.assertTrue("同批次重复提案应被拒绝: " + dup, dup.contains("请勿重复提交"));
        Assert.assertEquals("saveOne 应只被调用一次", 1, saved.size());
        // 同类型不同 key 仍可提
        Assert.assertTrue(tools.proposeAsset("metrics", "{\"name\":\"guard_new_metric_y\"}", "r")
                .contains("\"ok\":true"));
        Assert.assertEquals(2, saved.size());
    }

    // ────────────────────────── build_query / execute_query 守卫 ──────────────────────────

    /** build_query 引用不存在维度/指标：校验拒绝并回传错误清单（LLM 自纠闭环） */
    @Test
    public void buildQueryUnknownAssetRejected() {
        String r = queryTools.buildQuery(
                "{\"query_type\":\"metric\",\"metrics\":[\"not_exist_metric\"],\"dimensions\":[\"dept_code\"]}");
        Assert.assertTrue("应返回 valid=false: " + r, r.contains("\"valid\":false"));
        Assert.assertTrue("错误清单应点名未知资产: " + r, r.contains("not_exist_metric"));
    }

    /** execute_query 引用不存在资产：同样拒绝（先校验后执行） */
    @Test
    public void executeQueryUnknownAssetRejected() {
        String r = queryTools.executeQuery(
                "{\"query_type\":\"metric\",\"metrics\":[\"not_exist_metric\"],\"dimensions\":[]}", null);
        Assert.assertTrue("应返回 ok=false: " + r, r.contains("\"ok\":false"));
        Assert.assertTrue(r.contains("not_exist_metric"));
    }

    /** build_query 非法 JSON / 非对象拒绝 */
    @Test
    public void buildQueryBadJsonRejected() {
        Assert.assertTrue(queryTools.buildQuery("not json").contains("不是合法 JSON"));
        Assert.assertTrue(queryTools.buildQuery("[1,2]").contains("不是 JSON 对象"));
    }

    /** execute_query 非法 JSON 拒绝 */
    @Test
    public void executeQueryBadJsonRejected() {
        Assert.assertTrue(queryTools.executeQuery("bad json", null).contains("不是合法 JSON"));
    }

    /** 停止信号下两个工具都拒绝执行 */
    @Test
    public void stopSignalRejected() {
        SemanticLayer layer = new SemanticLayer();
        layer.init();
        SemanticLayerRegistry registry = new SemanticLayerRegistry(layer, new AgentAssetCacheService() {
            @Override
            public java.util.Map<String, String> assetsFor(String agentCode) {
                return java.util.Collections.emptyMap();
            }
        });
        SmartQueryService service = new SmartQueryService(new SmartQueryParser(),
                new SemanticQueryValidator(registry), new SqlGenerator(registry),
                new SmartQueryJdbcExecutor(), new com.bidr.insight.smartquery.adapter.StatisticPayloadAdapter(registry));
        SemanticQueryTools stopping = new SemanticQueryTools(service, "default", null, () -> true);
        Assert.assertTrue(stopping.buildQuery("{\"query_type\":\"metric\",\"metrics\":[]}")
                .contains("任务已被用户停止"));
        AssetProposalTools propose = new AssetProposalTools(null, "default", "b", "q", "", null, null, () -> true);
        Assert.assertTrue(propose.proposeAsset("metrics", "{\"name\":\"x\"}", "r")
                .contains("任务已被用户停止"));
    }
}
