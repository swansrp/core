package com.bidr.insight.smartquery.validate;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.layer.RelationDef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: AssetConsistencyValidator
 * Description: 八类资产草稿交叉一致性校验（发布前置检）：以 entities 骨架为锚点，
 * 校验维度/指标/关系/概念/敏感字段/行权限/码值域的引用完整性——重点拦截「取消选表后
 * 人工四类残留悬空引用」。校验只读草稿 JSON，不连数据库；问题分 error（阻断发布）
 * 与 warn（提示不阻断），每条均带处理建议供管理员定位修改。
 *
 * @author Sharp
 * @since 2026/8/19
 */
public class AssetConsistencyValidator {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 三段式列引用 db.tbl.col（指标公式/维度表达式中提取） */
    private static final Pattern COL_REF = Pattern.compile("([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)");

    /** 校验结果：issues 按资产类型归组，error 阻断发布、warn 仅提示 */
    public static class Result {
        private final List<Issue> issues = new ArrayList<>();

        public void error(String assetType, String message) {
            issues.add(new Issue(assetType, "error", message));
        }

        public void warn(String assetType, String message) {
            issues.add(new Issue(assetType, "warn", message));
        }

        public List<Issue> getIssues() {
            return issues;
        }

        public boolean hasErrors() {
            return issues.stream().anyMatch(i -> "error".equals(i.level));
        }

        public List<String> errorMessages() {
            List<String> msgs = new ArrayList<>();
            for (Issue i : issues) {
                if ("error".equals(i.level)) {
                    msgs.add("[" + i.assetType + "] " + i.message);
                }
            }
            return msgs;
        }
    }

    /** 单条问题：assetType 供前端定位到对应资产页签 */
    @Data
    public static class Issue {
        private final String assetType;
        private final String level;
        private final String message;

        public Issue(String assetType, String level, String message) {
            this.assetType = assetType;
            this.level = level;
            this.message = message;
        }
    }

    /**
     * 校验入口：contents 键为资产类型（entities/metrics/...），值为 JSON 全文；
     * 缺资产按空处理（与 SemanticLayer.fromContent 口径一致）
     */
    public static Result validate(Map<String, String> contents) {
        Result r = new Result();
        List<EntityDef> entities = readList(contents, "entities", EntityDef.class, r);
        List<DimensionDef> dimensions = readList(contents, "dimensions", DimensionDef.class, r);
        List<MetricDef> metrics = readList(contents, "metrics", MetricDef.class, r);
        List<RelationDef> relations = readList(contents, "relations", RelationDef.class, r);
        JsonNode conceptsRoot = readTree(contents, "concepts", r);
        JsonNode sensitiveRoot = readTree(contents, "sensitive-fields", r);
        JsonNode rowPoliciesRoot = readTree(contents, "row-policies", r);
        JsonNode domainsRoot = readTree(contents, "value-domains", r);

        // 实体索引：name → EntityDef；table(db.tbl) → EntityDef；实体字段名集合
        Map<String, EntityDef> byName = new HashMap<>();
        Map<String, EntityDef> byTable = new HashMap<>();
        if (entities.isEmpty()) {
            r.error("entities", "实体清单为空，发布后无法问数；请先选表并生成骨架");
        }
        for (EntityDef e : entities) {
            if (e.getName() == null || e.getName().isEmpty()) {
                r.error("entities", "存在无名实体，请检查实体定义");
                continue;
            }
            if (byName.put(e.getName(), e) != null) {
                r.error("entities", "实体名重复：'" + e.getName() + "'，请合并或删除其一");
            }
            if (e.getTable() == null || e.getTable().isEmpty()) {
                r.error("entities", "实体 '" + e.getName() + "' 缺少物理表（table），请补全");
            } else if (byTable.put(e.getTable(), e) != null) {
                r.warn("entities", "物理表 '" + e.getTable() + "' 被多个实体引用，查询可能歧义");
            }
        }
        Map<String, DimensionDef> dimByName = new HashMap<>();
        for (DimensionDef d : dimensions) {
            if (d.getName() != null && !d.getName().isEmpty() && dimByName.put(d.getName(), d) != null) {
                r.error("dimensions", "维度名重复：'" + d.getName() + "'，请合并或删除其一");
            }
        }

        checkDimensions(dimensions, byTable, r);
        checkMetrics(metrics, byTable, dimByName, r);
        checkRelations(relations, byName, r);
        checkConcepts(conceptsRoot, byName, dimByName, r);
        checkSensitiveFields(sensitiveRoot, byName, r);
        checkRowPolicies(rowPoliciesRoot, byTable, r);
        checkValueDomains(domainsRoot, entities, dimensions, r);
        return r;
    }

    /** 维度：表达式 db.tbl.col 的表与列必须落在实体骨架内 */
    private static void checkDimensions(List<DimensionDef> dimensions, Map<String, EntityDef> byTable, Result r) {
        for (DimensionDef d : dimensions) {
            if (d.getName() == null || d.getName().isEmpty()) {
                r.error("dimensions", "存在无名维度，请检查维度定义");
                continue;
            }
            if (d.getExpression() == null || d.getExpression().isEmpty()) {
                r.error("dimensions", "维度 '" + d.getName() + "' 缺少表达式（expression）");
                continue;
            }
            Matcher m = COL_REF.matcher(d.getExpression());
            if (m.find()) {
                checkColumnRef("dimensions", "维度 '" + d.getName() + "'", m.group(1) + "." + m.group(2),
                        m.group(3), byTable, r);
            }
        }
    }

    /** 指标：公式列引用/source_table 落在实体骨架内；supported_dimensions/depends_on 引用存在 */
    private static void checkMetrics(List<MetricDef> metrics, Map<String, EntityDef> byTable,
                                     Map<String, DimensionDef> dimByName, Result r) {
        Set<String> metricNames = new HashSet<>();
        for (MetricDef m : metrics) {
            if (m.getName() != null && !m.getName().isEmpty() && !metricNames.add(m.getName())) {
                r.error("metrics", "指标名重复：'" + m.getName() + "'，请合并或删除其一");
            }
        }
        for (MetricDef m : metrics) {
            String who = "指标 '" + m.getName() + "'";
            if (m.getFormula() != null) {
                Matcher cm = COL_REF.matcher(m.getFormula());
                Set<String> seen = new HashSet<>();
                while (cm.find()) {
                    String table = cm.group(1) + "." + cm.group(2);
                    if (seen.add(table + "." + cm.group(3))) {
                        checkColumnRef("metrics", who + " 公式", table, cm.group(3), byTable, r);
                    }
                }
            }
            if (m.getSourceTable() != null && !m.getSourceTable().isEmpty() && !byTable.containsKey(m.getSourceTable())) {
                r.error("metrics", who + " 的 source_table '" + m.getSourceTable()
                        + "' 不在实体清单（表可能已取消勾选），请修改指标或删除该指标");
            }
            if (m.getSupportedDimensions() != null) {
                for (String dim : m.getSupportedDimensions()) {
                    if (!dimByName.containsKey(dim)) {
                        r.error("metrics", who + " 引用的维度 '" + dim + "' 不存在，请从 supported_dimensions 中移除");
                    }
                }
            }
            if (m.getDependsOn() != null) {
                for (String dep : m.getDependsOn()) {
                    if (!metricNames.contains(dep)) {
                        r.error("metrics", who + " 依赖的指标 '" + dep + "' 不存在，请修正 depends_on");
                    }
                }
            }
        }
        if (metrics.isEmpty()) {
            r.warn("metrics", "尚无指标资产，发布后只能查明细无法算指标；可用 LLM 生成或手工补充");
        }
    }

    /** 关系：两端实体与 JOIN 列必须存在 */
    private static void checkRelations(List<RelationDef> relations, Map<String, EntityDef> byName, Result r) {
        for (RelationDef rel : relations) {
            String who = "关系 '" + (rel.getName() == null ? rel.getFromEntity() + "→" + rel.getToEntity() : rel.getName()) + "'";
            EntityDef from = rel.getFromEntity() == null ? null : byName.get(rel.getFromEntity());
            EntityDef to = rel.getToEntity() == null ? null : byName.get(rel.getToEntity());
            if (from == null) {
                r.error("relations", who + " 的 from_entity '" + rel.getFromEntity()
                        + "' 不存在（表可能已取消勾选），请修改或删除该关系");
            }
            if (to == null) {
                r.error("relations", who + " 的 to_entity '" + rel.getToEntity()
                        + "' 不存在（表可能已取消勾选），请修改或删除该关系");
            }
            if (rel.getJoin() != null) {
                for (RelationDef.JoinKey jk : rel.getJoin()) {
                    if (from != null && !hasField(from, jk.getLeft())) {
                        r.error("relations", who + " 的 join.left '" + jk.getLeft()
                                + "' 不在实体 '" + from.getName() + "' 字段中，请修正");
                    }
                    if (to != null && !hasField(to, jk.getRight())) {
                        r.error("relations", who + " 的 join.right '" + jk.getRight()
                                + "' 不在实体 '" + to.getName() + "' 字段中，请修正");
                    }
                }
            }
        }
    }

    /** 业务概念：归属实体与展开维度必须存在 */
    private static void checkConcepts(JsonNode root, Map<String, EntityDef> byName,
                                      Map<String, DimensionDef> dimByName, Result r) {
        if (root == null) {
            return;
        }
        for (JsonNode c : root.path("concepts")) {
            String who = "概念 '" + c.path("name").asText("") + "'";
            String entity = c.path("entity").asText("");
            if (!entity.isEmpty() && !byName.containsKey(entity)) {
                r.error("concepts", who + " 引用的实体 '" + entity
                        + "' 不存在（表可能已取消勾选），请修改或删除该概念");
            }
            String dim = c.path("expands_to").path("dimension").asText("");
            if (!dim.isEmpty() && !dimByName.containsKey(dim)) {
                r.error("concepts", who + " 展开的维度 '" + dim + "' 不存在，请修正 expands_to.dimension");
            }
        }
    }

    /** 敏感字段：entity/field/replace_field 落在实体骨架内（entity 为空=全局，仅提示） */
    private static void checkSensitiveFields(JsonNode root, Map<String, EntityDef> byName, Result r) {
        if (root == null) {
            return;
        }
        for (JsonNode f : root.path("fields")) {
            String entity = f.path("entity").asText("");
            String field = f.path("field").asText("");
            String who = "敏感字段 '" + (entity.isEmpty() ? "*" : entity) + "." + field + "'";
            if (entity.isEmpty()) {
                continue;
            }
            EntityDef ent = byName.get(entity);
            if (ent == null) {
                r.error("sensitive-fields", who + " 引用的实体不存在（表可能已取消勾选），请修改或删除该条");
                continue;
            }
            if (!hasField(ent, field)) {
                r.error("sensitive-fields", who + " 的字段不在实体 '" + entity + "' 中，请修正");
            }
            String replace = f.path("replace_field").asText("");
            if (!replace.isEmpty() && !hasField(ent, replace)) {
                r.error("sensitive-fields", who + " 的 replace_field '" + replace
                        + "' 不在实体 '" + entity + "' 中，请修正");
            }
        }
    }

    /** 行级权限：table/column 落在实体骨架内、op 白名单、value 模板变量白名单、
     *  in/not_in 须配数组（渲染期 fail-closed 的前置闸：结构错不过发布） */
    private static void checkRowPolicies(JsonNode root, Map<String, EntityDef> byTable, Result r) {
        if (root == null) {
            return;
        }
        Set<String> opWhitelist = new HashSet<>(Arrays.asList(
                "=", "!=", ">", ">=", "<", "<=", "in", "not_in"));
        Set<String> userVars = new HashSet<>(Arrays.asList(
                "id", "userId", "customerNumber", "operator", "userName", "name", "nickName"));
        for (JsonNode t : root.path("tables")) {
            String table = t.path("table").asText("");
            EntityDef ent = byTable.get(table);
            if (table.isEmpty()) {
                r.error("row-policies", "存在无 table 的行权限声明，请补全");
                continue;
            }
            if (ent == null) {
                r.error("row-policies", "表 '" + table + "' 未注册为实体（可能已取消勾选），请修改或删除该条");
                continue;
            }
            for (JsonNode p : t.path("policies")) {
                String column = p.path("column").asText("");
                String op = p.path("op").asText("");
                String who = "行权限 '" + table + "." + column + "'";
                if (column.isEmpty() || !hasField(ent, column)) {
                    r.error("row-policies", who + " 的过滤列不在实体 '" + ent.getName() + "' 字段清单中，请修正");
                    continue;
                }
                if (!opWhitelist.contains(op)) {
                    r.error("row-policies", who + " 操作符 '" + op + "' 不在白名单（= != > >= < <= in not_in）");
                    continue;
                }
                JsonNode value = p.path("value");
                if (value.isMissingNode() || value.isNull()) {
                    r.error("row-policies", who + " 缺少 value（常量或 ${user.xxx} 登录态模板）");
                    continue;
                }
                if (("in".equals(op) || "not_in".equals(op)) && !value.isArray()) {
                    r.error("row-policies", who + " 操作符为 " + op + "，value 必须为数组");
                    continue;
                }
                // 模板变量白名单：内置属性或 attr.KEY（attr 为登录 extraData 扩展位）
                Matcher m = Pattern.compile("\\$\\{user\\.([A-Za-z0-9_.]+)}").matcher(value.isTextual() ? value.asText() : "");
                while (m.find()) {
                    String key = m.group(1);
                    if (!userVars.contains(key) && !key.startsWith("attr")) {
                        r.error("row-policies", who + " 模板变量 ${user." + key
                                + "} 不在白名单（id/customerNumber/userName/name/attr.KEY）");
                    }
                }
            }
        }
    }

    /** 码值域：实体字段引用的 domain key 必须存在（维度不设级绑定，双路解析无需校验） */
    private static void checkValueDomains(JsonNode root, List<EntityDef> entities,
                                          List<DimensionDef> dimensions, Result r) {
        Set<String> domainKeys = new HashSet<>();
        if (root != null) {
            root.path("domains").fieldNames().forEachRemaining(domainKeys::add);
        }
        for (EntityDef e : entities) {
            if (e.getFields() == null) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (f.getValueDomain() != null && !f.getValueDomain().isEmpty()
                        && !domainKeys.contains(f.getValueDomain())) {
                    r.error("value-domains", "实体 '" + e.getName() + "' 字段 '" + f.getName()
                            + "' 引用的码值域 '" + f.getValueDomain() + "' 不存在，请补充码值域或清除引用");
                }
            }
        }
    }

    /** 三段式列引用校验：表须在实体清单、列须在该实体字段中 */
    private static void checkColumnRef(String assetType, String who, String table, String column,
                                       Map<String, EntityDef> byTable, Result r) {
        EntityDef ent = byTable.get(table);
        if (ent == null) {
            r.error(assetType, who + " 引用的表 '" + table
                    + "' 不在实体清单（表可能已取消勾选），请修正引用或重新勾选该表");
            return;
        }
        if (!hasField(ent, column)) {
            r.error(assetType, who + " 引用的列 '" + table + "." + column
                    + "' 不在实体 '" + ent.getName() + "' 字段中，请修正引用");
        }
    }

    private static boolean hasField(EntityDef entity, String fieldName) {
        if (fieldName == null || entity.getFields() == null) {
            return false;
        }
        for (EntityDef.EntityFieldDef f : entity.getFields()) {
            if (fieldName.equals(f.getName())) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<T> readList(Map<String, String> contents, String assetType,
                                        Class<T> type, Result r) {
        String content = contents.get(assetType);
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<T> list = OM.readValue(content, OM.getTypeFactory().constructCollectionType(List.class, type));
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            r.error(assetType, "资产 JSON 解析失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static JsonNode readTree(Map<String, String> contents, String assetType, Result r) {
        String content = contents.get(assetType);
        if (content == null || content.trim().isEmpty()) {
            return null;
        }
        try {
            return OM.readTree(content);
        } catch (Exception e) {
            r.error(assetType, "资产 JSON 解析失败：" + e.getMessage());
            return null;
        }
    }
}
