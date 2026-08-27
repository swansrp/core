package com.bidr.insight.smartquery.meta;

import com.bidr.insight.smartquery.constant.dict.DimensionGroupDict;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: ConceptsSupport
 * Description: concepts 资产专用工具（纯静态，从 SmartAgentMetaService 拆出）：
 * 启发式分组匹配（DimensionGroupDict 枚举字典一处定义两端消费）、
 * hierarchy 单源派生（实体列级 dim_group 是唯一真源，目录由实体抽出）、存量目录回填
 *
 * @author Sharp
 * @since 2026/8/23
 */
public final class ConceptsSupport {

    private static final ObjectMapper OM = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private ConceptsSupport() {
    }

    /** 启发式分组匹配（静态可测）：按 DimensionGroupDict 枚举声明顺序逐组尝试，命中返回组名，
     *  全部未命中返回 null（落「其他」桶）。字典一处定义两端消费：@MetaDict 自动注册系统字典供前端
     *  归类下拉取源，扩展匹配字段（词根/中文词/列段）供骨架预填列级归类；高置信词宁缺勿错，
     *  预填只是草稿，正式口径以实体确认页人工归类为准（人工改过打 edited 标，重建不覆盖） */
    public static String matchHierarchyGroup(DimensionDef d) {
        String name = d.getName() == null ? "" : d.getName().toLowerCase();
        String disp = d.getDisplayName() == null ? "" : d.getDisplayName();
        String expr = d.getExpression() == null ? "" : d.getExpression().toLowerCase();
        String colSeg = expr.substring(expr.lastIndexOf('.') + 1);
        for (DimensionGroupDict g : DimensionGroupDict.values()) {
            boolean hit = g.getEnWordRoots().stream().anyMatch(name::contains)
                    || g.getCnWords().stream().anyMatch(disp::contains)
                    || g.getColExact().contains(colSeg);
            if (hit) {
                return g.getLabel();
            }
        }
        return null;
    }

    /** 实体列级归类提取（静态可测）：维度列的「表.列」小写 → 组名，hierarchy 派生的唯一真源口径 */
    public static Map<String, String> exprGroupMap(List<EntityDef> entities) {
        Map<String, String> exprToGroup = new LinkedHashMap<>();
        if (entities == null) {
            return exprToGroup;
        }
        for (EntityDef e : entities) {
            if (FuncUtil.isEmpty(e.getTable()) || e.getFields() == null) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if ("dimension".equals(f.getRole()) && FuncUtil.isNotEmpty(f.getDimGroup())) {
                    exprToGroup.put((e.getTable() + "." + f.getName()).toLowerCase(), f.getDimGroup());
                }
            }
        }
        return exprToGroup;
    }

    /** hierarchy 派生（单源）：按「表.列」归类经维度表达式反查维度名归组——同表达式多维度一并入组，
     *  覆盖日期列派生的 _year 维度（与基础维度同表达式同组）；组序按维度草稿出现序，未归类维度不入目录（落「其他」桶） */
    public static List<Map<String, Object>> deriveHierarchy(List<DimensionDef> dims, Map<String, String> exprToGroup) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        if (dims == null || exprToGroup == null || exprToGroup.isEmpty()) {
            return new ArrayList<>();
        }
        for (DimensionDef d : dims) {
            if (d.getName() == null || d.getExpression() == null) {
                continue;
            }
            String group = exprToGroup.get(d.getExpression().toLowerCase());
            if (group != null) {
                grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(d.getName());
            }
        }
        List<Map<String, Object>> hierarchy = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", g.getKey());
            item.put("members", g.getValue());
            hierarchy.add(item);
        }
        return hierarchy;
    }

    /** 存量目录回填（一次性迁移口径）：hierarchy 里已有分组但实体列级归类为空的维度列，
     *  把组名写回 dim_group（人工编辑过的列不动）。老数据「目录面板维护的分组」迁进单源字段，
     *  此后 hierarchy 只出不进 */
    public static void backfillDimGroupsFromHierarchy(List<EntityDef> entities, List<DimensionDef> dims,
                                                      Map<String, String> dimToGroup) {
        if (entities == null || dims == null || dimToGroup == null || dimToGroup.isEmpty()) {
            return;
        }
        Map<String, String> exprToDim = new LinkedHashMap<>();
        for (DimensionDef d : dims) {
            if (d.getName() != null && d.getExpression() != null) {
                exprToDim.put(d.getExpression().toLowerCase(), d.getName());
            }
        }
        for (EntityDef e : entities) {
            if (FuncUtil.isEmpty(e.getTable()) || e.getFields() == null) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (!"dimension".equals(f.getRole()) || FuncUtil.isNotEmpty(f.getDimGroup())
                        || Boolean.TRUE.equals(f.getEdited())) {
                    continue;
                }
                String dimName = exprToDim.get((e.getTable() + "." + f.getName()).toLowerCase());
                String group = dimName == null ? null : dimToGroup.get(dimName);
                if (group != null) {
                    f.setDimGroup(group);
                }
            }
        }
    }

    /** concepts hierarchy 节点解析：组名 → 成员维度名集合（供回填反查），解析失败返回空 */
    public static Map<String, String> dimGroupOfHierarchy(JsonNode hierarchy) {
        Map<String, String> dimToGroup = new LinkedHashMap<>();
        if (hierarchy == null || !hierarchy.isArray()) {
            return dimToGroup;
        }
        for (JsonNode g : hierarchy) {
            String name = g.path("name").asText("");
            JsonNode members = g.path("members");
            if (name.isEmpty() || !members.isArray()) {
                continue;
            }
            for (JsonNode m : members) {
                String dim = m.asText("");
                if (!dim.isEmpty()) {
                    dimToGroup.put(dim, name);
                }
            }
        }
        return dimToGroup;
    }

    /** concepts 草稿初建：concepts 留空（人工/LLM 补充），hierarchy 由实体列级归类派生（可能为空数组） */
    public static String draftJson(List<Map<String, Object>> hierarchy) {
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("schema_version", 1);
        draft.put("concepts", new ArrayList<>());
        draft.put("hierarchy", hierarchy);
        try {
            return OM.writerWithDefaultPrettyPrinter().writeValueAsString(draft);
        } catch (Exception e) {
            throw new IllegalStateException("concepts 草稿序列化失败", e);
        }
    }

    /** concepts 根重建（LLM 合并/autonomous 落库共用，静态可测）：concepts 数组正常合并；
     *  hierarchy 恒保留现存——分级目录是实体列级归类的派生视图，LLM 来项的赋组不落库，
     *  避免绕过实体单源制造第二份真源；schema_version 固化 1.0 */
    public static ObjectNode rebuildRoot(JsonNode oldHierarchy, ArrayNode mergedConcepts) {
        ObjectNode wrap = OM.createObjectNode();
        wrap.put("schema_version", "1.0");
        wrap.set("concepts", mergedConcepts);
        if (oldHierarchy != null && oldHierarchy.isArray()) {
            wrap.set("hierarchy", oldHierarchy.deepCopy());
        } else {
            wrap.set("hierarchy", OM.createArrayNode());
        }
        return wrap;
    }

    /** 悬空概念同步清理（静态可测）：expands_to.dimension 非空且不在有效维度名集时删该概念
     *  （口径同 AssetAutoFixer.fixConcepts：展开维度消失后概念失去展开语义）；
     *  hierarchy 及其余字段原样保留；返回被删概念名清单，调用方据此落草稿与告警 */
    public static List<String> dropDanglingConcepts(JsonNode root, Set<String> validDimNames) {
        List<String> dropped = new ArrayList<>();
        if (root == null || !root.path("concepts").isArray() || validDimNames == null) {
            return dropped;
        }
        ArrayNode concepts = (ArrayNode) root.get("concepts");
        for (int i = concepts.size() - 1; i >= 0; i--) {
            JsonNode c = concepts.get(i);
            String dim = c.path("expands_to").path("dimension").asText("");
            if (!dim.isEmpty() && !validDimNames.contains(dim)) {
                dropped.add(c.path("name").asText(""));
                concepts.remove(i);
            }
        }
        return dropped;
    }
}
