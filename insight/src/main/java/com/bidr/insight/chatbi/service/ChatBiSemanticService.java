package com.bidr.insight.chatbi.service;

import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalIndicatorGroupService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.holder.PortalConfigContext;
import com.bidr.admin.service.statistic.AdminPortalDashboardStatisticService;
import com.bidr.admin.vo.statistic.DashboardStatisticVO;
import com.bidr.admin.vo.statistic.IndicatorItem;
import com.bidr.admin.vo.statistic.IndicatorRes;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.insight.chatbi.vo.ChatBiSemanticCatalog;
import com.bidr.insight.smartquery.vo.SemanticField;
import com.bidr.insight.smartquery.vo.SemanticField.SemanticValue;
import com.bidr.insight.chatbi.vo.SemanticIndicator;
import com.bidr.insight.chatbi.vo.SemanticIndicatorGroup;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.service.cache.DictTreeCacheService;
import com.bidr.platform.service.cache.dict.DictCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: ChatBiSemanticService
 * Description: 智能问数语义目录构建——把 portal 语义层（指标卡片 + indicator 筛选组 + 筛选字段）
 * 聚合为大模型可引用的目录边界（tableId 即 portalName，入口按 sys_portal 校验）：
 * <ul>
 *     <li>指标目录：sys_portal_dashboard_statistic（实时查询，随权限过滤）</li>
 *     <li>筛选组目录：sys_portal_indicator_group/sys_portal_indicator（口语筛选项，condition 解析为叶子条件）</li>
 *     <li>字段目录：DATASET 模式取 forge 数据集列，其余取 portal 可筛选列（与用户无关，缓存5分钟）</li>
 *     <li>值域：enum/tree 字段取字典项（value 即前端筛选提交的 dictValue），
 *     DATASET 列按 remark 约定声明值域与 dateFormat</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBiSemanticService {

    private static final String DATA_MODE_DATASET = "DATASET";
    private static final long FIELD_CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 值域注入上限：超出截断，避免大字典（如项目清单类）撑爆提示词
     */
    private static final int MAX_FIELD_VALUES = 30;

    /**
     * 数据集列 remark 的值域约定前缀，如“值域：在建/完工/停工”
     */
    private static final String REMARK_VALUES_PREFIX = "值域：";

    /**
     * 数据集列 remark 可声明的日期格式标识（与图表 dateFormat 六种约定一致）
     */
    private static final List<String> DATE_FORMATS = Arrays.asList(
            "DATETIME", "YYYY-MM-DD", "YYYYMMDD", "YYYY-MM", "YYYYMM", "YYYY");

    private final AdminPortalDashboardStatisticService statisticService;
    private final SysPortalService sysPortalService;
    private final SysPortalIndicatorGroupService sysPortalIndicatorGroupService;
    private final SysDatasetColumnService sysDatasetColumnService;
    private final DictCacheService dictCacheService;
    private final DictTreeCacheService dictTreeCacheService;
    private final ChatBiSensitiveService chatBiSensitiveService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 字段目录与用户无关，按 tableId 缓存；指标目录含权限过滤，必须实时查询
     */
    private final ConcurrentHashMap<String, FieldCacheEntry> fieldCache = new ConcurrentHashMap<>();

    /**
     * 构建指定 tableId（即 portalName）的语义目录；出口做敏感过滤（缓存存全量、出口做过滤）：
     * 敏感列定义保留（供指名查询）但 values 清空并加 sensitive/replaceProperty 标注，
     * indicatorGroups 条件里命中敏感列的叶子剔除（敏感值不因预设筛选组透传给模型）
     */
    public ChatBiSemanticCatalog getSemanticCatalog(String tableId) {
        SysPortal portal = sysPortalService.getByName(tableId, null);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "看板视图");

        ChatBiSemanticCatalog catalog = new ChatBiSemanticCatalog();
        catalog.setTableId(tableId);
        catalog.setPortalName(tableId);
        catalog.setIndicators(loadIndicators(tableId));
        catalog.setIndicatorGroups(loadIndicatorGroups(tableId));
        catalog.setFields(loadFields(tableId, tableId));
        applySensitiveGuard(catalog, chatBiSensitiveService.getSensitiveReplaceMap(tableId));
        return catalog;
    }

    /**
     * 敏感出口过滤：fields 命中敏感集的条目拷贝改写（不污染 fieldCache 全量缓存）——
     * values 清空（值域即批量取值，绝不外泄）+ sensitive/replaceProperty 标注；
     * indicatorGroups 各项 conditions 剔除命中叶子，剔空置 null（该项退化为仅展示，提示词规则兜底）
     */
    private void applySensitiveGuard(ChatBiSemanticCatalog catalog, Map<String, String> sensitive) {
        if (sensitive.isEmpty()) {
            return;
        }
        List<SemanticField> guarded = new ArrayList<>(catalog.getFields().size());
        for (SemanticField field : catalog.getFields()) {
            if (sensitive.containsKey(field.getProperty())) {
                SemanticField copy = new SemanticField();
                copy.setProperty(field.getProperty());
                copy.setLabel(field.getLabel());
                copy.setFieldType(field.getFieldType());
                copy.setDictName(field.getDictName());
                copy.setDateFormat(field.getDateFormat());
                copy.setAggregate(field.getAggregate());
                copy.setSensitive(Boolean.TRUE);
                copy.setReplaceProperty(sensitive.get(field.getProperty()));
                guarded.add(copy);
            } else {
                guarded.add(field);
            }
        }
        catalog.setFields(guarded);
        if (catalog.getIndicatorGroups() != null) {
            for (SemanticIndicatorGroup group : catalog.getIndicatorGroups()) {
                if (group.getItems() == null) {
                    continue;
                }
                for (SemanticIndicatorGroup.Item item : group.getItems()) {
                    if (item.getConditions() == null) {
                        continue;
                    }
                    List<SemanticIndicatorGroup.Condition> kept = new ArrayList<>(item.getConditions().size());
                    for (SemanticIndicatorGroup.Condition condition : item.getConditions()) {
                        if (!sensitive.containsKey(condition.getProperty())) {
                            kept.add(condition);
                        }
                    }
                    item.setConditions(kept.isEmpty() ? null : kept);
                }
            }
        }
    }

    /**
     * 指标卡片目录：与 ChartCard 取数同源（含资源权限过滤），
     * id 即前端图表生成物引用的 indicatorId
     */
    private List<SemanticIndicator> loadIndicators(String tableId) {
        List<DashboardStatisticVO> statistics;
        try {
            statistics = statisticService.getCommonStatistic(tableId);
        } catch (Exception e) {
            // 权限过滤依赖登录态（PermitService.isAdmin 校验 token），
            // 免登录调试态等场景下降级为空目录，不阻断 fields/indicatorGroups 装载
            log.warn("装载统计指标目录失败, tableId={}: {}", tableId, e.getMessage());
            return new ArrayList<>();
        }
        List<SemanticIndicator> indicators = new ArrayList<>();
        for (DashboardStatisticVO vo : statistics) {
            SemanticIndicator item = new SemanticIndicator();
            item.setId(vo.getId());
            item.setTitle(vo.getTitle());
            item.setSubTitle(vo.getSubTitle());
            item.setDescription(vo.getDescription());
            parseIndicatorJson(vo.getIndicator(), item);
            indicators.add(item);
        }
        return indicators;
    }

    /**
     * indicator 筛选组目录：与看板筛选区同源（sys_portal_indicator_group + 组下有效项），
     * 是口语筛选（如"华北区域"）的语义落点；condition 解析为叶子条件，项命中后原样引用
     */
    private List<SemanticIndicatorGroup> loadIndicatorGroups(String portalName) {
        List<IndicatorRes> groups = sysPortalIndicatorGroupService.getIndicator(portalName);
        List<SemanticIndicatorGroup> result = new ArrayList<>();
        for (IndicatorRes group : groups) {
            if (FuncUtil.isEmpty(group.getItems())) {
                continue;
            }
            SemanticIndicatorGroup semanticGroup = new SemanticIndicatorGroup();
            semanticGroup.setTitle(group.getTitle());
            List<SemanticIndicatorGroup.Item> items = new ArrayList<>();
            for (IndicatorItem item : group.getItems()) {
                if (FuncUtil.isEmpty(item.getTitle())) {
                    continue;
                }
                SemanticIndicatorGroup.Item semanticItem = new SemanticIndicatorGroup.Item();
                semanticItem.setTitle(item.getTitle());
                semanticItem.setKey(item.getKey());
                semanticItem.setConditions(parseIndicatorConditions(item.getCondition()));
                items.add(semanticItem);
            }
            if (!items.isEmpty()) {
                semanticGroup.setItems(items);
                result.add(semanticGroup);
            }
        }
        return result;
    }

    /**
     * 解析 indicator 项 condition JSON（{"conditionList":[{property,relation,value}]}）为叶子条件；
     * 解析失败或无有效叶子返回 null，由提示词规则兜底（该项仅作展示不可引用）
     */
    private List<SemanticIndicatorGroup.Condition> parseIndicatorConditions(String condition) {
        if (FuncUtil.isEmpty(condition)) {
            return null;
        }
        try {
            List<SemanticIndicatorGroup.Condition> conditions = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(condition).path("conditionList")) {
                SemanticIndicatorGroup.Condition item = new SemanticIndicatorGroup.Condition();
                item.setProperty(node.path("property").asText(null));
                item.setRelation(node.has("relation") ? node.path("relation").asInt() : null);
                List<String> values = new ArrayList<>();
                for (JsonNode value : node.path("value")) {
                    if (value.isValueNode() && StringUtils.hasText(value.asText())) {
                        values.add(value.asText());
                    }
                }
                if (StringUtils.hasText(item.getProperty()) && !values.isEmpty()) {
                    item.setValue(values);
                    conditions.add(item);
                }
            }
            return conditions.isEmpty() ? null : conditions;
        } catch (Exception e) {
            log.warn("解析 indicator 项条件失败: {}", condition);
            return null;
        }
    }

    /**
     * 解析 indicator JSON 中的维度项/统计指标/图表类型摘要，单条解析失败不影响整体
     */
    private void parseIndicatorJson(String indicator, SemanticIndicator item) {
        item.setDimensions(Collections.emptyList());
        item.setMetrics(Collections.emptyList());
        item.setChartTypes(Collections.emptyList());
        if (FuncUtil.isEmpty(indicator)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(indicator);
            Set<String> dimensions = new LinkedHashSet<>();
            for (String dimensionKey : new String[]{"firstDimension", "secondDimension"}) {
                for (JsonNode node : root.path(dimensionKey).path("indicatorItems")) {
                    String name = node.path("itemName").asText(null);
                    if (StringUtils.hasText(name)) {
                        dimensions.add(name);
                    }
                }
            }
            Set<String> metrics = new LinkedHashSet<>();
            Set<String> chartTypes = new LinkedHashSet<>();
            for (JsonNode node : root.path("dataMetrics")) {
                String name = node.path("dataName").asText(null);
                if (StringUtils.hasText(name)) {
                    metrics.add(name);
                }
                String type = node.path("chartType").asText(null);
                if (StringUtils.hasText(type)) {
                    chartTypes.add(type);
                }
            }
            item.setDimensions(new ArrayList<>(dimensions));
            item.setMetrics(new ArrayList<>(metrics));
            item.setChartTypes(new ArrayList<>(chartTypes));
        } catch (Exception e) {
            log.warn("解析指标配置摘要失败, statisticId={}: {}", item.getId(), e.getMessage());
        }
    }

    private List<SemanticField> loadFields(String tableId, String portalName) {
        FieldCacheEntry entry = fieldCache.get(tableId);
        if (entry != null && System.currentTimeMillis() - entry.timestamp < FIELD_CACHE_TTL_MS) {
            return entry.fields;
        }
        List<SemanticField> fields = buildFields(portalName);
        fieldCache.put(tableId, new FieldCacheEntry(System.currentTimeMillis(), fields));
        return fields;
    }

    private List<SemanticField> buildFields(String portalName) {
        SysPortal portal = sysPortalService.getByName(portalName, null);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "看板视图");
        if (DATA_MODE_DATASET.equals(portal.getDataMode()) && isNumeric(portal.getReferenceId())) {
            return buildDatasetFields(Long.parseLong(portal.getReferenceId()));
        }
        return buildPortalFields(portalName);
    }

    /**
     * DATASET 模式：字段目录取 forge 数据集列（可见列，alias 为属性名）；
     * remark 约定：显示名 + “值域：a/b/c”（段尾）+ 日期格式标识（六种之一），
     * 如“项目状态 值域：在建/完工/停工”、“开工日期（YYYY-MM-DD）”
     */
    private List<SemanticField> buildDatasetFields(Long datasetId) {
        List<SemanticField> fields = new ArrayList<>();
        for (SysDatasetColumn column : sysDatasetColumnService.getByDatasetId(datasetId)) {
            if (!CommonConst.YES.equals(column.getIsVisible())) {
                continue;
            }
            SemanticField field = new SemanticField();
            field.setProperty(column.getColumnAlias());
            String remark = StringUtils.hasText(column.getRemark()) ? column.getRemark().trim() : "";
            field.setLabel(parseDatasetLabel(remark, column.getColumnAlias()));
            field.setValues(parseDatasetValues(remark));
            field.setDateFormat(findDateFormat(remark));
            if (field.getValues() != null) {
                field.setFieldType("enum");
            } else if (field.getDateFormat() != null) {
                field.setFieldType("date");
            }
            field.setAggregate(CommonConst.YES.equals(column.getIsAggregate()));
            fields.add(field);
        }
        return fields;
    }

    /**
     * 剥离“值域：…”段与日期格式标识后余文作显示名；为空则回退列别名
     */
    private String parseDatasetLabel(String remark, String columnAlias) {
        String label = remark;
        int valuesIndex = label.indexOf(REMARK_VALUES_PREFIX);
        if (valuesIndex >= 0) {
            label = label.substring(0, valuesIndex);
        }
        String format = findDateFormat(label);
        if (format != null) {
            label = label.replace(format, " ");
        }
        label = label.replaceAll("^[（）()：:，。；,;\\s]+|[（）()：:，。；,;\\s]+$", "").trim();
        return StringUtils.hasText(label) ? label : columnAlias;
    }

    /**
     * 解析“值域：a/b/c”约定（遇日期格式标识或结尾截断），value 与 label 同值
     */
    private List<SemanticValue> parseDatasetValues(String remark) {
        int start = remark.indexOf(REMARK_VALUES_PREFIX);
        if (start < 0) {
            return null;
        }
        String segment = remark.substring(start + REMARK_VALUES_PREFIX.length());
        String format = findDateFormat(segment);
        if (format != null) {
            segment = segment.substring(0, segment.indexOf(format));
        }
        segment = segment.replaceAll("[，。；,;\\s]+$", "").trim();
        if (!StringUtils.hasText(segment)) {
            return null;
        }
        List<SemanticValue> values = new ArrayList<>();
        for (String item : segment.split("[/／|]")) {
            String value = item.trim();
            if (StringUtils.hasText(value) && values.size() < MAX_FIELD_VALUES) {
                values.add(new SemanticValue(value, value));
            }
        }
        return values.isEmpty() ? null : values;
    }

    /**
     * 提取文本中的日期格式标识；同位置长格式优先，避免 YYYY 误吞 YYYY-MM-DD 前缀
     */
    private String findDateFormat(String text) {
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String format : DATE_FORMATS) {
            int index = text.indexOf(format);
            if (index < 0) {
                continue;
            }
            if (index < bestIndex || (index == bestIndex && best != null && format.length() > best.length())) {
                best = format;
                bestIndex = index;
            }
        }
        return best;
    }

    /**
     * 实体/矩阵模式：字段目录取 portal 列；
     * fieldType 归并为语义类型，enum/tree 类透传字典值域（value 即筛选提交的 dictValue），
     * tree 类带出字典编码（自造 treeStackedBar 图表用）；
     * 可筛选列全收，未开筛选的数值类列也保留（自造图表的聚合指标原料）
     */
    private List<SemanticField> buildPortalFields(String portalName) {
        Long roleId = PortalConfigContext.getPortalConfigRoleId();
        List<SysPortalColumn> columns = sysPortalService.getColumnsByPortalName(portalName, roleId);
        List<SemanticField> fields = new ArrayList<>();
        for (SysPortalColumn column : columns) {
            String semanticType = semanticFieldType(column.getFieldType());
            boolean filterAble = CommonConst.YES.equals(column.getFilterAble());
            if (!filterAble && !isMetricFieldType(semanticType)) {
                continue;
            }
            SemanticField field = new SemanticField();
            field.setProperty(column.getProperty());
            field.setLabel(column.getDisplayName());
            field.setFieldType(semanticType);
            field.setValues(loadFieldValues(column));
            if ("tree".equals(semanticType) || "tree-multi".equals(semanticType)) {
                field.setDictName(column.getReference());
            }
            fields.add(field);
        }
        return fields;
    }

    /**
     * 数值类语义类型：自造图表（ChartBlueprint.metrics[].field）的聚合指标原料
     */
    private boolean isMetricFieldType(String semanticType) {
        return "number".equals(semanticType) || "money".equals(semanticType) || "percent".equals(semanticType);
    }

    /**
     * portal fieldType 归并为提示词语义类型，与前端 AdvancedSearch 的可用关系一一对应：
     * text/enum/enum-multi/tree/tree-multi/boolean/number/money/percent/date/datetime/entity；
     * public static 供敏感列配置页（ChatBiSensitiveService）同口径展示
     */
    public static String semanticFieldType(String fieldType) {
        PortalFieldDict dict = DictEnumUtil.getEnumByValue(fieldType, PortalFieldDict.class, PortalFieldDict.DEFAULT);
        switch (dict) {
            case ENUM:
                return "enum";
            case ENUM_MULTI_IN_ONE:
                return "enum-multi";
            case TREE:
                return "tree";
            case TREE_MULTI_IN_ONE:
                return "tree-multi";
            case BOOLEAN:
                return "boolean";
            case NUMBER:
                return "number";
            case MONEY:
                return "money";
            case PERCENT:
                return "percent";
            case DATE:
                return "date";
            case DATETIME:
                return "datetime";
            case ENTITY:
            case ENTITY_CONDITION:
                return "entity";
            default:
                return "text";
        }
    }

    /**
     * 按语义类型装载值域：boolean 固定 1/0（与前端开关一致），下拉取字典项，树形取树节点；
     * 超上限截断，未配 reference 或字典为空返回 null，由提示词规则兜底
     */
    private List<SemanticValue> loadFieldValues(SysPortalColumn column) {
        String semanticType = semanticFieldType(column.getFieldType());
        if ("boolean".equals(semanticType)) {
            return Arrays.asList(new SemanticValue("1", "是"), new SemanticValue("0", "否"));
        }
        if (!StringUtils.hasText(column.getReference())) {
            return null;
        }
        if ("enum".equals(semanticType) || "enum-multi".equals(semanticType)) {
            return loadDictValues(dictCacheService.getKeyValue(column.getReference()));
        }
        if ("tree".equals(semanticType) || "tree-multi".equals(semanticType)) {
            return loadDictValues(dictTreeCacheService.getAll(column.getReference()));
        }
        return null;
    }

    /**
     * 字典项转值域条目并截断；value 是筛选条件实际生效值
     */
    private List<SemanticValue> loadDictValues(List<? extends KeyValueResVO> dictItems) {
        if (FuncUtil.isEmpty(dictItems)) {
            return null;
        }
        List<SemanticValue> values = new ArrayList<>();
        for (KeyValueResVO item : dictItems) {
            if (FuncUtil.isEmpty(item.getValue())) {
                continue;
            }
            values.add(new SemanticValue(item.getValue().toString(), item.getLabel()));
            if (values.size() >= MAX_FIELD_VALUES) {
                break;
            }
        }
        return values.isEmpty() ? null : values;
    }

    private boolean isNumeric(String value) {
        if (FuncUtil.isEmpty(value)) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static class FieldCacheEntry {
        final long timestamp;
        final List<SemanticField> fields;

        FieldCacheEntry(long timestamp, List<SemanticField> fields) {
            this.timestamp = timestamp;
            this.fields = fields;
        }
    }
}
