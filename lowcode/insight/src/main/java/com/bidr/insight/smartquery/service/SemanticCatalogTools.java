package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.layer.ConceptDef;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Title: SemanticCatalogTools
 * Description: 语义层资产目录检索工具（langchain4j function calling，内存态只读）：
 * 问数链路提示词只常驻名称级精简索引，字段明细/指标公式/维度表达式/码值域等重内容
 * 由模型通过这些工具按需拉取，支撑大规模资产不爆上下文。构造入参 SemanticLayer——
 * 维护链传临时层时工具看到的即校验所用同一套资产。
 * 找不到目标返回错误文本（模型可自纠换名再查）；不暴露敏感字段明细
 *
 * @author Sharp
 * @since 2026/8/21
 */
public class SemanticCatalogTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** searchAssets 单次返回条目上限（防关键词过宽撑爆上下文） */
    private static final int SEARCH_LIMIT = 30;

    /** dimensionDetail 码值域回显上限（大值域防撑爆上下文，其余值可经 findValue 反查） */
    private static final int DOMAIN_ECHO_LIMIT = 50;

    /** metricDetail supported_dimensions 回显上限：维度名短、全量回显体量可控，上限放到 120 保证 LLM
     * 拿到完整候选集（凑选择题选项/写维度过滤不再双盲）；仅极端超大表触顶截断时引导关键词收窄 */
    private static final int SUPPORTED_DIMS_ECHO_LIMIT = 120;

    /** 分级目录阈值：候选维度超过该数且已配置 hierarchy 分组时，回分组摘要（组名+数量+预览）
     *  代替全量清单，LLM 按组名取组内全量（两级导航）；未配置分组则回落全量回显+截断引导。
     *  正式值 60；测试期临时调低（验证完调回） */
    private static final int GROUP_SUMMARY_THRESHOLD = 10;

    /** 分组摘要每组预览维度数（全量组内清单用 group 参数取） */
    private static final int GROUP_PREVIEW_SIZE = 5;

    /** 单条事实摘录截断上限（台账供后续子会话注入，控制体量） */
    private static final int FACT_MAX_LEN = 200;

    private final SemanticLayer layer;

    /** 事实摘录上报（跨阶段交接台账）：非空时各查询工具核实成功的资产事实逐条上报，
     *  由编排层收集注入后续子会话（解析链核实 → 维护链直接采信，禁止重复探索）；null=不记录 */
    private final Consumer<String> factRecorder;

    public SemanticCatalogTools(SemanticLayer layer) {
        this(layer, null);
    }

    public SemanticCatalogTools(SemanticLayer layer, Consumer<String> factRecorder) {
        this.layer = layer;
        this.factRecorder = factRecorder;
    }

    @Tool("按关键词搜索语义层资产（实体/指标/维度/概念），返回匹配的资产名称清单；"
            + "同时会搜维度的口语别名与码值域中的取值名称（命中时返回所属维度）。"
            + "用于在精简索引中没看清目标资产时按中文名/英文名/表名/业务叫法检索")
    public String searchAssets(@P("关键词（中文名/英文名/表名片段/业务叫法均可）") String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return "{\"error\":\"keyword 不能为空\"}";
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        ArrayNode hits = OM.createArrayNode();
        for (EntityDef ent : layer.entities()) {
            if (hit(kw, ent.getName(), ent.getDisplayName(), ent.getTable())) {
                hits.add(item("entity", ent.getName(), ent.getDisplayName()));
            }
        }
        for (MetricDef md : layer.metricMap().values()) {
            if (hit(kw, md.getName(), md.getDisplayName())) {
                hits.add(item("metric", md.getName(), md.getDisplayName()));
            }
        }
        for (DimensionDef dd : layer.dimensionMap().values()) {
            if (hit(kw, dd.getName(), dd.getDisplayName()) || hitAliases(kw, dd.getAliases())) {
                hits.add(item("dimension", dd.getName(), dd.getDisplayName()));
            }
        }
        for (String concept : layer.conceptNames()) {
            if (concept != null && concept.toLowerCase(Locale.ROOT).contains(kw)) {
                hits.add(item("concept", concept, null));
            }
        }
        // 码值域取值名称命中 → 返回拥有该值域的维度（具体码值用 findValue 精确反查）
        for (Map.Entry<String, ValueDomainDef> e : layer.domains().entrySet()) {
            ValueDomainDef.DomainValue matched = matchDomainValue(kw, e.getValue());
            if (matched != null) {
                for (String dim : dimsOfDomain(e.getValue())) {
                    ObjectNode n = item("dimension", dim, layer.dimensionMap().get(dim).getDisplayName());
                    n.put("hit_value", matched.getLabel());
                    hits.add(n);
                }
            }
        }
        if (hits.size() == 0) {
            return "{\"error\":\"未找到匹配资产，请换关键词（如中文名片段）再搜；若搜的是具体机构/部门/状态等取值，改用 findValue\"}";
        }
        recordFact(searchFact(keyword.trim(), hits));
        ObjectNode out = OM.createObjectNode();
        out.put("total", hits.size());
        out.set("items", hits.size() > SEARCH_LIMIT ? slice(hits, SEARCH_LIMIT) : hits);
        if (hits.size() > SEARCH_LIMIT) {
            out.put("note", "结果过多已截断，请补充更精确的关键词");
        }
        return writeJson(out);
    }

    @Tool("按取值反查维度与码值：输入用户问题中出现的具体取值（机构/部门名称、状态、区域等业务名称或简称），"
            + "返回所属维度与写入 filters 应使用的真实存储值（code）。"
            + "用户问题含具体机构名/部门名等取值时，写 filters 前必须先用本工具核实维度与码值，禁止凭猜测写")
    public String findValue(@P("取值关键词（业务名称/简称片段）") String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return "{\"error\":\"keyword 不能为空\"}";
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        ArrayNode hits = OM.createArrayNode();
        for (Map.Entry<String, ValueDomainDef> e : layer.domains().entrySet()) {
            for (ValueDomainDef.DomainValue v : nzValues(e.getValue())) {
                if (hit(kw, v.getLabel(), v.getCode()) || hitAliases(kw, v.getAliases())) {
                    for (String dim : dimsOfDomain(e.getValue())) {
                        ObjectNode n = OM.createObjectNode();
                        n.put("dimension", dim);
                        DimensionDef dd = layer.dimensionMap().get(dim);
                        if (dd != null) {
                            n.put("dimension_display", dd.getDisplayName());
                        }
                        n.put("code", v.getCode());
                        n.put("label", v.getLabel());
                        hits.add(n);
                    }
                }
            }
        }
        if (hits.size() == 0) {
            return "{\"error\":\"码值域中未找到取值 '" + keyword.trim() + "'，"
                    + "可能是自由文本字段（直接用 contains 过滤）或取值不存在，请换关键词或放弃该条件\"}";
        }
        recordFact(findValueFact(keyword.trim(), hits));
        ObjectNode out = OM.createObjectNode();
        out.put("total", hits.size());
        out.set("items", hits.size() > SEARCH_LIMIT ? slice(hits, SEARCH_LIMIT) : hits);
        out.put("usage", "filters 中写 {\"dimension\":维度名,\"operator\":\"=\",\"value\":code}");
        return writeJson(out);
    }

    @Tool("查询实体的字段明细：字段名、类型、中文名、码值域。组装 list 查询选字段、"
            + "写 filters 前必须先用本工具核实字段名与码值，禁止凭索引猜测")
    public String describeEntity(@P("实体英文名或源表全名（db.tbl）") String nameOrTable) {
        EntityDef ent = findEntity(nameOrTable);
        if (ent == null) {
            return "{\"error\":\"未找到实体 '" + nameOrTable + "'，请用 searchAssets 核实名称后重试\"}";
        }
        ObjectNode out = OM.createObjectNode();
        out.put("name", ent.getName());
        out.put("display_name", ent.getDisplayName());
        out.put("table", ent.getTable());
        // 分区列/快照语义：问数链写跨期查询前可见（全量取单期、增量跨期累加），防累计数算错
        if (ent.getPartitionColumn() != null && !ent.getPartitionColumn().isEmpty()) {
            out.put("partition_column", ent.getPartitionColumn());
        }
        if (ent.getSnapshotType() != null && !ent.getSnapshotType().isEmpty()) {
            out.put("snapshot_type", ent.getSnapshotType());
        }
        ArrayNode fs = out.putArray("fields");
        if (ent.getFields() != null) {
            for (EntityDef.EntityFieldDef f : ent.getFields()) {
                // 禁用列不参与 LLM 输入（口径同生成链 EntityDef.forLlmInput）
                if (Boolean.TRUE.equals(f.getDisabled())) {
                    continue;
                }
                ObjectNode fn = OM.createObjectNode();
                fn.put("name", f.getName());
                fn.put("type", f.getType());
                fn.put("display_name", f.getDisplayName());
                if (f.getValueDomain() != null && !f.getValueDomain().isEmpty()) {
                    fn.put("value_domain", f.getValueDomain());
                }
                fs.add(fn);
            }
        }
        StringBuilder fl = new StringBuilder();
        if (ent.getFields() != null) {
            for (EntityDef.EntityFieldDef f : ent.getFields()) {
                if (Boolean.TRUE.equals(f.getDisabled())) {
                    continue;
                }
                if (fl.length() > 0) {
                    fl.append(',');
                }
                fl.append(f.getName());
            }
        }
        recordFact("[describeEntity] 实体 " + ent.getName() + "(" + ent.getTable() + ") 字段：" + fl);
        return writeJson(out);
    }

    @Tool("查询指标明细：公式、来源表、支持维度（维度过多时返回分组目录，传 group 取组内全量，"
            + "或传 dimension_keyword 按中文名/英文名片段收窄）。"
            + "metric 查询写 metrics 前先用本工具核实指标名与支持维度；"
            + "新建同结构指标时条目字段以本工具返回为准（composite 指标必须携带 source_tables 数组）")
    public String metricDetail(@P("指标英文名") String name,
            @P("维度关键词，可省略：supported_dimensions 过多时按维度中文名/英文名片段过滤只回命中项") String dimensionKeyword,
            @P("维度分组名，可省略：分组摘要返回后传组名取组内全量维度清单") String group) {
        MetricDef md = name == null ? null : layer.metricMap().get(name.trim());
        if (md == null) {
            return "{\"error\":\"未找到指标 '" + name + "'，请用 searchAssets 核实名称后重试\"}";
        }
        ObjectNode out = OM.createObjectNode();
        out.put("name", md.getName());
        out.put("display_name", md.getDisplayName());
        out.put("type", md.getType() == null ? "atomic" : md.getType());
        out.put("formula", md.getFormula());
        if (md.getSourceTables() != null && !md.getSourceTables().isEmpty()) {
            ArrayNode sts = out.putArray("source_tables");
            md.getSourceTables().forEach(sts::add);
        } else {
            out.put("source_table", md.getSourceTable());
        }
        String dimNote = null;
        if (md.getSupportedDimensions() != null && !md.getSupportedDimensions().isEmpty()) {
            List<String> dims = new ArrayList<>(md.getSupportedDimensions());
            boolean keywordGiven = dimensionKeyword != null && !dimensionKeyword.trim().isEmpty();
            boolean groupGiven = group != null && !group.trim().isEmpty();
            if (keywordGiven) {
                String kw = dimensionKeyword.trim().toLowerCase(Locale.ROOT);
                List<String> kept = new ArrayList<>();
                for (String d : dims) {
                    DimensionDef dd = layer.dimensionMap().get(d);
                    if ((d != null && d.toLowerCase(Locale.ROOT).contains(kw))
                            || (dd != null && dd.getDisplayName() != null
                                    && dd.getDisplayName().toLowerCase(Locale.ROOT).contains(kw))) {
                        kept.add(d);
                    }
                }
                if (!kept.isEmpty()) {
                    dims = kept;
                } else {
                    dimNote = "关键词 '" + dimensionKeyword.trim() + "' 无命中，已回全量；请换维度中文名/英文名片段重试";
                }
            } else if (groupGiven) {
                // 分级目录第二级：按组名取组内全量（组名不匹配时回落全量+note）
                String gname = group.trim();
                List<String> inGroup = new ArrayList<>();
                for (String d : dims) {
                    if (gname.equals(layer.groupOfDimension(d))) {
                        inGroup.add(d);
                    }
                }
                if (!inGroup.isEmpty()) {
                    dims = inGroup;
                } else {
                    dimNote = "分组 '" + gname + "' 无命中（组名以分组摘要返回为准），已回全量";
                }
            } else if (dims.size() > GROUP_SUMMARY_THRESHOLD && !layer.dimensionGroups().isEmpty()) {
                // 分级目录第一级：超阈值且已配置分组 → 分组摘要（组名+数量+预览），代替全量清单
                return metricDetailWithGroupSummary(md, out, dims);
            }
            if (dims.size() > SUPPORTED_DIMS_ECHO_LIMIT) {
                dims = new ArrayList<>(dims.subList(0, SUPPORTED_DIMS_ECHO_LIMIT));
                dimNote = "支持维度过多已截断，请传 dimension_keyword 按中文名片段收窄或传 group 取组内全量后再查";
            }
            ArrayNode sd = out.putArray("supported_dimensions");
            dims.forEach(sd::add);
        }
        if (dimNote != null) {
            out.put("note", dimNote);
        }
        recordFact("[metricDetail] 指标 " + md.getName() + "：" + nvl(md.getFormula())
                + (md.getSourceTables() != null && !md.getSourceTables().isEmpty()
                        ? " 源表:" + String.join(",", md.getSourceTables())
                        : md.getSourceTable() != null ? " 源表:" + md.getSourceTable() : ""));
        return writeJson(out);
    }

    /** 分级目录分组摘要：候选维度按 hierarchy 归组（未入组归「其他」），每组出数量+预览；
     *  LLM 看摘要选组后传 group 参数取组内全量（两级导航，避免大清单盲扫或截断双盲） */
    private String metricDetailWithGroupSummary(MetricDef md, ObjectNode out, List<String> dims) {
        LinkedHashMap<String, List<String>> byGroup = new LinkedHashMap<>();
        for (SemanticLayer.DimensionGroup g : layer.dimensionGroups()) {
            byGroup.put(g.getName(), new ArrayList<>());
        }
        List<String> ungrouped = new ArrayList<>();
        for (String d : dims) {
            String g = layer.groupOfDimension(d);
            if (g == null) {
                ungrouped.add(d);
            } else {
                byGroup.get(g).add(d);
            }
        }
        ArrayNode groups = out.putArray("dimension_groups");
        for (Map.Entry<String, List<String>> e : byGroup.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            ObjectNode g = groups.addObject();
            g.put("group", e.getKey());
            g.put("count", e.getValue().size());
            ArrayNode pv = g.putArray("preview");
            for (int i = 0; i < Math.min(GROUP_PREVIEW_SIZE, e.getValue().size()); i++) {
                pv.add(e.getValue().get(i));
            }
        }
        if (!ungrouped.isEmpty()) {
            ObjectNode g = groups.addObject();
            g.put("group", "其他");
            g.put("count", ungrouped.size());
            ArrayNode pv = g.putArray("preview");
            for (int i = 0; i < Math.min(GROUP_PREVIEW_SIZE, ungrouped.size()); i++) {
                pv.add(ungrouped.get(i));
            }
        }
        out.put("note", "支持维度 " + dims.size() + " 个过多，已按分组目录返回摘要；"
                + "传 group=组名 取组内全量，或传 dimension_keyword 直达检索");
        recordFact("[metricDetail] 指标 " + md.getName() + "：" + nvl(md.getFormula())
                + (md.getSourceTables() != null && !md.getSourceTables().isEmpty()
                        ? " 源表:" + String.join(",", md.getSourceTables())
                        : md.getSourceTable() != null ? " 源表:" + md.getSourceTable() : ""));
        return writeJson(out);
    }

    @Tool("查询概念明细：展开定义（映射的维度、运算符、存储码值）与别名。"
            + "业务术语命中概念（如“传统项目”“勘察院”）时，写 filters 前必须先用本工具查看展开，"
            + "把概念转换为展开后的维度+取值条件（value 即存储码，直接写入 filters，禁止把概念名写进 filters）")
    public String conceptDetail(@P("概念名（中文名）") String name) {
        ConceptDef c = name == null ? null : layer.conceptMap().get(name.trim());
        if (c == null) {
            return "{\"error\":\"未找到概念 '" + name + "'，请用 searchAssets 核实名称后重试\"}";
        }
        ObjectNode out = OM.createObjectNode();
        out.put("name", c.getName());
        if (c.getAliases() != null && !c.getAliases().isEmpty()) {
            ArrayNode al = out.putArray("aliases");
            c.getAliases().forEach(al::add);
        }
        out.put("entity", c.getEntity());
        ConceptDef.ExpandsTo ex = c.getExpandsTo();
        if (ex != null) {
            ObjectNode exn = out.putObject("expands_to");
            exn.put("dimension", ex.getDimension());
            DimensionDef dd = layer.dimensionMap().get(ex.getDimension());
            if (dd != null) {
                exn.put("dimension_display", dd.getDisplayName());
            }
            exn.put("operator", ex.getOperator() == null ? "=" : ex.getOperator());
            exn.put("value", ex.getValue());
            out.put("usage", "filters 中展开为 {\"dimension\":\"" + ex.getDimension()
                    + "\",\"operator\":\"=\",\"value\":\"" + ex.getValue() + "\"}");
            recordFact("[conceptDetail] 概念 " + c.getName() + " → " + ex.getDimension()
                    + " " + (ex.getOperator() == null ? "=" : ex.getOperator()) + " " + ex.getValue());
        }
        if (c.getNote() != null && !c.getNote().isEmpty()) {
            out.put("note", c.getNote());
        }
        return writeJson(out);
    }

    @Tool("查询维度明细：表达式、中文名与码值域取值。filters 写维度前先用本工具核实维度名与码值")
    public String dimensionDetail(@P("维度英文名") String name) {
        DimensionDef dd = name == null ? null : layer.dimensionMap().get(name.trim());
        if (dd == null) {
            return "{\"error\":\"未找到维度 '" + name + "'，请用 searchAssets 核实名称后重试\"}";
        }
        ObjectNode out = OM.createObjectNode();
        out.put("name", dd.getName());
        out.put("display_name", dd.getDisplayName());
        out.put("expression", dd.getExpression());
        if ("multi".equals(dd.getMatch())) {
            out.put("match_note", "多值列（逗号分隔 code 串）：过滤照常用 = 单个码值，引擎自动按「包含」匹配（FIND_IN_SET），勿传整串");
        }
        ValueDomainDef dom = layer.domainOfDim(dd.getName());
        if (dom != null) {
            ObjectNode vd = out.putObject("value_domain");
            vd.put("stored_as", dom.getStoredAs());
            ArrayNode vals = vd.putArray("values");
            int n = 0;
            for (ValueDomainDef.DomainValue v : nzValues(dom)) {
                if (n++ >= DOMAIN_ECHO_LIMIT) {
                    vals.addObject().put("note", "…其余取值用 findValue 按名称反查");
                    break;
                }
                ObjectNode vo = vals.addObject();
                vo.put("code", v.getCode());
                vo.put("label", v.getLabel());
            }
        }
        recordFact("[dimensionDetail] 维度 " + dd.getName() + "：" + nvl(dd.getExpression()));
        return writeJson(out);
    }

    // ---------------- 内部 ----------------

    /** 事实摘录上报（超长截断，台账体量可控） */
    private void recordFact(String line) {
        if (factRecorder == null || line == null || line.isEmpty()) {
            return;
        }
        factRecorder.accept(line.length() > FACT_MAX_LEN ? line.substring(0, FACT_MAX_LEN) + "…" : line);
    }

    /** searchAssets 事实摘录：关键词 → 命中资产（类型+名，前 6 个） */
    private static String searchFact(String kw, ArrayNode hits) {
        StringBuilder sb = new StringBuilder("[searchAssets] \"").append(kw).append("\" → ");
        int n = 0;
        for (JsonNode h : hits) {
            if (n++ >= 6) {
                sb.append('…');
                break;
            }
            if (n > 1) {
                sb.append('、');
            }
            sb.append(h.path("type").asText()).append(' ').append(h.path("name").asText());
        }
        return sb.toString();
    }

    /** findValue 事实摘录：取值 → 维度=存储码（前 4 组，码值过滤直接采信） */
    private static String findValueFact(String kw, ArrayNode hits) {
        StringBuilder sb = new StringBuilder("[findValue] 取值\"").append(kw).append("\" → ");
        int n = 0;
        for (JsonNode h : hits) {
            if (n++ >= 4) {
                sb.append('…');
                break;
            }
            if (n > 1) {
                sb.append('、');
            }
            sb.append(h.path("dimension").asText()).append('=').append(h.path("code").asText());
        }
        return sb.toString();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 实体定位：英文名/源表全名/中文名任一命中 */
    private EntityDef findEntity(String nameOrTable) {
        if (nameOrTable == null || nameOrTable.trim().isEmpty()) {
            return null;
        }
        String key = nameOrTable.trim();
        for (EntityDef ent : layer.entities()) {
            if (key.equals(ent.getName()) || key.equals(ent.getTable()) || key.equals(ent.getDisplayName())) {
                return ent;
            }
        }
        return null;
    }

    private static boolean hit(String kw, String... candidates) {
        for (String c : candidates) {
            if (c != null && c.toLowerCase(Locale.ROOT).contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 别名列表包含匹配（维度/码值域取值的口语别名） */
    private static boolean hitAliases(String kw, List<String> aliases) {
        if (aliases == null) {
            return false;
        }
        for (String a : aliases) {
            if (a != null && a.toLowerCase(Locale.ROOT).contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 码值域内首个 label/code/别名命中的取值（无命中返回 null） */
    private static ValueDomainDef.DomainValue matchDomainValue(String kw, ValueDomainDef domain) {
        for (ValueDomainDef.DomainValue v : nzValues(domain)) {
            if (hit(kw, v.getLabel(), v.getCode()) || hitAliases(kw, v.getAliases())) {
                return v;
            }
        }
        return null;
    }

    /** 拥有指定码值域的维度清单（显式声明或实体字段声明回退均纳入） */
    private List<String> dimsOfDomain(ValueDomainDef domain) {
        List<String> out = new ArrayList<>();
        for (String dimName : layer.dimensionMap().keySet()) {
            if (layer.domainOfDim(dimName) == domain) {
                out.add(dimName);
            }
        }
        return out;
    }

    private static List<ValueDomainDef.DomainValue> nzValues(ValueDomainDef domain) {
        return domain == null || domain.getValues() == null
                ? new ArrayList<ValueDomainDef.DomainValue>() : domain.getValues();
    }

    private static ObjectNode item(String type, String name, String display) {
        ObjectNode n = OM.createObjectNode();
        n.put("type", type);
        n.put("name", name);
        if (display != null) {
            n.put("display_name", display);
        }
        return n;
    }

    private static ArrayNode slice(ArrayNode arr, int limit) {
        ArrayNode out = OM.createArrayNode();
        for (int i = 0; i < limit; i++) {
            out.add(arr.get(i));
        }
        return out;
    }

    private String writeJson(ObjectNode node) {
        try {
            return OM.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"error\":\"结果序列化失败: " + e.getMessage() + "\"}";
        }
    }
}
