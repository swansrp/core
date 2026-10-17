package com.bidr.insight.smartquery.semantic;

import com.bidr.insight.smartquery.layer.ConceptDef;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.RelationDef;
import com.bidr.insight.smartquery.layer.RowPolicyDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.sqlgen.SqlGenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Title: SemanticLayer
 * Description: 语义层加载与共享查找（对应 Python SqlGenerator/Validator 的 __init__ 部分）。
 * 启动时从 classpath smartquery/*.json 加载 6 类真源资产并构建索引；运行期只读，
 * 语义层变更走维护流程（改真源 → 重新生成 → 回归），严禁运行期修改。
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Component
public class SemanticLayer {

    private static final String DEFAULT_ASSET_PREFIX = "/smartquery/";

    /** 资产 classpath 前缀：运行期仅内置 default Agent 使用；DB 驱动的 Agent 只认发布资产，不回退 */
    private String assetPrefix = DEFAULT_ASSET_PREFIX;

    /** DB 驱动的 Agent 资产（资产名 → JSON 全文）；非空时优先于 classpath 读取 */
    private Map<String, String> inlineAssets;

    private List<EntityDef> entities = new ArrayList<>();
    private List<RelationDef> relations = new ArrayList<>();
    private List<MetricDef> metrics = new ArrayList<>();
    private List<DimensionDef> dimensions = new ArrayList<>();

    private Map<String, EntityDef> entityMap = new HashMap<>();
    private Map<String, MetricDef> metricMap = new HashMap<>();
    private Map<String, DimensionDef> dimensionMap = new HashMap<>();
    /** 表名(db.tbl) → 实体名 */
    private Map<String, String> tableToEntity = new HashMap<>();
    /** "db.tbl.col" → 码值域名 */
    private Map<String, String> fieldToDomain = new HashMap<>();
    /** 码值域名 → 码值域定义 */
    private Map<String, ValueDomainDef> domains = new HashMap<>();
    /** 业务概念完整定义清单（conceptDetail 按需展开供给模型） */
    private List<ConceptDef> concepts = new ArrayList<>();
    /** 业务概念名集合（filters 中禁止直接引用，§6.6.2） */
    private Set<String> conceptNames = new HashSet<>();
    private Map<String, ConceptDef> conceptMap = new LinkedHashMap<>();
    /** 实体无向邻接表（JOIN 路径可达性） */
    private Map<String, Set<String>> entAdj = new HashMap<>();
    /** 维度分组目录（concepts.json hierarchy 字段：分级目录两级导航的组层，空=未配置） */
    private List<DimensionGroup> dimensionGroups = new ArrayList<>();
    /** 维度名 → 组名索引（hierarchy 加载时构建） */
    private Map<String, String> dimToGroup = new HashMap<>();
    /** 行级权限策略（表 db.tbl → 策略清单，渲染期注入 WHERE；DB 发布资产携带，缺失按空） */
    private Map<String, List<RowPolicyDef.Policy>> rowPolicies = new HashMap<>();

    @PostConstruct
    public void init() {
        ObjectMapper om = new ObjectMapper();
        try {
            entities = readList(om, "entities.json", EntityDef.class);
            relations = readList(om, "relations.json", RelationDef.class);
            metrics = readList(om, "metrics.json", MetricDef.class);
            dimensions = readList(om, "dimensions.json", DimensionDef.class);
            loadDomains(om);
            loadConcepts(om);
            loadRowPolicies(om);
        } catch (IOException e) {
            throw new IllegalStateException("smart-query 语义层资产加载失败", e);
        }
        buildIndexes();
        log.info("smart-query 语义层加载完成：entities={}, relations={}, metrics={}, dimensions={}, domains={}, concepts={}",
                entities.size(), relations.size(), metrics.size(), dimensions.size(), domains.size(), conceptNames.size());
    }

    private <T> List<T> readList(ObjectMapper om, String asset, Class<T> type) throws IOException {
        try (InputStream in = openAsset(asset)) {
            return om.readValue(in, om.getTypeFactory().constructCollectionType(List.class, type));
        }
    }

    private void loadDomains(ObjectMapper om) throws IOException {
        try (InputStream in = openAsset("value-domains.json")) {
            JsonNode root = om.readTree(in);
            JsonNode domainsNode = root.get("domains");
            if (domainsNode != null) {
                domainsNode.fields().forEachRemaining(e -> {
                    try {
                        domains.put(e.getKey(), om.treeToValue(e.getValue(), ValueDomainDef.class));
                    } catch (IOException ex) {
                        throw new IllegalStateException("码值域 '" + e.getKey() + "' 解析失败", ex);
                    }
                });
            }
        } catch (IllegalStateException e) {
            if (inlineAssets == null || inlineAssets.containsKey("value-domains.json")) {
                throw e;
            }
            // DB 驱动且资产缺失：跳过
        }
    }

    private void loadConcepts(ObjectMapper om) throws IOException {
        try (InputStream in = openAsset("concepts.json")) {
            JsonNode root = om.readTree(in);
            JsonNode conceptsNode = root.get("concepts");
            if (conceptsNode != null && conceptsNode.isArray()) {
                for (JsonNode c : conceptsNode) {
                    if (c.hasNonNull("name")) {
                        ConceptDef def = om.treeToValue(c, ConceptDef.class);
                        concepts.add(def);
                        conceptNames.add(def.getName());
                        conceptMap.put(def.getName(), def);
                    }
                }
            }
            JsonNode hierarchy = root.get("hierarchy");
            if (hierarchy != null && hierarchy.isArray()) {
                for (JsonNode g : hierarchy) {
                    String gname = g.path("name").asText("");
                    if (gname.isEmpty()) {
                        continue;
                    }
                    DimensionGroup group = new DimensionGroup(gname);
                    for (JsonNode m : g.path("members")) {
                        String dim = m.asText("");
                        if (!dim.isEmpty() && !dimToGroup.containsKey(dim)) {
                            group.getMembers().add(dim);
                            dimToGroup.put(dim, gname);
                        }
                    }
                    if (!group.getMembers().isEmpty()) {
                        dimensionGroups.add(group);
                    }
                }
            }
        } catch (IllegalStateException e) {
            if (inlineAssets == null || inlineAssets.containsKey("concepts.json")) {
                throw e;
            }
            // DB 驱动且资产缺失：跳过
        }
    }

    /** 行级权限资产加载（row-policies.json：tables[].policies[]，缺失按空——旧发布资产兼容） */
    private void loadRowPolicies(ObjectMapper om) {
        try {
            JsonNode root;
            if (inlineAssets != null) {
                String content = inlineAssets.get("row-policies.json");
                if (content == null) {
                    return;
                }
                root = om.readTree(content);
            } else {
                try (InputStream in = SemanticLayer.class.getResourceAsStream(assetPrefix + "row-policies.json")) {
                    if (in == null) {
                        return;
                    }
                    root = om.readTree(in);
                }
            }
            for (JsonNode t : root.path("tables")) {
                String table = t.path("table").asText("");
                if (table.isEmpty()) {
                    continue;
                }
                List<RowPolicyDef.Policy> list = rowPolicies.computeIfAbsent(table, k -> new ArrayList<>());
                for (JsonNode p : t.path("policies")) {
                    list.add(om.treeToValue(p, RowPolicyDef.Policy.class));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("row-policies 资产解析失败", e);
        }
    }

    private InputStream openAsset(String name) {
        if (inlineAssets != null) {
            String content = inlineAssets.get(name);
            if (content == null) {
                throw new IllegalStateException("smart-query 资产缺失: " + name);
            }
            return new java.io.ByteArrayInputStream(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        InputStream in = SemanticLayer.class.getResourceAsStream(assetPrefix + name);
        if (in == null) {
            throw new IllegalStateException("smart-query 真源文件缺失: " + assetPrefix + name);
        }
        return in;
    }

    /**
     * 非 Spring 场景按指定 classpath 前缀构建独立语义层实例（多 Agent）。
     * 资产缺失/解析失败抛 IllegalStateException，由调用方转译为业务异常
     */
    public static SemanticLayer fromClasspath(String prefix) {
        SemanticLayer layer = new SemanticLayer();
        layer.assetPrefix = prefix;
        layer.init();
        return layer;
    }

    /**
     * 从 DB 资产 JSON 全文构建语义层实例（多 Agent 发布态资产）。
     * 键为资产文件名（entities.json 等六类），缺失的资产按空处理（允许只发布部分资产）
     */
    public static SemanticLayer fromContent(Map<String, String> assets) {
        SemanticLayer layer = new SemanticLayer();
        layer.inlineAssets = assets;
        ObjectMapper om = new ObjectMapper();
        try {
            layer.entities = readListSafe(om, assets, "entities.json", EntityDef.class);
            layer.relations = readListSafe(om, assets, "relations.json", RelationDef.class);
            layer.metrics = readListSafe(om, assets, "metrics.json", MetricDef.class);
            layer.dimensions = readListSafe(om, assets, "dimensions.json", DimensionDef.class);
            layer.loadDomains(om);
            layer.loadConcepts(om);
            layer.loadRowPolicies(om);
        } catch (IOException e) {
            throw new IllegalStateException("Agent 语义层资产解析失败", e);
        }
        layer.buildIndexes();
        return layer;
    }

    /** 资产缺失时返回空列表（草稿阶段允许只有部分资产） */
    private static <T> List<T> readListSafe(ObjectMapper om, Map<String, String> assets, String asset, Class<T> type) throws IOException {
        if (!assets.containsKey(asset)) {
            return new ArrayList<>();
        }
        return om.readValue(assets.get(asset), om.getTypeFactory().constructCollectionType(List.class, type));
    }

    private void buildIndexes() {
        for (EntityDef e : entities) {
            entityMap.put(e.getName(), e);
            if (e.getTable() != null) {
                tableToEntity.put(e.getTable(), e.getName());
            }
            for (EntityDef.EntityFieldDef f : nz(e.getFields())) {
                if (f.getValueDomain() != null && !f.getValueDomain().isEmpty()) {
                    fieldToDomain.put(e.getTable() + "." + f.getName(), f.getValueDomain());
                }
            }
        }
        for (MetricDef m : metrics) {
            metricMap.put(m.getName(), m);
        }
        for (DimensionDef d : dimensions) {
            dimensionMap.put(d.getName(), d);
        }
        for (RelationDef r : relations) {
            entAdj.computeIfAbsent(r.getFromEntity(), k -> new HashSet<>()).add(r.getToEntity());
            entAdj.computeIfAbsent(r.getToEntity(), k -> new HashSet<>()).add(r.getFromEntity());
        }
    }

    /** 表的行级权限策略（无配置返回空清单，渲染期 fail-closed 判定在 SqlGenerator） */
    public List<RowPolicyDef.Policy> rowPoliciesOf(String table) {
        List<RowPolicyDef.Policy> list = rowPolicies.get(table);
        return list == null ? new ArrayList<>() : list;
    }

    /** 维度分组目录（分级目录的组层；空=未配置 hierarchy，调用方回落全量回显） */
    public List<DimensionGroup> dimensionGroups() {
        return dimensionGroups;
    }

    /** 维度所属组名（未入组返回 null，目录工具归「其他」桶） */
    public String groupOfDimension(String dimName) {
        return dimToGroup.get(dimName);
    }

    /** 维度分组（concepts.json hierarchy 条目：组名+成员维度名清单） */
    public static class DimensionGroup {
        private final String name;
        private final List<String> members = new ArrayList<>();

        public DimensionGroup(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public List<String> getMembers() {
            return members;
        }
    }

    private static <T> List<T> nz(List<T> list) {
        return list == null ? new ArrayList<T>() : list;
    }

    // ── 表达式解析（等价 Python _split_expr）─────────────────────

    /** 'db.tbl.col' → [db.tbl, col]；格式非法抛 SqlGenException */
    public static String[] splitExpr(String expression) {
        String[] parts = expression.split("\\.");
        if (parts.length != 3) {
            throw new SqlGenException("维度表达式格式非法: " + expression);
        }
        return new String[]{parts[0] + "." + parts[1], parts[2]};
    }

    /** 维度所属实体（严格版：表未注册抛异常，等价 Python _dim_entity） */
    public String dimEntityOf(String dimName) {
        DimensionDef dim = dimensionMap.get(dimName);
        if (dim == null) {
            throw new SqlGenException("维度 '" + dimName + "' 未定义");
        }
        String table = splitExpr(dim.getExpression())[0];
        String entName = tableToEntity.get(table);
        if (entName == null) {
            throw new SqlGenException("维度 '" + dimName + "' 的表 '" + table + "' 未注册为实体");
        }
        return entName;
    }

    /** 维度所属实体（宽松版：无法定位返回 null，等价 Python _dim_entity_of） */
    public String dimEntityOfOrNull(String dimName) {
        DimensionDef dim = dimensionMap.get(dimName);
        if (dim == null || dim.getExpression() == null) {
            return null;
        }
        String[] parts = dim.getExpression().split("\\.");
        if (parts.length != 3) {
            return null;
        }
        return tableToEntity.get(parts[0] + "." + parts[1]);
    }

    // ── 值域（等价 Python _domain_of_dim / resolve_value / translate_back）──

    /** 维度的码值域定义（双路解析：实体字段声明优先，回退域键=维度名登记约定） */
    public ValueDomainDef domainOfDim(String dimName) {
        DimensionDef dim = dimensionMap.get(dimName);
        if (dim == null) {
            return null;
        }
        String[] tc = splitExpr(dim.getExpression());
        String dname = fieldToDomain.get(tc[0] + "." + tc[1]);
        ValueDomainDef d = dname == null ? null : domains.get(dname);
        return d != null ? d : domains.get(dimName);
    }

    /** label/alias → 存储值（code 或 label）；无值域或无法解析时原样返回 */
    public Object resolveValue(String dimName, Object value) {
        ValueDomainDef domain = domainOfDim(dimName);
        if (domain == null || value == null) {
            return value;
        }
        for (ValueDomainDef.DomainValue v : nz(domain.getValues())) {
            boolean matched = Objects.equals(value, v.getLabel()) || Objects.equals(value, v.getCode())
                    || (v.getAliases() != null && v.getAliases().contains(value));
            if (matched) {
                return "code".equals(domain.getStoredAs()) ? v.getCode() : v.getLabel();
            }
        }
        return value;
    }

    /** code → label（结果展示用）；无匹配返回 null */
    public String translateBack(String dimName, Object code) {
        ValueDomainDef domain = domainOfDim(dimName);
        if (domain == null || !"code".equals(domain.getStoredAs())) {
            return null;
        }
        for (ValueDomainDef.DomainValue v : nz(domain.getValues())) {
            if (Objects.equals(String.valueOf(code), String.valueOf(v.getCode()))) {
                return v.getLabel();
            }
        }
        return null;
    }

    /** 过滤值能否在值域中解析（label/alias/code 匹配，列表递归，等价 Python _resolve_value） */
    public boolean canResolveValue(Object value, ValueDomainDef domain) {
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (!canResolveValue(item, domain)) {
                    return false;
                }
            }
            return true;
        }
        for (ValueDomainDef.DomainValue v : nz(domain.getValues())) {
            if (Objects.equals(value, v.getLabel()) || Objects.equals(value, v.getCode())) {
                return true;
            }
            if (v.getAliases() != null && v.getAliases().contains(value)) {
                return true;
            }
        }
        return false;
    }

    // ── JOIN 路径（等价 Python _join_path / _has_join_path）──────

    /** BFS 找 from → to 的关系路径，返回边列表 [(src, dst, rel)]；不可达抛异常 */
    public List<Object[]> joinPath(String fromEnt, String toEnt) {
        if (fromEnt.equals(toEnt)) {
            return new ArrayList<>();
        }
        Map<String, List<RelationDef>> adj = new LinkedHashMap<>();
        for (RelationDef r : relations) {
            adj.computeIfAbsent(r.getFromEntity(), k -> new ArrayList<>()).add(r);
            adj.computeIfAbsent(r.getToEntity(), k -> new ArrayList<>()).add(r);
        }
        Map<String, Object[]> prev = new HashMap<>();
        prev.put(fromEnt, null);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromEnt);
        while (!queue.isEmpty()) {
            String cur = queue.pollFirst();
            for (RelationDef r : nz(adj.get(cur))) {
                String nxt = r.getFromEntity().equals(cur) ? r.getToEntity() : r.getFromEntity();
                if (!prev.containsKey(nxt)) {
                    prev.put(nxt, new Object[]{cur, r});
                    if (nxt.equals(toEnt)) {
                        List<Object[]> path = new ArrayList<>();
                        String node = nxt;
                        while (prev.get(node) != null) {
                            Object[] p = prev.get(node);
                            String parent = (String) p[0];
                            path.add(new Object[]{parent, node, p[1]});
                            node = parent;
                        }
                        java.util.Collections.reverse(path);
                        return path;
                    }
                    queue.addLast(nxt);
                }
            }
        }
        throw new SqlGenException("实体 " + fromEnt + " 与 " + toEnt + " 之间无 JOIN 路径");
    }

    /** relations 图上 from → to 是否可达（无向 BFS） */
    public boolean hasJoinPath(String fromEnt, String toEnt) {
        if (fromEnt.equals(toEnt)) {
            return true;
        }
        Set<String> seen = new HashSet<>();
        seen.add(fromEnt);
        Deque<String> queue = new ArrayDeque<>();
        queue.add(fromEnt);
        while (!queue.isEmpty()) {
            String cur = queue.pollFirst();
            for (String nxt : nzSet(entAdj.get(cur))) {
                if (nxt.equals(toEnt)) {
                    return true;
                }
                if (seen.add(nxt)) {
                    queue.addLast(nxt);
                }
            }
        }
        return false;
    }

    private static Set<String> nzSet(Set<String> set) {
        return set == null ? new HashSet<String>() : set;
    }

    // ── getter ────────────────────────────────────────────────

    public Map<String, EntityDef> entityMap() {
        return entityMap;
    }

    public Map<String, MetricDef> metricMap() {
        return metricMap;
    }

    public Map<String, DimensionDef> dimensionMap() {
        return dimensionMap;
    }

    public Map<String, String> tableToEntity() {
        return tableToEntity;
    }

    public Map<String, String> fieldToDomain() {
        return fieldToDomain;
    }

    public Map<String, ValueDomainDef> domains() {
        return domains;
    }

    public Set<String> conceptNames() {
        return conceptNames;
    }

    public List<ConceptDef> concepts() {
        return concepts;
    }

    public Map<String, ConceptDef> conceptMap() {
        return conceptMap;
    }

    public List<EntityDef> entities() {
        return entities;
    }

    public List<RelationDef> relations() {
        return relations;
    }
}
