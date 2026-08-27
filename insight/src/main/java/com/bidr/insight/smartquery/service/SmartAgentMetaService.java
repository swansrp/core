package com.bidr.insight.smartquery.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.entity.InsightAgentTable;
import com.bidr.insight.smartquery.dao.repository.InsightAgentAssetService;
import com.bidr.insight.smartquery.dao.repository.InsightAgentService;
import com.bidr.insight.smartquery.dao.repository.InsightAgentTableService;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.ColumnConventions;
import com.bidr.insight.smartquery.meta.ConceptsSupport;
import com.bidr.insight.smartquery.meta.DimensionDeriveSupport;
import com.bidr.insight.smartquery.meta.SkeletonBuilder;
import com.bidr.insight.smartquery.meta.SkeletonMergeSupport;
import com.bidr.insight.smartquery.meta.SupportedDimensionSupport;
import com.bidr.insight.smartquery.meta.TableTemplateSupport;
import com.bidr.insight.smartquery.validate.AssetAutoFixer;
import com.bidr.insight.smartquery.validate.AssetConsistencyValidator;
import com.bidr.insight.smartquery.validate.ConfigConflictDetector;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckFinding;
import com.bidr.insight.smartquery.validate.conflict.ConfigCheckResolution;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bidr.forge.datasource.service.DataSourceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: SmartAgentMetaService
 * Description: Agent 元数据服务：按绑定数据源读 INFORMATION_SCHEMA 提供选表能力，
 * 并按选表生成八类语义层资产草稿（entities 骨架 + dimensions 骨架 + value-domains
 * 低基数采样；metrics/relations/concepts/sensitive-fields/row-policies 出空稿待人工/LLM 补充）。
 * 生成结果 upsert
 * 进 insight_agent_asset（status=0 草稿），确认发布后才进运行期。
 * 骨架构建/合并/认证等专项能力拆至 smartquery.meta 包（SkeletonBuilder/SkeletonMergeSupport/
 * CertifiedDraftMerger/ConceptsSupport/ColumnConventions/CommentValueParser），本类留编排
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartAgentMetaService {

    /** 八类资产类型（与资产文件名对应，去掉 .json 后缀）；
     * sensitive-fields 参照 chat bi 敏感列配置（值域不外泄，配对替换列供条件查询）；
     * row-policies 行级权限（渲染期注入 WHERE，登录态模板参数化绑定，fail-closed） */
    public static final List<String> ASSET_TYPES = Arrays.asList(
            "entities", "metrics", "dimensions", "relations", "value-domains", "concepts",
            "sensitive-fields", "row-policies");

    /** 人工补充类资产：生成草稿时若已有非空草稿则保留不覆盖（骨架类 entities/dimensions/value-domains 照常重建） */
    private static final Set<String> MANUAL_TYPES = new HashSet<>(Arrays.asList(
            "metrics", "relations", "concepts", "sensitive-fields", "row-policies"));

    /** AI 评审报告类型（复用资产表但不属于八类资产：发布/一致性校验/自动修复一律跳过，仅评审页读取） */
    public static final String REVIEW_REPORT_TYPE = "review-report";

    /** 选表时排除的系统库 */
    private static final Set<String> SKIP_SCHEMAS = new HashSet<>(Arrays.asList(
            "information_schema", "mysql", "sys", "__internal_schema"));

    private final DataSourceCacheService dataSourceCacheService;
    private final InsightAgentService insightAgentService;
    private final InsightAgentTableService insightAgentTableService;
    private final InsightAgentAssetService insightAgentAssetService;
    private final SkeletonBuilder skeletonBuilder;
    private final SkeletonMergeSupport skeletonMergeSupport;
    private final TableTemplateSupport tableTemplateSupport;

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 列出数据源下可选的物理表（排除系统库），返回 db.tbl 全名 + 表注释 */
    public List<Map<String, Object>> listTables(String dsName) {
        if (FuncUtil.isEmpty(dsName)) {
            throw new NoticeException("请先为 Agent 绑定数据源");
        }
        DataSource pool = dataSourceCacheService.getDataSource(dsName);
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT TABLE_SCHEMA, TABLE_NAME, TABLE_COMMENT FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_SCHEMA, TABLE_NAME";
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEMA");
                if (SKIP_SCHEMAS.contains(schema.toLowerCase())) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tableName", schema + "." + rs.getString("TABLE_NAME"));
                row.put("tableComment", rs.getString("TABLE_COMMENT"));
                out.add(row);
            }
        } catch (Exception e) {
            log.error("读取表清单失败: dsName={}, {}", dsName, e.getMessage());
            throw new NoticeException("读取表清单失败: " + e.getMessage());
        }
        return out;
    }

    /** 已选表清单 */
    public List<InsightAgentTable> selectedTables(String agentCode) {
        return insightAgentTableService.select(
                new QueryWrapper<InsightAgentTable>().eq("agent_code", agentCode));
    }

    /** 保存选表：整体替换（前端传全量勾选结果），表注释随行快照 */
    public void saveTables(String agentCode, List<InsightAgentTable> tables) {
        insightAgentTableService.delete(
                new QueryWrapper<InsightAgentTable>().eq("agent_code", agentCode));
        if (FuncUtil.isNotEmpty(tables)) {
            for (InsightAgentTable t : tables) {
                if (FuncUtil.isEmpty(t.getTableName())) {
                    continue;
                }
                t.setId(null);
                t.setAgentCode(agentCode);
                insightAgentTableService.insert(t);
            }
        }
        // 骨架前置：选表保存即确定，构建实体草稿（实体确认页秒级可见，不等生成任务）；
        // 失败不阻断选表保存（骨架构建可回落生成流程）
        if (FuncUtil.isNotEmpty(tables)) {
            try {
                buildSkeletonDraft(agentCode);
            } catch (Exception e) {
                log.warn("Agent '{}' 选表后骨架构建失败（回落生成流程）: {}", agentCode, e.getMessage());
            }
        }
    }

    /** 骨架前置：按选表确定性构建 entities/dimensions/value-domains 三类草稿（无 LLM），
     *  供实体确认页即时编辑；合并语义同生成落盘（已认证与已人工确认实体保留），
     *  不触碰人工五类草稿（如 concepts，待生成流程产） */
    public void buildSkeletonDraft(String agentCode) {
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null) {
            throw new NoticeException("Agent [" + agentCode + "] 不存在");
        }
        List<InsightAgentTable> tables = selectedTables(agentCode);
        if (tables.isEmpty()) {
            return;
        }
        List<EntityDef> entities = new ArrayList<>();
        List<DimensionDef> dimensions = new ArrayList<>();
        Map<String, ValueDomainDef> domains = new LinkedHashMap<>();
        Set<String> entityNames = new HashSet<>();
        Set<String> dimensionNames = new HashSet<>();
        DataSource pool = dataSourceCacheService.getDataSource(agent.getDsName());
        try (Connection conn = pool.getConnection()) {
            for (InsightAgentTable t : tables) {
                String[] split = ColumnConventions.splitTableName(t.getTableName());
                if (split == null) {
                    continue;
                }
                skeletonBuilder.buildTableAssets(conn, split[0], split[1], t.getTableComment(),
                        entities, dimensions, domains, entityNames, dimensionNames);
            }
        } catch (Exception e) {
            throw new NoticeException("骨架构建失败: " + e.getMessage());
        }
        // 跨 Agent 表级复用：同表其他 Agent 已确认的人工结论模板先套用，
        // 本 Agent 自己草稿的确认结论优先（后置的 carryConfirmedFromOld 同名列覆盖模板）
        List<String> templateApplied = tableTemplateSupport.apply(agent.getDsName(), entities, false);
        // 只落骨架三类：已确认实体走字段级合并（键/分区/人工编辑列携入，未编辑列随物理表刷新，
        // 新增列入、删列不残留）；已认证实体整体保留不重建；敏感清理口径同生成落盘（安全优先）
        skeletonMergeSupport.carryConfirmedFromOld(agentCode, entities);
        List<EntityDef> mergedEntities = skeletonMergeSupport.mergeByName(agentCode, "entities", entities,
                EntityDef[].class, EntityDef::getName, EntityDef::getCertified);
        List<DimensionDef> mergedDimensions = skeletonMergeSupport.mergeByName(agentCode, "dimensions", dimensions,
                DimensionDef[].class, DimensionDef::getName, DimensionDef::getCertified);
        Map<String, ValueDomainDef> mergedDomains = skeletonMergeSupport.mergeDomains(agentCode, domains);
        // 模板套用表的维度按实体结论重派生（模板只存实体；认证维度保留），保证列结论与维度一致
        DimensionDeriveSupport.rederiveForTables(mergedDimensions, mergedEntities, templateApplied);
        // 禁用列清理：carry 携入 disabled 后剔除其派生的未认证维度（禁用列问数侧不可见）
        DimensionDeriveSupport.dropDisabledColumnDims(mergedDimensions, mergedEntities);
        skeletonMergeSupport.purgeSensitive(mergedEntities, mergedDomains, loadSensitiveKeys(agentCode));
        // 归类单源：存量目录先回填进实体列级 dim_group（老数据迁移），再落实体并从实体派生 hierarchy
        backfillDimGroupsFromHierarchy(agentCode, mergedEntities, mergedDimensions);
        upsertDraft(agentCode, "entities", writeJson(mergedEntities));
        upsertDraft(agentCode, "dimensions", writeJson(mergedDimensions));
        upsertDraft(agentCode, "value-domains", writeDomains(mergedDomains));
        syncHierarchyFromEntities(agentCode, mergedEntities, mergedDimensions);
        log.info("Agent '{}' 选表骨架前置完成：entities={}, dimensions={}, domains={}",
                agentCode, mergedEntities.size(), mergedDimensions.size(), mergedDomains.size());
    }


    /** 存量目录回填列级归类（老数据一次性迁移）：concepts 草稿 hierarchy 已有分组但实体 dim_group 为空的，
     *  组名写回实体字段（人工编辑过的列不动）；无现存目录则不动实体 */
    private void backfillDimGroupsFromHierarchy(String agentCode, List<EntityDef> entities, List<DimensionDef> dims) {
        InsightAgentAsset conceptsAsset = getAsset(agentCode, "concepts");
        if (conceptsAsset == null || FuncUtil.isEmpty(conceptsAsset.getContent())) {
            return;
        }
        try {
            Map<String, String> dimToGroup = ConceptsSupport.dimGroupOfHierarchy(
                    om.readTree(conceptsAsset.getContent()).path("hierarchy"));
            ConceptsSupport.backfillDimGroupsFromHierarchy(entities, dims, dimToGroup);
        } catch (Exception e) {
            log.warn("Agent '{}' 存量目录回填跳过: {}", agentCode, e.getMessage());
        }
    }

    /** hierarchy 单源派生落盘：分级目录完全由实体列级归类（dim_group）抽出，保留 concepts 条目不动；
     *  实体保存/骨架重建/生成落盘后调用，业务概念页目录面板只读展示（编辑入口在实体确认页归类列） */
    public void syncHierarchyFromEntities(String agentCode, List<EntityDef> entities, List<DimensionDef> dims) {
        List<Map<String, Object>> hierarchy = ConceptsSupport.deriveHierarchy(
                dims, ConceptsSupport.exprGroupMap(entities));
        com.fasterxml.jackson.databind.node.ObjectNode root;
        try {
            InsightAgentAsset conceptsAsset = getAsset(agentCode, "concepts");
            if (conceptsAsset == null || FuncUtil.isEmpty(conceptsAsset.getContent())) {
                root = om.createObjectNode();
                root.put("schema_version", "1.0");
                root.set("concepts", om.createArrayNode());
            } else {
                root = (com.fasterxml.jackson.databind.node.ObjectNode) om.readTree(conceptsAsset.getContent());
                if (!root.has("concepts") || !root.get("concepts").isArray()) {
                    root.set("concepts", om.createArrayNode());
                }
            }
        } catch (Exception e) {
            log.warn("Agent '{}' concepts 草稿解析失败，分级目录派生跳过: {}", agentCode, e.getMessage());
            return;
        }
        root.set("hierarchy", om.valueToTree(hierarchy));
        upsertDraft(agentCode, "concepts", root.toString());
        log.info("Agent '{}' 分级目录已从实体归类派生：{} 个分组", agentCode, hierarchy.size());
    }

    /** 敏感列键提取（实体.字段 小写）：优先新形态 tables[]（按表声明），兼容旧形态顶层 fields[]；
     *  口径同生成服务 loadSensitiveMarks，供骨架前置的敏感清理 */
    private Set<String> loadSensitiveKeys(String agentCode) {
        Set<String> keys = new HashSet<>();
        InsightAgentAsset sf = getAsset(agentCode, "sensitive-fields");
        if (sf == null || FuncUtil.isEmpty(sf.getContent())) {
            return keys;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(sf.getContent());
            com.fasterxml.jackson.databind.JsonNode tables = root.path("tables");
            if (tables.isArray() && tables.size() > 0) {
                for (com.fasterxml.jackson.databind.JsonNode t : tables) {
                    String entity = t.path("entity").asText("").trim();
                    for (com.fasterxml.jackson.databind.JsonNode f : t.path("fields")) {
                        addSensitiveKey(keys, entity, f.path("field").asText(""));
                    }
                }
            } else {
                for (com.fasterxml.jackson.databind.JsonNode f : root.path("fields")) {
                    addSensitiveKey(keys, f.path("entity").asText(""), f.path("field").asText(""));
                }
            }
        } catch (Exception e) {
            log.warn("Agent '{}' 敏感字段草稿解析失败，按未标记处理: {}", agentCode, e.getMessage());
        }
        return keys;
    }

    /** entity.field 键归一入集（空值防护：点两侧都非空才有效） */
    private static void addSensitiveKey(Set<String> keys, String entity, String field) {
        if (FuncUtil.isEmpty(entity) || FuncUtil.isEmpty(field)) {
            return;
        }
        keys.add((entity + "." + field).toLowerCase());
    }


    /** 生成结果落草稿：骨架三类按认证语义合并（现存已认证项与本次重建未涉及项原样保留，
     * 其余由新骨架替换；合并后统一清理覆盖敏感列的域与字段引用），
     * 人工补充四类（metrics/relations/concepts/sensitive-fields）已有非空草稿时保留 */
    public void saveGeneratedDrafts(String agentCode, List<EntityDef> entities,
                                    List<DimensionDef> dimensions, Map<String, ValueDomainDef> domains,
                                    Set<String> sensitiveKeys) {
        // 跨 Agent 表级复用：口径同骨架前置（模板先套用，本 Agent 草稿确认结论优先）
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        List<String> templateApplied = agent != null
                ? tableTemplateSupport.apply(agent.getDsName(), entities, false)
                : Collections.emptyList();
        // 已确认实体字段级合并（人工结论不丢），已认证实体整体保留；口径同骨架前置
        skeletonMergeSupport.carryConfirmedFromOld(agentCode, entities);
        List<EntityDef> mergedEntities = skeletonMergeSupport.mergeByName(agentCode, "entities", entities,
                EntityDef[].class, EntityDef::getName, EntityDef::getCertified);
        List<DimensionDef> mergedDimensions = skeletonMergeSupport.mergeByName(agentCode, "dimensions", dimensions,
                DimensionDef[].class, DimensionDef::getName, DimensionDef::getCertified);
        Map<String, ValueDomainDef> mergedDomains = skeletonMergeSupport.mergeDomains(agentCode, domains);
        // 模板套用表的维度按实体结论重派生（口径同骨架前置：模板只存实体，认证维度保留）
        DimensionDeriveSupport.rederiveForTables(mergedDimensions, mergedEntities, templateApplied);
        // 禁用列清理：口径同骨架前置（禁用列问数侧不可见）
        DimensionDeriveSupport.dropDisabledColumnDims(mergedDimensions, mergedEntities);
        skeletonMergeSupport.purgeSensitive(mergedEntities, mergedDomains, sensitiveKeys);
        // 归类单源：落库前存量目录回填列级归类（老数据迁移），concepts 初建的目录由实体派生
        backfillDimGroupsFromHierarchy(agentCode, mergedEntities, mergedDimensions);
        Map<String, String> drafts = new LinkedHashMap<>();
        drafts.put("entities", writeJson(mergedEntities));
        drafts.put("metrics", "[]");
        drafts.put("dimensions", writeJson(mergedDimensions));
        drafts.put("relations", "[]");
        drafts.put("value-domains", writeDomains(mergedDomains));
        drafts.put("concepts", ConceptsSupport.draftJson(ConceptsSupport.deriveHierarchy(
                mergedDimensions, ConceptsSupport.exprGroupMap(mergedEntities))));
        drafts.put("sensitive-fields", "{\"schema_version\":\"1.0\",\"tables\":[]}");
        drafts.put("row-policies", "{\"schema_version\":\"1.0\",\"tables\":[]}");
        for (Map.Entry<String, String> entry : drafts.entrySet()) {
            // 人工补充类资产已有非空草稿时保留，避免重新生成覆盖人工成果
            if (MANUAL_TYPES.contains(entry.getKey()) && !isManualDraftEmpty(entry.getKey(), getAsset(agentCode, entry.getKey()))) {
                log.info("Agent '{}' 资产 '{}' 已有非空草稿，生成时保留", agentCode, entry.getKey());
                continue;
            }
            upsertDraft(agentCode, entry.getKey(), entry.getValue());
        }
        log.info("Agent '{}' 资产草稿生成完成：entities={}, dimensions={}, domains={}",
                agentCode, mergedEntities.size(), mergedDimensions.size(), mergedDomains.size());
    }


    /** 敏感字段治理是否就绪：新口径按表逐一声明（tables[] 覆盖 entities 草稿全部实体，
     *  每表或标记敏感列 fields[] 或显式声明 no_sensitive）；兼容旧形态（顶层 fields 非空或全局 no_sensitive）；
     *  LLM 生成闸与 LLM 对话闸共用此口径，防项目编码/名称等被采样外泄 */
    public boolean sensitiveGoverned(String agentCode) {
        InsightAgentAsset sf = getAsset(agentCode, "sensitive-fields");
        if (sf == null || FuncUtil.isEmpty(sf.getContent())) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(sf.getContent());
            com.fasterxml.jackson.databind.JsonNode tables = root.path("tables");
            if (tables.isArray() && tables.size() > 0) {
                // 已处理表集合：声明无敏感 或 标记了敏感列均算处理
                Set<String> covered = new HashSet<>();
                for (com.fasterxml.jackson.databind.JsonNode t : tables) {
                    String entity = t.path("entity").asText("").trim().toLowerCase();
                    if (entity.isEmpty()) {
                        continue;
                    }
                    if (t.path("no_sensitive").asBoolean(false) || t.path("fields").size() > 0) {
                        covered.add(entity);
                    }
                }
                // 表清单基线：entities 草稿优先；无骨架时按选表派生（与 governEntities 同命名口径，声明键不漂移）
                Set<String> baseline = governEntityNames(agentCode);
                if (baseline.isEmpty()) {
                    return false;
                }
                for (String name : baseline) {
                    if (!covered.contains(name)) {
                        return false;
                    }
                }
                return true;
            }
            // 旧形态兼容：fields 非空 或 全局确认无敏感列
            return root.path("fields").size() > 0 || root.path("no_sensitive").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /** 敏感治理实体清单（「敏感字段」tab 行源）：entities 草稿优先（骨架产出，含字段清单）；
     *  无骨架时直接按选表派生——实体命名与骨架构建同口径（选表顺序 + 裸表名去重），
     *  先生成骨架后声明与先声明后生成骨架两种顺序下声明键一致；字段清单实时读列元数据
     *  （仅读结构不采样、不调 LLM），敏感声明不再以骨架为前置 */
    public List<Map<String, Object>> governEntities(String agentCode) {
        InsightAgentAsset ea = getAsset(agentCode, "entities");
        if (ea != null && FuncUtil.isNotEmpty(ea.getContent())) {
            try {
                com.fasterxml.jackson.databind.JsonNode entities = om.readTree(ea.getContent());
                if (entities.isArray() && entities.size() > 0) {
                    List<Map<String, Object>> parsed = om.convertValue(entities,
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                    List<Map<String, Object>> out = new ArrayList<>();
                    for (Map<String, Object> e : parsed) {
                        if (e != null && e.get("name") != null && FuncUtil.isNotEmpty(String.valueOf(e.get("name")))) {
                            out.add(e);
                        }
                    }
                    if (!out.isEmpty()) {
                        return out;
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' entities 草稿解析失败，回落选表派生: {}", agentCode, e.getMessage());
            }
        }
        // 无骨架回落：按选表派生（表注释取选表快照，字段实时读列元数据）
        List<Map<String, Object>> out = new ArrayList<>();
        List<InsightAgentTable> tables = selectedTables(agentCode);
        if (tables.isEmpty()) {
            return out;
        }
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null) {
            throw new NoticeException("Agent [" + agentCode + "] 不存在");
        }
        DataSource pool = dataSourceCacheService.getDataSource(agent.getDsName());
        Set<String> names = new HashSet<>();
        try (Connection conn = pool.getConnection()) {
            for (InsightAgentTable t : tables) {
                String[] split = ColumnConventions.splitTableName(t.getTableName());
                if (split == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", ColumnConventions.uniqueName(split[1], names));
                row.put("display_name", FuncUtil.isNotEmpty(t.getTableComment()) ? t.getTableComment() : t.getTableName());
                row.put("table", t.getTableName());
                row.put("fields", readColumnFields(conn, split[0], split[1]));
                out.add(row);
            }
        } catch (Exception e) {
            log.error("读取选表结构失败: agentCode={}, {}", agentCode, e.getMessage());
            throw new NoticeException("读取选表结构失败: " + e.getMessage());
        }
        return out;
    }

    /** 实体列配置是否全部确认（LLM 生成闸依据）：entities 草稿非空、逐实体 confirmed，
     *  且实体数覆盖选表数（新增表未落骨架/未确认时不放行）；旧数据无 confirmed 字段视为未确认 */
    public boolean entitiesConfirmed(String agentCode) {
        InsightAgentAsset ea = getAsset(agentCode, "entities");
        if (ea == null || FuncUtil.isEmpty(ea.getContent())) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode entities = om.readTree(ea.getContent());
            if (!entities.isArray() || entities.size() == 0) {
                return false;
            }
            if (entities.size() < selectedTables(agentCode).size()) {
                return false;
            }
            for (com.fasterxml.jackson.databind.JsonNode e : entities) {
                if (!e.path("confirmed").asBoolean(false)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("Agent '{}' entities 草稿解析失败，按未确认处理: {}", agentCode, e.getMessage());
            return false;
        }
    }

    /** 敏感治理基线实体名（sensitiveGoverned 口径）：entities 草稿优先；无骨架按选表派生
     *  （命名与 governEntities/骨架构建一致：选表顺序 + 裸表名去重）。仅用选表元数据，不读数据源 */
    private Set<String> governEntityNames(String agentCode) {
        Set<String> names = new HashSet<>();
        InsightAgentAsset ea = getAsset(agentCode, "entities");
        if (ea != null && FuncUtil.isNotEmpty(ea.getContent())) {
            try {
                com.fasterxml.jackson.databind.JsonNode entities = om.readTree(ea.getContent());
                if (entities.isArray() && entities.size() > 0) {
                    for (com.fasterxml.jackson.databind.JsonNode e : entities) {
                        String n = e.path("name").asText("").trim().toLowerCase();
                        if (!n.isEmpty()) {
                            names.add(n);
                        }
                    }
                    if (!names.isEmpty()) {
                        return names;
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' entities 草稿解析失败，回落选表派生: {}", agentCode, e.getMessage());
            }
        }
        Set<String> used = new HashSet<>();
        for (InsightAgentTable t : selectedTables(agentCode)) {
            String[] split = ColumnConventions.splitTableName(t.getTableName());
            if (split != null) {
                names.add(ColumnConventions.uniqueName(split[1], used).toLowerCase());
            }
        }
        return names;
    }

    /** 单表列元数据（敏感治理无骨架回落的字段清单）：仅读结构不采样，读失败该表出空清单不阻断 */
    private List<Map<String, Object>> readColumnFields(Connection conn, String schema, String tbl) {
        List<Map<String, Object>> fields = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tbl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    String comment = rs.getString("COLUMN_COMMENT");
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("name", col);
                    f.put("display_name", FuncUtil.isNotEmpty(comment) ? comment : col);
                    fields.add(f);
                }
            }
        } catch (Exception e) {
            log.warn("表 {}.{} 列元数据读取失败: {}", schema, tbl, e.getMessage());
        }
        return fields;
    }

    /** 键/分区预选（实体确认页行源）：逐选表出预选业务键+依据、候选列、分区识别+依据、
     *  索引全清单（实时 STATISTICS 读，不落库）；仅读结构不采样，读失败单表回落空不阻断 */
    public List<Map<String, Object>> governKeys(String agentCode) {
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null) {
            throw new NoticeException("Agent [" + agentCode + "] 不存在");
        }
        List<InsightAgentTable> tables = selectedTables(agentCode);
        List<Map<String, Object>> out = new ArrayList<>();
        if (tables.isEmpty()) {
            return out;
        }
        // entities 草稿的确认态（表级 confirmed，随骨架落库；无骨架视为未确认）
        Map<String, Boolean> confirmedByTable = new HashMap<>();
        InsightAgentAsset ea = getAsset(agentCode, "entities");
        if (ea != null && FuncUtil.isNotEmpty(ea.getContent())) {
            try {
                for (com.fasterxml.jackson.databind.JsonNode e : om.readTree(ea.getContent())) {
                    String table = e.path("table").asText("");
                    if (!table.isEmpty()) {
                        confirmedByTable.put(table, e.path("confirmed").asBoolean(false));
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' entities 草稿解析失败: {}", agentCode, e.getMessage());
            }
        }
        DataSource pool = dataSourceCacheService.getDataSource(agent.getDsName());
        try (Connection conn = pool.getConnection()) {
            for (InsightAgentTable t : tables) {
                String[] split = ColumnConventions.splitTableName(t.getTableName());
                if (split == null) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table", t.getTableName());
                row.put("display_name", FuncUtil.isNotEmpty(t.getTableComment()) ? t.getTableComment() : t.getTableName());
                row.put("confirmed", confirmedByTable.getOrDefault(t.getTableName(), false));
                fillKeyChoices(conn, split[0], split[1], row);
                out.add(row);
            }
        } catch (Exception e) {
            log.error("读取键预选失败: agentCode={}, {}", agentCode, e.getMessage());
            throw new NoticeException("读取键预选失败: " + e.getMessage());
        }
        return out;
    }

    /** 单表键/分区预选填充：列元数据（COLUMN_KEY）+ STATISTICS 索引全貌，
     *  预选序：主键 PRI → 唯一索引 UNI 全列 → 启发式首个 *id 列；分区 dy/dm/dd 粗到细 */
    private void fillKeyChoices(Connection conn, String schema, String tbl, Map<String, Object> row) {
        List<String> cols = new ArrayList<>();
        List<String> priCols = new ArrayList<>();
        String colSql = "SELECT COLUMN_NAME, COLUMN_KEY FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(colSql)) {
            ps.setString(1, schema);
            ps.setString(2, tbl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    cols.add(col);
                    if ("PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY"))) {
                        priCols.add(col);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("表 {}.{} 列元数据读取失败: {}", schema, tbl, e.getMessage());
        }
        // 索引全貌（实时读不存）：索引名按首见序，列按 SEQ_IN_INDEX 展开，含唯一性标记供前端展示依据
        Map<String, List<String>> idxCols = new LinkedHashMap<>();
        Map<String, Boolean> idxUnique = new LinkedHashMap<>();
        String idxSql = "SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY INDEX_NAME, SEQ_IN_INDEX";
        try (PreparedStatement ps = conn.prepareStatement(idxSql)) {
            ps.setString(1, schema);
            ps.setString(2, tbl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("INDEX_NAME");
                    idxCols.computeIfAbsent(name, k -> new ArrayList<>()).add(rs.getString("COLUMN_NAME"));
                    idxUnique.putIfAbsent(name, rs.getInt("NON_UNIQUE") == 0);
                }
            }
        } catch (Exception e) {
            log.debug("表 {}.{} 索引读取失败（无索引库回落启发式）: {}", schema, tbl, e.getMessage());
        }
        List<Map<String, Object>> indexes = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : idxCols.entrySet()) {
            Map<String, Object> idx = new LinkedHashMap<>();
            idx.put("name", e.getKey());
            idx.put("unique", Boolean.TRUE.equals(idxUnique.get(e.getKey())));
            idx.put("columns", e.getValue());
            indexes.add(idx);
        }
        row.put("indexes", indexes);

        // 业务键预选：PRI → 首个唯一索引（复合全列）→ 启发式首个 *id；依据随预选同出，用户信不信一目了然
        List<String> preKey = null;
        String basis = null;
        if (!priCols.isEmpty()) {
            preKey = priCols;
            basis = "主键 PRI";
        } else {
            for (Map.Entry<String, List<String>> e : idxCols.entrySet()) {
                if (Boolean.TRUE.equals(idxUnique.get(e.getKey())) && !"PRIMARY".equals(e.getKey())) {
                    preKey = e.getValue();
                    basis = "唯一索引 UNI（" + e.getKey() + "）";
                    break;
                }
            }
        }
        if (preKey == null) {
            for (String col : cols) {
                if (col != null && col.toLowerCase().endsWith("id")) {
                    preKey = Collections.singletonList(col);
                    basis = "启发式（首个 *id 列，未确认前画像标注启发式）";
                    break;
                }
            }
        }
        row.put("pre_key", preKey);
        row.put("key_basis", preKey == null ? null : basis);
        // 候选列：*id 尾缀列中未被预选的（复合唯一第二列起等备选在索引清单里自见）
        List<String> candidates = new ArrayList<>();
        Set<String> preSet = preKey == null ? Collections.emptySet() : new HashSet<>(preKey);
        for (String col : cols) {
            if (col != null && col.toLowerCase().endsWith("id") && !preSet.contains(col)) {
                candidates.add(col);
            }
        }
        row.put("candidates", candidates);

        // 分区列预选（与骨架/画像同口径 dy/dm/dd 粗到细），依据标注快照粒度
        String partition = null;
        String partitionBasis = null;
        for (String[] c : new String[][]{{"dy", "年快照"}, {"dm", "月快照"}, {"dd", "日快照"}}) {
            for (String col : cols) {
                if (c[0].equalsIgnoreCase(col)) {
                    partition = col;
                    partitionBasis = "启发式（" + c[1] + "）";
                    break;
                }
            }
            if (partition != null) {
                break;
            }
        }
        row.put("partition", partition);
        row.put("partition_basis", partitionBasis);
    }

    /** 人工补充类草稿是否为空：metrics/relations 看顶层数组；concepts 看 concepts 数组；
     *  sensitive-fields 看 tables 声明（新）或 fields 数组（旧）；row-policies 看 tables 数组 */
    private boolean isManualDraftEmpty(String assetType, InsightAgentAsset asset) {
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return true;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = om.readTree(asset.getContent());
            if ("concepts".equals(assetType)) {
                // hierarchy 含骨架启发式分级目录草稿，与 concepts 皆有内容才视为非空（人工成果保留）
                return root.path("concepts").size() == 0 && root.path("hierarchy").size() == 0;
            }
            if ("sensitive-fields".equals(assetType)) {
                return root.path("tables").size() == 0 && root.path("fields").size() == 0;
            }
            if ("row-policies".equals(assetType)) {
                return root.path("tables").size() == 0;
            }
            return root.size() == 0;
        } catch (Exception e) {
            // 解析失败视为非空（宁可保留不覆盖）
            return false;
        }
    }

    /** 取单类资产（管理页编辑用）；不存在返回 null */
    public InsightAgentAsset getAsset(String agentCode, String assetType) {
        return insightAgentAssetService.selectOne(new QueryWrapper<InsightAgentAsset>()
                .eq("agent_code", agentCode).eq("asset_type", assetType));
    }

    /** 保存资产草稿（JSON 合法性由 Controller 校验），存在则覆盖并回到草稿态 */
    public void saveAssetDraft(String agentCode, String assetType, String content) {
        // 实体归类单源：保存前存量目录回填列级归类（老数据迁移），保存后从实体派生分级目录；
        // content 替换为回填后结果（落库即单源一致，页面下次打开归类列与目录同口径）
        if ("entities".equals(assetType)) {
            try {
                List<EntityDef> ents = Arrays.asList(om.readValue(content, EntityDef[].class));
                List<DimensionDef> dims = new ArrayList<>(loadDimensions(agentCode));
                backfillDimGroupsFromHierarchy(agentCode, ents, dims);
                // 维度单源：角色修正同步重派生维度（认证维度保留）并重展开指标 supported_dimensions——
                // 派生件即时跟随实体结论，消除「维度已改、指标清单漂移」的批量悬空
                if (getAsset(agentCode, "dimensions") != null) {
                    List<String> tables = new ArrayList<>();
                    for (EntityDef e : ents) {
                        if (FuncUtil.isNotEmpty(e.getTable())) {
                            tables.add(e.getTable());
                        }
                    }
                    DimensionDeriveSupport.rederiveForTables(dims, ents, tables);
                    upsertDraft(agentCode, "dimensions", writeJson(dims));
                    reexpandMetricsSupportedDimensions(agentCode, ents, dims);
                    resyncConceptsDangling(agentCode, dims);
                }
                syncHierarchyFromEntities(agentCode, ents, dims);
                content = writeJson(ents);
            } catch (Exception e) {
                log.warn("Agent '{}' 实体保存后归类目录派生失败（不影响实体本身）: {}", agentCode, e.getMessage());
            }
        }
        InsightAgentAsset existed = getAsset(agentCode, assetType);
        if (existed == null) {
            InsightAgentAsset asset = new InsightAgentAsset();
            asset.setAgentCode(agentCode);
            asset.setAssetType(assetType);
            asset.setContent(content);
            asset.setStatus("0");
            insightAgentAssetService.insert(asset);
        } else {
            existed.setContent(content);
            existed.setStatus("0");
            insightAgentAssetService.updateById(existed);
        }
        // dimensions/relations 是 metrics supported_dimensions 的展开上下文：草稿变更后同步重展开存量指标（派生件跟随单源）——
        // 补齐「新维度提案合并/手工改维度后存量指标清单不含它」的 §6.2.2 悬空缺口；失败仅告警不阻断本次保存
        if ("dimensions".equals(assetType) || "relations".equals(assetType)) {
            try {
                reexpandMetricsSupportedDimensions(agentCode, loadEntitiesDraft(agentCode), loadDimensions(agentCode));
            } catch (Exception e) {
                log.warn("Agent '{}' {} 草稿保存后同步重展开 metrics supported_dimensions 失败（不影响本次保存）: {}",
                        agentCode, assetType, e.getMessage());
            }
        }
    }

    /** 实体角色修正同步重展开指标 supported_dimensions（派生件跟随维度单源）：
     *  上下文=新实体+重派生维度+现存关系/敏感声明；无 metrics 草稿/解析失败仅告警不阻断实体保存 */
    private void reexpandMetricsSupportedDimensions(String agentCode, List<EntityDef> ents, List<DimensionDef> dims) {
        InsightAgentAsset m = getAsset(agentCode, "metrics");
        if (m == null || FuncUtil.isEmpty(m.getContent())) {
            return;
        }
        try {
            JsonNode root = om.readTree(m.getContent());
            if (!(root instanceof ArrayNode)) {
                return;
            }
            InsightAgentAsset rel = getAsset(agentCode, "relations");
            JsonNode relations = rel == null || FuncUtil.isEmpty(rel.getContent())
                    ? null : om.readTree(rel.getContent());
            SupportedDimensionSupport.expand((ArrayNode) root, ents, dims,
                    loadSensitiveKeys(agentCode), relations);
            upsertDraft(agentCode, "metrics", om.writeValueAsString(root));
        } catch (Exception e) {
            log.warn("Agent '{}' 实体保存同步重展开 metrics supported_dimensions 失败（不影响实体保存）: {}",
                    agentCode, e.getMessage());
        }
    }

    /** 实体保存同步清理 expands_to 悬空的概念（派生件跟随维度单源）：维度重派生后
     *  展开到已移除维度（如禁用/改角色列）的概念失去展开语义，同步删除（口径同一键修复 AssetAutoFixer.fixConcepts）；
     *  无 concepts 草稿/解析失败仅告警不阻断实体保存 */
    private void resyncConceptsDangling(String agentCode, List<DimensionDef> dims) {
        InsightAgentAsset c = getAsset(agentCode, "concepts");
        if (c == null || FuncUtil.isEmpty(c.getContent())) {
            return;
        }
        try {
            JsonNode root = om.readTree(c.getContent());
            Set<String> valid = new HashSet<>();
            for (DimensionDef d : dims) {
                if (FuncUtil.isNotEmpty(d.getName())) {
                    valid.add(d.getName());
                }
            }
            List<String> dropped = ConceptsSupport.dropDanglingConcepts(root, valid);
            if (!dropped.isEmpty()) {
                upsertDraft(agentCode, "concepts", om.writeValueAsString(root));
                log.warn("Agent '{}' 实体保存同步删除 {} 个悬空概念（expands_to 维度已不存在）: {}",
                        agentCode, dropped.size(), dropped);
            }
        } catch (Exception e) {
            log.warn("Agent '{}' 实体保存同步清理悬空概念失败（不影响实体保存）: {}",
                    agentCode, e.getMessage());
        }
    }

    /** dimensions 草稿列表（解析失败/无草稿返回空）：归类派生与存量回填共用 */
    private List<DimensionDef> loadDimensions(String agentCode) {
        InsightAgentAsset dimsAsset = getAsset(agentCode, "dimensions");
        if (dimsAsset == null || FuncUtil.isEmpty(dimsAsset.getContent())) {
            return Collections.emptyList();
        }
        try {
            return Arrays.asList(om.readValue(dimsAsset.getContent(), DimensionDef[].class));
        } catch (Exception e) {
            log.warn("Agent '{}' dimensions 草稿解析失败: {}", agentCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** entities 草稿列表（解析失败/无草稿返回空）：模板显式保存/导入/AI 评审共用 */
    public List<EntityDef> loadEntitiesDraft(String agentCode) {
        InsightAgentAsset entAsset = getAsset(agentCode, "entities");
        if (entAsset == null || FuncUtil.isEmpty(entAsset.getContent())) {
            return Collections.emptyList();
        }
        try {
            return Arrays.asList(om.readValue(entAsset.getContent(), EntityDef[].class));
        } catch (Exception e) {
            log.warn("Agent '{}' entities 草稿解析失败: {}", agentCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Agent 数据源（模板三个接口共用；不存在报错） */
    private InsightAgent requireAgentOf(String agentCode) {
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null) {
            throw new NoticeException("Agent [" + agentCode + "] 不存在");
        }
        return agent;
    }

    /** 表模板清单（页面「从模板导入」预览：表全名/来源 Agent/更新时间） */
    public List<Map<String, Object>> tableTemplates(String agentCode) {
        InsightAgent agent = requireAgentOf(agentCode);
        List<Map<String, Object>> out = new ArrayList<>();
        for (com.bidr.insight.smartquery.dao.entity.InsightTableTemplate t
                : tableTemplateSupport.listByDs(agent.getDsName())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tableName", t.getTableName());
            row.put("sourceAgent", t.getSourceAgent());
            row.put("updateAt", t.getUpdateAt());
            out.add(row);
        }
        return out;
    }

    /** 保存到模板（页面显式动作）：当前 entities 草稿已认证实体沉淀模板库；
     *  onlyTables 非空时仅沉淀清单内表（认证本表按表同步）；不随实体保存自动沉淀，防个别 Agent 特化配置污染共享模板 */
    public Map<String, Object> saveTemplates(String agentCode, List<String> onlyTables) {
        InsightAgent agent = requireAgentOf(agentCode);
        List<String> tables = tableTemplateSupport.sediment(
                agent.getDsName(), agentCode, loadEntitiesDraft(agentCode), onlyTables);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("tables", tables);
        log.info("Agent '{}' 显式沉淀表模板 {} 张: {}", agentCode, tables.size(), tables);
        return res;
    }

    /** 从模板导入（页面显式动作）：模板人工结论套用到当前 entities 草稿的未确认实体（已确认的不覆盖）；
     *  模板只存实体，套用后维度按实体结论重派生并落 dimensions 草稿（认证维度保留），
     *  再派生分级目录；骨架链路选表时的自动套用不受影响 */
    public Map<String, Object> importTemplates(String agentCode) {
        InsightAgent agent = requireAgentOf(agentCode);
        List<EntityDef> ents = loadEntitiesDraft(agentCode);
        List<String> tables = ents.isEmpty() ? Collections.emptyList()
                : tableTemplateSupport.apply(agent.getDsName(), ents, true);
        if (!tables.isEmpty()) {
            upsertDraft(agentCode, "entities", writeJson(ents));
            List<DimensionDef> dims = new ArrayList<>(loadDimensions(agentCode));
            DimensionDeriveSupport.rederiveForTables(dims, ents, tables);
            upsertDraft(agentCode, "dimensions", writeJson(dims));
            syncHierarchyFromEntities(agentCode, ents, dims);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("tables", tables);
        log.info("Agent '{}' 显式导入表模板 {} 张: {}", agentCode, tables.size(), tables);
        return res;
    }

    /** AI 评审报告落盘（upsert 覆盖，只留最新一份；评审链 submit_review 工具调用） */
    public void saveReviewReport(String agentCode, String reportJson) {
        upsertDraft(agentCode, REVIEW_REPORT_TYPE, reportJson);
    }

    /** AI 评审报告读取（无报告返回 null）：评审面板/生成入口弱提醒共用 */
    public String loadReviewReport(String agentCode) {
        InsightAgentAsset asset = getAsset(agentCode, REVIEW_REPORT_TYPE);
        return asset == null || FuncUtil.isEmpty(asset.getContent()) ? null : asset.getContent();
    }

    /** 评审条目处理标记（人工消化闭环）：resolved 写回报告 JSON 落盘；
     *  index 为 items 原始下标（前端筛选视图传原始下标）；返回更新后报告 JSON */
    public String resolveReviewItem(String agentCode, int index, boolean resolved) {
        String report = loadReviewReport(agentCode);
        if (FuncUtil.isEmpty(report)) {
            throw new NoticeException("评审报告不存在");
        }
        String updated = applyResolved(report, index, resolved);
        saveReviewReport(agentCode, updated);
        return updated;
    }

    /** 处理标记写回（静态可测）：items[index] 落 resolved 字段；下标越界/结构异常抛提示 */
    public static String applyResolved(String reportJson, int index, boolean resolved) {
        try {
            ObjectMapper m = new ObjectMapper();
            JsonNode root = m.readTree(reportJson);
            JsonNode items = root.get("items");
            if (!(root instanceof ObjectNode) || items == null || !items.isArray()
                    || index < 0 || index >= items.size()) {
                throw new NoticeException("评审条目下标越界: " + index);
            }
            ((ObjectNode) items.get(index)).put("resolved", resolved);
            return m.writeValueAsString(root);
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            throw new NoticeException("处理标记保存失败: " + e.getMessage());
        }
    }

    /** 标准八类过滤：评审报告/提示词模板等非标准行不进发布快照与一致性校验/自动修复 */
    public static List<InsightAgentAsset> standardOnly(List<InsightAgentAsset> list) {
        List<InsightAgentAsset> out = new ArrayList<>();
        if (list != null) {
            for (InsightAgentAsset asset : list) {
                if (ASSET_TYPES.contains(asset.getAssetType())) {
                    out.add(asset);
                }
            }
        }
        return out;
    }

    /** 发布：草稿先过交叉一致性校验（悬空引用等 error 阻断），通过后拷贝进发布快照并整体置 1
     *  （草稿确认流程的终点，随后 /refresh 生效）。快照列独立于草稿列，改稿回 status=0 不销毁线上版本 */
    public int publish(String agentCode) {
        List<InsightAgentAsset> list = insightAgentAssetService.select(
                new QueryWrapper<InsightAgentAsset>().eq("agent_code", agentCode));
        if (FuncUtil.isEmpty(list)) {
            throw new NoticeException("Agent [" + agentCode + "] 尚无资产，请先生成");
        }
        // 非标准八类行（评审报告/提示词模板）不进校验与发布快照
        List<InsightAgentAsset> standard = standardOnly(list);
        Map<String, String> contents = new LinkedHashMap<>();
        for (InsightAgentAsset asset : standard) {
            contents.put(asset.getAssetType(), asset.getContent());
        }
        AssetConsistencyValidator.Result vr = AssetConsistencyValidator.validate(contents);
        if (vr.hasErrors()) {
            List<String> msgs = vr.errorMessages();
            log.warn("Agent '{}' 发布校验未通过，共 {} 个问题：{}", agentCode, msgs.size(), msgs);
            throw new NoticeException("发布校验未通过：" + String.join("；", msgs));
        }
        int count = 0;
        for (InsightAgentAsset asset : standard) {
            asset.setPublishedContent(asset.getContent());
            asset.setStatus("1");
            insightAgentAssetService.updateById(asset);
            count++;
        }
        return count;
    }

    /** 资产草稿交叉一致性校验（前端 /validate 发布前预检）：只读草稿不落库；
     *  无草稿返回空结果（交由发布环节报「尚无资产」） */
    public AssetConsistencyValidator.Result validateDrafts(String agentCode) {
        List<InsightAgentAsset> list = insightAgentAssetService.select(
                new QueryWrapper<InsightAgentAsset>().eq("agent_code", agentCode));
        Map<String, String> contents = new LinkedHashMap<>();
        for (InsightAgentAsset asset : standardOnly(list)) {
            contents.put(asset.getAssetType(), asset.getContent());
        }
        return AssetConsistencyValidator.validate(contents);
    }

    /** 发布校验错误一键自动修复：悬空引用类（失效资产/悬空引用/重名）确定性修复后落草稿，
     *  修复后重校验返回剩余问题；安全设施类（敏感字段/行权限）不自动处理留给人工；
     *  返回 {summary 逐类修复摘要, hasErrors, issues 修复后剩余问题} */
    public Map<String, Object> autoFixDrafts(String agentCode) {
        List<InsightAgentAsset> list = insightAgentAssetService.select(
                new QueryWrapper<InsightAgentAsset>().eq("agent_code", agentCode));
        Map<String, String> contents = new LinkedHashMap<>();
        for (InsightAgentAsset asset : standardOnly(list)) {
            contents.put(asset.getAssetType(), asset.getContent());
        }
        AssetAutoFixer.Result fr = AssetAutoFixer.fix(contents);
        for (Map.Entry<String, String> e : fr.getFixedContents().entrySet()) {
            saveAssetDraft(agentCode, e.getKey(), e.getValue());
        }
        if (fr.hasChanges()) {
            log.info("Agent '{}' 发布校验错误自动修复：{}", agentCode, String.join("；", fr.getSummary()));
        }
        AssetConsistencyValidator.Result vr = validateDrafts(agentCode);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", fr.getSummary());
        out.put("hasErrors", vr.hasErrors());
        out.put("issues", vr.getIssues());
        return out;
    }

    /** 配置纠错自查（规则全部收敛在 ConfigConflictDetector，此处只编排）：
     *  探出单位矛盾/缺单位/码值域缺码疑似项，返前端逐条裁决（无一键确认）；
     *  自查失败不阻断页面（辅助不是闸），无实体草稿/无数据源绑定返空清单 */
    public List<ConfigCheckFinding> detectConflicts(String agentCode) {
        InsightAgent agent = insightAgentService.selectOne(
                new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
        if (agent == null || FuncUtil.isEmpty(agent.getDsName())) {
            return Collections.emptyList();
        }
        List<EntityDef> entities = loadEntitiesDraft(agentCode);
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        try (Connection conn = dataSourceCacheService.getDataSource(agent.getDsName()).getConnection()) {
            return ConfigConflictDetector.detect(conn, entities, loadDomainsDraft(agentCode));
        } catch (Exception e) {
            log.warn("Agent '{}' 配置自查失败（不阻断页面）: {}", agentCode, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 逐条裁决配置自查疑点（一次一条，无批量）：裁决写回落 ConfigConflictDetector.applyResolution，
     *  应用成功即存草稿（裁决即经验：unitVerified/ignoredCodes 随重建与模板复用携走），再自查返剩余清单 */
    public List<ConfigCheckFinding> resolveConflict(String agentCode, ConfigCheckResolution r) {
        List<EntityDef> entities = loadEntitiesDraft(agentCode);
        Map<String, ValueDomainDef> domains = loadDomainsDraft(agentCode);
        if (ConfigConflictDetector.applyResolution(entities, domains, r)) {
            // entities 走 saveAssetDraft 顺带归类目录派生；两类都存（幂等，省得区分改了哪类）
            saveAssetDraft(agentCode, "entities", writeJson(entities));
            saveAssetDraft(agentCode, "value-domains", writeDomains(domains));
            log.info("Agent '{}' 配置自查裁决落经验：type={}, {}.{}", agentCode,
                    r.getType(), r.getEntity(), FuncUtil.isNotEmpty(r.getDomainKey()) ? r.getDomainKey() : r.getField());
        }
        return detectConflicts(agentCode);
    }

    /** value-domains 草稿反序列化（同 mergeDomains 口径：domains map 逐项 treeToValue） */
    private Map<String, ValueDomainDef> loadDomainsDraft(String agentCode) {
        LinkedHashMap<String, ValueDomainDef> out = new LinkedHashMap<>();
        InsightAgentAsset asset = getAsset(agentCode, "value-domains");
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return out;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode doms = om.readTree(asset.getContent()).path("domains");
            java.util.Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> it = doms.fields();
            while (it.hasNext()) {
                Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> entry = it.next();
                out.put(entry.getKey(), om.treeToValue(entry.getValue(), ValueDomainDef.class));
            }
        } catch (Exception e) {
            log.warn("Agent '{}' value-domains 草稿解析失败，自查跳过码值类: {}", agentCode, e.getMessage());
        }
        return out;
    }

    /** 保存草稿：upsert，存在即覆盖内容并回到草稿态 */
    private void upsertDraft(String agentCode, String assetType, String content) {
        saveAssetDraft(agentCode, assetType, content);
    }

    /** value-domains 资产：{schema_version, domains:{key: def}} */
    private String writeDomains(Map<String, ValueDomainDef> domains) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", "1.0");
        root.put("domains", domains);
        return writeJson(root);
    }

    private String writeJson(Object value) {
        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("资产 JSON 序列化失败", e);
        }
    }
}
