package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.meta.DimensionDeriveSupport;
import com.bidr.insight.smartquery.meta.SupportedDimensionSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: SupportedDimensionExpandTest
 * Description: 指标 supported_dimensions 后端确定性展开回归（2026-08-25 背景：sd 展开
 * 后端化后 LLM 不再全量输出维度清单，落库时统一覆盖展开——展开规则即问数校验边界，
 * 骨架+关系可达/敏感排除/大小写归一任一口径走偏都会直接造成问数漏维度或多维度）：
 * 源表骨架维度 + 经 relations 可达实体维度（排除不可达）、敏感列排除、表名与表达式大小写归一
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class SupportedDimensionExpandTest {

    private final ObjectMapper om = new ObjectMapper();

    private EntityDef entity(String name, String table) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable(table);
        e.setFields(new ArrayList<>());
        return e;
    }

    private DimensionDef dim(String name, String expression) {
        DimensionDef d = new DimensionDef();
        d.setName(name);
        d.setExpression(expression);
        return d;
    }

    private List<String> sd(JsonNode metric) {
        List<String> out = new ArrayList<>();
        metric.path("supported_dimensions").forEach(n -> out.add(n.asText()));
        return out;
    }

    /** 骨架+关系可达：源表维度 + relations 可达实体维度，去重保序；不可达实体维度排除；未知源表跳过不动 */
    @Test
    public void expandFollowsSourceTableAndRelations() throws Exception {
        ArrayNode metrics = (ArrayNode) om.readTree(
                "[{\"name\":\"amt\",\"source_table\":\"dw.ht_info\"},"
                        + "{\"name\":\"amt2\",\"source_table\":\"dw.unknown\"}]");
        List<EntityDef> entities = Arrays.asList(
                entity("ht_info", "dw.ht_info"),
                entity("ht_org", "dw.ht_org"),
                entity("ht_island", "dw.ht_island"));
        List<DimensionDef> dims = Arrays.asList(
                dim("region", "dw.ht_info.region"),
                dim("region", "dw.ht_org.region"),       // 重名：保序去重只留首次
                dim("dept", "dw.ht_org.dept"),
                dim("isolated", "dw.ht_island.isolated"));
        JsonNode relations = om.readTree(
                "[{\"from_entity\":\"ht_info\",\"to_entity\":\"ht_org\"}]");
        SupportedDimensionSupport.expand(metrics, entities, dims, new HashSet<>(), relations);
        Assert.assertEquals("源表骨架维度 + 可达实体维度，重名去重保序",
                Arrays.asList("region", "dept"), sd(metrics.get(0)));
        Assert.assertFalse("未知源表跳过（守卫前置拦截），不触碰原结构",
                metrics.get(1).has("supported_dimensions"));
    }

    /** 敏感排除：可达实体的敏感列不进 supported_dimensions（entity.field 小写键，与生成服务同口径） */
    @Test
    public void expandExcludesSensitiveColumns() throws Exception {
        ArrayNode metrics = (ArrayNode) om.readTree(
                "[{\"name\":\"cnt\",\"source_table\":\"dw.ht_info\"}]");
        List<EntityDef> entities = Collections.singletonList(entity("ht_info", "dw.ht_info"));
        List<DimensionDef> dims = Arrays.asList(
                dim("region", "dw.ht_info.region"),
                dim("phone", "dw.ht_info.phone"));
        SupportedDimensionSupport.expand(metrics, entities, dims,
                new HashSet<>(Collections.singletonList("ht_info.phone")), null);
        Assert.assertEquals("敏感列维度被排除", Collections.singletonList("region"), sd(metrics.get(0)));
    }

    /** 大小写归一：source_table / 维度表达式前两段按小写匹配（库内大小写混杂不漏不重）；
     *  敏感集合键由调用方按小写构建（与生成服务同口径），展开侧不兼容大写键 */
    @Test
    public void expandNormalizesCase() throws Exception {
        ArrayNode metrics = (ArrayNode) om.readTree(
                "[{\"name\":\"amt\",\"source_table\":\"DW.HT_INFO\"}]");
        List<EntityDef> entities = Collections.singletonList(entity("ht_info", "dw.ht_info"));
        List<DimensionDef> dims = Arrays.asList(
                dim("region", "DW.HT_INFO.region"),
                dim("id_card", "dw.HT_INFO.id_card"));
        SupportedDimensionSupport.expand(metrics, entities, dims,
                new HashSet<>(Collections.singletonList("ht_info.id_card")), null);
        Assert.assertEquals("大写表名/表达式可命中，敏感列照常排除",
                Collections.singletonList("region"), sd(metrics.get(0)));
    }

    /** 空实体兕底：无实体直接返回不抛错、不改写指标（防止落库链路被展开逻辑拖垮） */
    @Test
    public void expandNoopWithoutEntities() throws Exception {
        ArrayNode metrics = (ArrayNode) om.readTree(
                "[{\"name\":\"amt\",\"source_table\":\"dw.ht_info\"}]");
        SupportedDimensionSupport.expand(metrics, Collections.<EntityDef>emptyList(),
                Collections.singletonList(dim("region", "dw.ht_info.region")), new HashSet<>(), null);
        Assert.assertFalse(metrics.get(0).has("supported_dimensions"));
    }

    /** LLM 自填清单整体覆盖（2026-08-26 背景：单资产路径旧稿曾把原始列名 bidr_name/
     *  originatingleadid 等写进 supported_dimensions，发布校验报悬空）：模型违背「不必输出」
     *  自填的原始列名清单被确定性展开整体替换，悬空名一个不留 */
    @Test
    public void expandOverwritesLlmProvidedDanglingList() throws Exception {
        ArrayNode metrics = (ArrayNode) om.readTree(
                "[{\"name\":\"amt\",\"source_table\":\"dw.ht_info\","
                        + "\"supported_dimensions\":[\"bidr_name\",\"originatingleadid\",\"region\"]}]");
        List<EntityDef> entities = Collections.singletonList(entity("ht_info", "dw.ht_info"));
        List<DimensionDef> dims = Arrays.asList(
                dim("region", "dw.ht_info.region"),
                dim("phase", "dw.ht_info.phase"));
        SupportedDimensionSupport.expand(metrics, entities, dims, new HashSet<>(), null);
        Assert.assertEquals("自填悬空列名被清除，只留骨架维度",
                Arrays.asList("region", "phase"), sd(metrics.get(0)));
    }

    /** 角色修正联动（2026-08-26 背景：确认页改角色后 dimensions 不重派生、metrics sd 不重展开，
     *  旧骨架的技术列维度批量悬空）：rederiveForTables 移除改角色列的未认证维度后 expand，
     *  指标 sd 即时跟随实体结论（实体保存路径同序调用这两个静态件） */
    @Test
    public void rederiveThenExpandFollowsRoleCorrection() throws Exception {
        EntityDef ent = entity("opp", "dw.opp");
        EntityDef.EntityFieldDef keep = new EntityDef.EntityFieldDef();
        keep.setName("region");
        keep.setRole("dimension");
        keep.setType("String");
        EntityDef.EntityFieldDef fixed = new EntityDef.EntityFieldDef();
        fixed.setName("originatingleadid");
        fixed.setRole("ignore");   // 人工修正：技术列不当维度
        fixed.setType("String");
        ent.getFields().add(keep);
        ent.getFields().add(fixed);
        // 修正前骨架派生的旧维度清单（含技术列维度）
        List<DimensionDef> dims = new ArrayList<>(Arrays.asList(
                dim("region", "dw.opp.region"),
                dim("originatingleadid", "dw.opp.originatingleadid")));
        DimensionDeriveSupport.rederiveForTables(dims, Collections.singletonList(ent),
                Collections.singletonList("dw.opp"));
        List<String> dimNames = new ArrayList<>();
        dims.forEach(d -> dimNames.add(d.getName()));
        Assert.assertEquals("改角色列的未认证维度被重派生移除", Collections.singletonList("region"), dimNames);
        // 旧指标 sd 带悬空名，重展开后即时跟随
        ArrayNode metrics = (ArrayNode) om.readTree(
                "[{\"name\":\"amt\",\"source_table\":\"dw.opp\","
                        + "\"supported_dimensions\":[\"originatingleadid\",\"region\"]}]");
        SupportedDimensionSupport.expand(metrics, Collections.singletonList(ent), dims, new HashSet<>(), null);
        Assert.assertEquals(Collections.singletonList("region"), sd(metrics.get(0)));
    }

    /** 合并资产 map 全量重展开：存量指标跟随新增维度（2026-08-26 背景：勘察院问数维护链
     *  临时层 append 合同名称维度，旧逻辑只展开新增指标，存量 receive_money_amount 清单
     *  不含新维度，§6.2.2 误判不走误入 SQL 兜底） */
    @Test
    public void reexpandMergedMetricsCoversExistingMetrics() throws Exception {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("entities.json", "[{\"name\":\"recv\",\"table\":\"dw.recv\"},"
                + "{\"name\":\"contract\",\"table\":\"dw.contract\"}]");
        merged.put("dimensions.json", "[{\"name\":\"bidr_date_year\",\"expression\":\"dw.recv.bidr_date\"},"
                + "{\"name\":\"bidr_code\",\"expression\":\"dw.contract.bidr_code\"},"
                + "{\"name\":\"bidr_contract_name\",\"expression\":\"dw.contract.bidr_name\"}]");
        merged.put("relations.json", "[{\"from_entity\":\"recv\",\"to_entity\":\"contract\"}]");
        merged.put("sensitive-fields.json", "{\"schema_version\":\"1.0\",\"tables\":[]}");
        // 存量指标旧清单不含后 append 的新维度 bidr_contract_name
        merged.put("metrics.json", "[{\"name\":\"receive_money_amount\",\"source_table\":\"dw.recv\","
                + "\"supported_dimensions\":[\"bidr_date_year\",\"bidr_code\"]}]");
        Assert.assertTrue(SupportedDimensionSupport.reexpandMergedMetrics(merged));
        JsonNode m = om.readTree(merged.get("metrics.json")).get(0);
        Assert.assertEquals("存量指标清单跟随新增维度（关系可达）",
                Arrays.asList("bidr_date_year", "bidr_code", "bidr_contract_name"), sd(m));
    }
}
