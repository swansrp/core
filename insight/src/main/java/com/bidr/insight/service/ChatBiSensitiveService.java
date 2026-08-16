package com.bidr.insight.service;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.insight.dao.entity.ChatBiSensitiveColumn;
import com.bidr.insight.dao.repository.ChatBiSensitiveColumnService;
import com.bidr.insight.vo.ChatBiSensitiveColumnRes;
import com.bidr.insight.vo.ChatBiSensitiveSaveReq;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: ChatBiSensitiveService
 * Description: 智能问数敏感列配置——单一事实源：所有"进大模型的出口"只准调这里取敏感集合。
 * 语义是"值不外泄"而非"列消失"：目录保留列定义（供用户指名查询），清空值域清单；
 * 配对替换列（如 项目名称→项目编号）供大模型跨轮/批量子集查询与回显翻译。
 * <ul>
 *     <li>全量列清单：portal 模式取主角色权威列副本（管理页无 PortalConfigContext，
 *     按 portal.roleId 取列，按 property 去重——角色副本差异只在权限不在语义）；DATASET 模式取可见列</li>
 *     <li>本地缓存 + 保存即清：配置即改即生效，不受语义目录 fieldCache 5 分钟缓存影响（缓存存全量、出口做过滤）</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatBiSensitiveService {

    private static final String DATA_MODE_DATASET = "DATASET";

    private final ChatBiSensitiveColumnService sensitiveColumnService;
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;
    private final SysDatasetColumnService sysDatasetColumnService;

    /**
     * 敏感配置按看板缓存（tableCode → property→replaceProperty）；保存时清除
     */
    private final ConcurrentHashMap<String, Map<String, String>> sensitiveCache = new ConcurrentHashMap<>();

    /**
     * 敏感 property → 配对替换 property（未配对为 null 值）映射；空 Map 即该板无敏感配置
     */
    public Map<String, String> getSensitiveReplaceMap(String tableCode) {
        if (!StringUtils.hasText(tableCode)) {
            return new LinkedHashMap<>();
        }
        return sensitiveCache.computeIfAbsent(tableCode, code -> {
            Map<String, String> map = new LinkedHashMap<>();
            for (ChatBiSensitiveColumn column : sensitiveColumnService.listByTableCode(code)) {
                map.put(column.getColumnProperty(), StringUtils.hasText(column.getReplaceProperty())
                        ? column.getReplaceProperty().trim() : null);
            }
            return map;
        });
    }

    /**
     * 敏感字段使用约定段（仅当该板存在敏感配置时生成，追加进 promptExtra——
     * 自定义链只要模板带 {{promptExtra}} 占位符即同样生效）；无敏感配置返回空串
     */
    public String buildSensitivePromptNote(String tableId) {
        Map<String, String> sensitive = getSensitiveReplaceMap(tableId);
        if (sensitive.isEmpty()) {
            return "";
        }
        StringBuilder note = new StringBuilder("\n\n【敏感字段约定】\n");
        note.append("以下字段的业务数据取值不外泄（目录中它们不带 values 属正常）：");
        StringJoiner joiner = new StringJoiner("、");
        sensitive.keySet().forEach(joiner::add);
        note.append(joiner).append("。\n");
        note.append("- 用户指名查询单条记录时，可用敏感字段按用户原话拼 1-等于 或 9-模糊匹配 条件（值取自用户原话，禁止展开其它取值）；\n");
        note.append("- 按敏感字段统计、分组、列表或跨轮引用时，改用其配对编码列完成——这是正常查询能力而非降级，照常出数出图：\n");
        boolean paired = false;
        for (Map.Entry<String, String> entry : sensitive.entrySet()) {
            if (StringUtils.hasText(entry.getValue())) {
                note.append("  ").append(entry.getKey()).append(" → ").append(entry.getValue()).append('\n');
                paired = true;
            }
        }
        if (paired) {
            note.append("  · 作分组/聚合维度：直接用配对编码列做维度字段；\n");
            note.append("  · 作列表或子集条件：用配对编码列拼条件（优先 11-在列表内）；\n");
        } else {
            note.append("  （本板敏感字段未配对编码列，涉及批量子集时说明局限即可）\n");
        }
        note.append("- 回答中不要罗列、猜测或转述敏感字段的任何批量取值；也不要向用户解释敏感限制、宣称\"不能\"或建议手动筛选——口径切换为配对列后，按配对列口径自然陈述结果即可，用户想看某条具体值时引导其指名询问。");
        return note.toString();
    }

    /**
     * 管理页列清单：看板全量有效列 + 敏感标记回显
     *（数据源用全量列而非仅语义目录列：不可筛选的文本列同样可能经数据回流外泄，一并可配）
     */
    public List<ChatBiSensitiveColumnRes> listColumnsWithFlag(String tableId) {
        SysPortal portal = sysPortalService.getByName(tableId, null);
        Validator.assertNotNull(portal, ErrCodeSys.PA_DATA_NOT_EXIST, "看板视图");
        List<ChatBiSensitiveColumnRes> result = new ArrayList<>();
        if (DATA_MODE_DATASET.equals(portal.getDataMode()) && isNumeric(portal.getReferenceId())) {
            for (SysDatasetColumn column : sysDatasetColumnService.getByDatasetId(Long.parseLong(portal.getReferenceId()))) {
                if (!CommonConst.YES.equals(column.getIsVisible())) {
                    continue;
                }
                ChatBiSensitiveColumnRes item = new ChatBiSensitiveColumnRes();
                item.setProperty(column.getColumnAlias());
                item.setLabel(column.getColumnAlias());
                item.setRemark(StringUtils.hasText(column.getRemark()) ? column.getRemark().trim() : null);
                result.add(item);
            }
        } else {
            Set<String> seen = new HashSet<>();
            for (SysPortalColumn column : sysPortalColumnService.getPropertyListByPortalId(portal.getId(), portal.getRoleId())) {
                // 角色副本列以 property 去重（副本差异只在权限不在语义）；禁用列不进清单
                if (!CommonConst.YES.equals(column.getEnable()) || !seen.add(column.getProperty())) {
                    continue;
                }
                ChatBiSensitiveColumnRes item = new ChatBiSensitiveColumnRes();
                item.setProperty(column.getProperty());
                item.setLabel(column.getDisplayName());
                item.setFieldType(ChatBiSemanticService.semanticFieldType(column.getFieldType()));
                result.add(item);
            }
        }
        Map<String, String> sensitive = getSensitiveReplaceMap(tableId);
        for (ChatBiSensitiveColumnRes item : result) {
            if (sensitive.containsKey(item.getProperty())) {
                item.setSensitive(Boolean.TRUE);
                item.setReplaceProperty(sensitive.get(item.getProperty()));
            } else {
                item.setSensitive(Boolean.FALSE);
            }
        }
        return result;
    }

    /**
     * 整板覆盖保存：校验（property/replaceProperty 必须在该板列清单内、不得自配、配对列不得本身是敏感列）
     * 后落库并清缓存（即改即生效）
     */
    public void saveSensitiveColumns(ChatBiSensitiveSaveReq req) {
        List<ChatBiSensitiveColumnRes> allColumns = listColumnsWithFlag(req.getTableId());
        Map<String, String> labelMap = new HashMap<>();
        Set<String> properties = new HashSet<>();
        for (ChatBiSensitiveColumnRes item : allColumns) {
            properties.add(item.getProperty());
            labelMap.put(item.getProperty(), item.getLabel());
        }
        Set<String> sensitiveProps = new HashSet<>();
        for (ChatBiSensitiveSaveReq.Column column : FuncUtil.isEmpty(req.getColumns())
                ? new ArrayList<ChatBiSensitiveSaveReq.Column>() : req.getColumns()) {
            String property = column.getProperty().trim();
            if (!properties.contains(property)) {
                throw new IllegalArgumentException("敏感列不在该看板列清单内: " + property);
            }
            if (!sensitiveProps.add(property)) {
                throw new IllegalArgumentException("敏感列重复配置: " + property);
            }
        }
        List<ChatBiSensitiveColumn> rows = new ArrayList<>();
        for (ChatBiSensitiveSaveReq.Column column : FuncUtil.isEmpty(req.getColumns())
                ? new ArrayList<ChatBiSensitiveSaveReq.Column>() : req.getColumns()) {
            String property = column.getProperty().trim();
            String replaceProperty = StringUtils.hasText(column.getReplaceProperty())
                    ? column.getReplaceProperty().trim() : null;
            if (replaceProperty != null) {
                if (replaceProperty.equals(property)) {
                    throw new IllegalArgumentException("敏感列不能与自身配对: " + property);
                }
                if (!properties.contains(replaceProperty)) {
                    throw new IllegalArgumentException("配对列不在该看板列清单内: " + replaceProperty);
                }
                if (sensitiveProps.contains(replaceProperty)) {
                    throw new IllegalArgumentException("配对列本身是敏感列，无法承担替换查询: " + replaceProperty);
                }
            }
            ChatBiSensitiveColumn row = new ChatBiSensitiveColumn();
            row.setTableCode(req.getTableId());
            row.setColumnProperty(property);
            row.setColumnLabel(labelMap.get(property));
            row.setReplaceProperty(replaceProperty);
            rows.add(row);
        }
        sensitiveColumnService.replaceAll(req.getTableId(), rows);
        sensitiveCache.remove(req.getTableId());
        log.info("敏感列配置已保存, tableId={}, count={}", req.getTableId(), rows.size());
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
}
