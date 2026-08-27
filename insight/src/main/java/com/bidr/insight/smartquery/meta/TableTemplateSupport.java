package com.bidr.insight.smartquery.meta;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightTableTemplate;
import com.bidr.insight.smartquery.dao.repository.InsightTableTemplateService;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Title: TableTemplateSupport
 * Description: 表级资产模板（跨 Agent 复用）：实体/维度/码值域是物理表的确定性产物，
 * 某 Agent 人工认证的实体结论（角色/单位/归类/键/分区）按 数据源+表 沉淀为模板，
 * 其他 Agent 选到同表时骨架自动套用，免重复配置（认证结论一并携入，复用免再认证）。
 * 沉淀/导入均为页面显式动作（防个别 Agent 特化配置随保存自动污染共享模板）；
 * 消费口径：模板实体直接当「已确认旧稿」喂 carryConfirmedFields（合并语义零改动），
 * 本 Agent 自己草稿的确认结论优先（骨架链路先套模板后 carryConfirmedFromOld；
 * 显式导入跳过本 Agent 已确认实体）；物理表增删列照常随新骨架进出（模板只携同名列的人工结论）
 *
 * @author Sharp
 * @since 2026/8/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TableTemplateSupport {

    private final InsightTableTemplateService insightTableTemplateService;

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 沉淀（页面「保存到模板」显式动作）：已认证实体按 数据源+表 upsert 进模板库（覆盖式，最近沉淀者为准）；
     *  未认证实体与无物理表的实体不沉淀（人工填的不默认算认证，逐条点认证的才进模板）；
     *  onlyTables 非空时仅沉淀清单内表（认证本表按表同步）；单表失败不阻断其余沉淀；返回实际沉淀的表全名清单 */
    public List<String> sediment(String dsName, String agentCode, List<EntityDef> entities, List<String> onlyTables) {
        List<String> sedimented = new ArrayList<>();
        if (FuncUtil.isEmpty(dsName) || FuncUtil.isEmpty(entities)) {
            return sedimented;
        }
        Set<String> scoped = FuncUtil.isEmpty(onlyTables) ? null : new HashSet<>(onlyTables);
        for (EntityDef e : selectSedimentTargets(entities, scoped)) {
            try {
                upsert(dsName, e.getTable(), om.writeValueAsString(e), agentCode);
                sedimented.add(e.getTable());
            } catch (Exception ex) {
                log.warn("表模板沉淀失败（不阻断其余沉淀）: ds={}, table={}, {}", dsName, e.getTable(), ex.getMessage());
            }
        }
        return sedimented;
    }

    /** 沉淀候选筛选（静态可测）：仅已认证且有物理表的实体；
     *  scoped 非空时再限清单内表（认证本表按表同步，不把其他已认证表一并推上模板库） */
    public static List<EntityDef> selectSedimentTargets(List<EntityDef> entities, Set<String> scoped) {
        List<EntityDef> out = new ArrayList<>();
        if (FuncUtil.isEmpty(entities)) {
            return out;
        }
        for (EntityDef e : entities) {
            if (!Boolean.TRUE.equals(e.getCertified()) || FuncUtil.isEmpty(e.getTable())) {
                continue;
            }
            if (scoped != null && !scoped.contains(e.getTable())) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** 模板清单（页面「从模板导入」预览）：当前数据源下全部模板的表全名/来源 Agent/更新时间 */
    public List<InsightTableTemplate> listByDs(String dsName) {
        if (FuncUtil.isEmpty(dsName)) {
            return new ArrayList<>();
        }
        return insightTableTemplateService.select(
                new QueryWrapper<InsightTableTemplate>().eq("ds_name", dsName));
    }

    /** 模板库总览（跨数据源，模板库管理页左树数据源）：数据源名/表全名/来源 Agent/更新时间/字段数 */
    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (InsightTableTemplate t : insightTableTemplateService.select(
                new QueryWrapper<InsightTableTemplate>().orderByAsc("ds_name", "table_name"))) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ds_name", t.getDsName());
            row.put("table_name", t.getTableName());
            row.put("source_agent", t.getSourceAgent());
            row.put("update_at", t.getUpdateAt());
            row.put("field_count", fieldCountOf(t.getEntityJson()));
            out.add(row);
        }
        return out;
    }

    private Integer fieldCountOf(String entityJson) {
        try {
            EntityDef e = om.readValue(entityJson, EntityDef.class);
            return e.getFields() == null ? 0 : e.getFields().size();
        } catch (Exception ex) {
            return null;
        }
    }

    /** 模板详情（模板库管理页右侧编辑）：实体 JSON 解析后连同来源/更新时间一并返回；不存在返回 null */
    public Map<String, Object> templateDetail(String dsName, String tableName) {
        InsightTableTemplate t = findByKeys(dsName, tableName);
        if (t == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.put("entity", om.readValue(t.getEntityJson(), EntityDef.class));
        } catch (Exception e) {
            throw new NoticeException("模板 JSON 损坏无法解析: " + tableName);
        }
        out.put("source_agent", t.getSourceAgent());
        out.put("update_at", t.getUpdateAt());
        return out;
    }

    /** 手工编辑保存（模板库管理页）：实体身份以模板键（数据源+表）为准回写，
     *  upsert 覆盖；来源 Agent 保留原值（追溯沉淀来源），无原值记 manual */
    public Map<String, Object> updateTemplate(String dsName, String tableName, EntityDef entity) {
        if (entity == null) {
            throw new NoticeException("实体内容不能为空");
        }
        entity.setName(tableName);
        entity.setTable(tableName);
        InsightTableTemplate existed = findByKeys(dsName, tableName);
        String sourceAgent = existed != null && FuncUtil.isNotEmpty(existed.getSourceAgent())
                ? existed.getSourceAgent() : "manual";
        try {
            upsert(dsName, tableName, om.writeValueAsString(entity), sourceAgent);
        } catch (Exception e) {
            throw new NoticeException("模板保存失败: " + e.getMessage());
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("saved", 1);
        res.put("table_name", tableName);
        return res;
    }

    /** 删除模板（模板库管理页）：返回实际删除条数（0=不存在） */
    public Map<String, Object> deleteTemplate(String dsName, String tableName) {
        boolean ok = insightTableTemplateService.delete(
                new QueryWrapper<InsightTableTemplate>()
                        .eq("ds_name", dsName).eq("table_name", tableName));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("deleted", ok ? 1 : 0);
        return res;
    }

    private InsightTableTemplate findByKeys(String dsName, String tableName) {
        if (FuncUtil.isEmpty(dsName) || FuncUtil.isEmpty(tableName)) {
            throw new NoticeException("数据源名与表全名不能为空");
        }
        return insightTableTemplateService.selectOne(
                new QueryWrapper<InsightTableTemplate>()
                        .eq("ds_name", dsName).eq("table_name", tableName));
    }

    /** 单条 upsert（沉淀/手工编辑共用）：存在覆盖、不存在新增 */
    private void upsert(String dsName, String tableName, String entityJson, String sourceAgent) {
        InsightTableTemplate existed = insightTableTemplateService.selectOne(
                new QueryWrapper<InsightTableTemplate>()
                        .eq("ds_name", dsName).eq("table_name", tableName));
        if (existed == null) {
            InsightTableTemplate t = new InsightTableTemplate();
            t.setDsName(dsName);
            t.setTableName(tableName);
            t.setEntityJson(entityJson);
            t.setSourceAgent(sourceAgent);
            insightTableTemplateService.insert(t);
        } else {
            existed.setEntityJson(entityJson);
            existed.setSourceAgent(sourceAgent);
            insightTableTemplateService.updateById(existed);
        }
    }

    /** 消费：新骨架实体按 数据源+表 取模板套用人工结论（模板解析失败按无模板处理）；
     *  返回实际套用的表全名清单；skipConfirmed 时跳过已确认实体（显式导入场景：本 Agent 结论不被模板覆盖） */
    public List<String> apply(String dsName, List<EntityDef> rebuilt, boolean skipConfirmed) {
        if (FuncUtil.isEmpty(dsName)) {
            return new ArrayList<>();
        }
        return applyTemplates(rebuilt, table -> {
            InsightTableTemplate t = insightTableTemplateService.selectOne(
                    new QueryWrapper<InsightTableTemplate>()
                            .eq("ds_name", dsName).eq("table_name", table));
            if (t == null || FuncUtil.isEmpty(t.getEntityJson())) {
                return null;
            }
            try {
                return om.readValue(t.getEntityJson(), EntityDef.class);
            } catch (Exception e) {
                log.warn("表模板解析失败，按无模板处理: ds={}, table={}, {}", dsName, table, e.getMessage());
                return null;
            }
        }, skipConfirmed);
    }

    /** 表级匹配合并（静态可测）：每个重建项按表全名取模板，模板名对齐重建项
     *  （模板身份是表，实体名可能因唯一化碰撞不同），再走字段级合并 */
    public static List<String> applyTemplates(List<EntityDef> rebuilt, Function<String, EntityDef> templateOf) {
        return applyTemplates(rebuilt, templateOf, false);
    }

    /** 表级匹配合并（静态可测，带开关）：同两参口径；返回实际套用的表全名清单；
     *  skipConfirmed 时跳过已确认实体（显式导入场景：本 Agent 结论不被模板覆盖） */
    public static List<String> applyTemplates(List<EntityDef> rebuilt, Function<String, EntityDef> templateOf,
                                              boolean skipConfirmed) {
        List<String> applied = new ArrayList<>();
        if (FuncUtil.isEmpty(rebuilt)) {
            return applied;
        }
        List<EntityDef> hits = new ArrayList<>();
        for (EntityDef ne : rebuilt) {
            if (FuncUtil.isEmpty(ne.getTable()) || (skipConfirmed && Boolean.TRUE.equals(ne.getConfirmed()))) {
                continue;
            }
            EntityDef te = templateOf.apply(ne.getTable());
            if (te == null) {
                continue;
            }
            te.setName(ne.getName());
            hits.add(te);
            applied.add(ne.getTable());
        }
        if (!hits.isEmpty()) {
            SkeletonMergeSupport.carryConfirmedFields(rebuilt, hits.toArray(new EntityDef[0]));
        }
        return applied;
    }
}
