package com.bidr.forge.service.widetable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.dao.entity.FormWideTableConfig;
import com.bidr.forge.dao.entity.FormWideTableConfigAttr;
import com.bidr.forge.dao.entity.FormWideTableSyncLog;
import com.bidr.forge.dao.repository.FormDataService;
import com.bidr.forge.dao.repository.FormWideTableSyncLogService;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 宽表数据收集服务
 * <p>
 * 从 FormData（EAV模型）中读取已配置字段的值，
 * 写入到物理宽表中。
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormWideTableCollector {

    private final FormWideTableManager formWideTableManager;
    private final FormDataService formDataService;
    private final FormWideTableSyncLogService syncLogService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 收集入口：处理单个配置，收集所有未同步的已提交记录
     *
     * @param configId 宽表配置 ID
     * @return 收集的记录数
     */
    public int collect(Long configId) {
        FormWideTableConfig config = formWideTableManager.getConfigById(configId);
        if (config == null) {
            log.warn("宽表配置不存在: {}", configId);
            return 0;
        }

        // 获取字段配置
        List<FormWideTableConfigAttr> attrs = formWideTableManager.getConfigAttrs(configId);
        if (FuncUtil.isEmpty(attrs)) {
            log.warn("宽表字段配置为空: {}", configId);
            return 0;
        }

        // 查询已同步的 historyId 集合
        Set<String> syncedHistoryIds = getSyncedHistoryIds(configId);

        // 获取业务上下文提供者（通过 Spring 注入的可选依赖）
        List<WideTableBusinessContext> contexts = getSubmittedContexts(config.getFormId(), new ArrayList<>(syncedHistoryIds));
        if (FuncUtil.isEmpty(contexts)) {
            log.debug("没有待收集的记录: configId={}", configId);
            return 0;
        }

        int successCount = 0;
        for (WideTableBusinessContext context : contexts) {
            try {
                collectOne(config, attrs, context);
                saveSyncLog(configId, context.getHistoryId(), "success", null);
                successCount++;
            } catch (Exception e) {
                log.error("收集记录失败: historyId={}", context.getHistoryId(), e);
                saveSyncLog(configId, context.getHistoryId(), "fail", e.getMessage());
            }
        }

        log.info("宽表收集完成: configId={}, 总数={}, 成功={}", configId, contexts.size(), successCount);
        return successCount;
    }

    /**
     * 收集所有 active 配置
     *
     * @return 总收集记录数
     */
    public int collectAll() {
        List<FormWideTableConfig> configs = formWideTableManager.getActiveConfigs();
        int total = 0;
        for (FormWideTableConfig config : configs) {
            try {
                total += collect(config.getId());
            } catch (Exception e) {
                log.error("收集配置失败: configId={}", config.getId(), e);
            }
        }
        return total;
    }

    /**
     * 收集单条记录
     */
    private void collectOne(FormWideTableConfig config, List<FormWideTableConfigAttr> attrs, WideTableBusinessContext context) {
        // 1. 读取 FormData
        Set<Long> attrIds = attrs.stream()
                .map(FormWideTableConfigAttr::getAttributeId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<FormData> dataWrapper = formDataService.getQueryWrapper();
        dataWrapper.eq(FormData::getHistoryId, context.getHistoryId())
                .in(FormData::getAttributeId, attrIds)
                .eq(FormData::getValid, "1");
        List<FormData> dataList = formDataService.select(dataWrapper);

        // 2. 构建 attributeId -> value 映射
        Map<Long, String> valueMap = new HashMap<>();
        for (FormData data : dataList) {
            valueMap.put(data.getAttributeId(), data.getValue());
        }

        // 3. 构建 INSERT SQL
        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        // 框架固定列: history_id
        columns.append("history_id");
        placeholders.append(":historyId");
        params.put("historyId", context.getHistoryId());

        // 业务固定列（从 configProvider 获取，通过 contextKey 动态取值）
        for (WideTableFixedColumn fc : getFixedColumns()) {
            String colName = fc.getColumnName();
            Object value = context.getContextValue(fc.getContextKey());
            // datetime 类型需要 Long → Timestamp 转换
            if (value instanceof Long && fc.getColumnType().toLowerCase().contains("datetime")) {
                value = new java.sql.Timestamp((Long) value);
            }
            columns.append(", ").append(colName);
            placeholders.append(", :").append(colName);
            params.put(colName, value);
        }

        // 动态列
        for (FormWideTableConfigAttr attr : attrs) {
            String colName = attr.getColumnName();
            String value = valueMap.get(attr.getAttributeId());

            columns.append(", ").append(colName);
            placeholders.append(", :").append(colName);
            params.put(colName, value);
        }

        // 先尝试删除旧记录（如果已存在），再 INSERT
        String deleteSql = "DELETE FROM `" + config.getTableName() + "` WHERE history_id = :historyId";
        jdbcConnectService.executeUpdate(deleteSql, params);

        String insertSql = "INSERT INTO `" + config.getTableName() + "` (" + columns + ") VALUES (" + placeholders + ")";
        jdbcConnectService.executeUpdate(insertSql, params);
    }

    /**
     * 查询已同步的 historyId 集合
     */
    private Set<String> getSyncedHistoryIds(Long configId) {
        LambdaQueryWrapper<FormWideTableSyncLog> wrapper = syncLogService.getQueryWrapper();
        wrapper.eq(FormWideTableSyncLog::getConfigId, configId)
                .eq(FormWideTableSyncLog::getStatus, "success");
        List<FormWideTableSyncLog> logs = syncLogService.select(wrapper);
        return logs.stream()
                .map(FormWideTableSyncLog::getHistoryId)
                .collect(Collectors.toSet());
    }

    /**
     * 保存同步日志
     */
    private void saveSyncLog(Long configId, String historyId, String status, String errorMsg) {
        // 先删除旧的日志（如果有）
        LambdaQueryWrapper<FormWideTableSyncLog> delWrapper = syncLogService.getQueryWrapper();
        delWrapper.eq(FormWideTableSyncLog::getConfigId, configId)
                .eq(FormWideTableSyncLog::getHistoryId, historyId);
        syncLogService.delete(delWrapper);

        FormWideTableSyncLog log = new FormWideTableSyncLog();
        log.setConfigId(configId);
        log.setHistoryId(historyId);
        log.setStatus(status);
        log.setErrorMsg(errorMsg);
        log.setSyncedAt(new Date());
        syncLogService.insert(log);
    }

    // ==================== 业务上下文与配置获取 ====================

    /**
     * 业务上下文提供者（可选注入，由业务层实现）
     */
    private WideTableBusinessContextProvider contextProvider;

    /**
     * 宽表配置提供者（可选注入，由业务层提供固定列定义）
     */
    private WideTableConfigProvider configProvider;

    /**
     * 注入业务上下文提供者（由业务层调用）
     */
    public void setContextProvider(WideTableBusinessContextProvider provider) {
        this.contextProvider = provider;
    }

    /**
     * 注入宽表配置提供者（由业务层调用）
     */
    public void setConfigProvider(WideTableConfigProvider provider) {
        this.configProvider = provider;
    }

    /**
     * 获取业务固定列定义（无 provider 时返回空列表）
     */
    private List<WideTableFixedColumn> getFixedColumns() {
        if (configProvider == null) return Collections.emptyList();
        return configProvider.getFixedColumns();
    }

    /**
     * 获取待收集的已提交记录列表
     */
    private List<WideTableBusinessContext> getSubmittedContexts(String formId, List<String> excludeIds) {
        if (contextProvider == null) {
            log.warn("业务上下文提供者未配置，无法获取已提交记录");
            return Collections.emptyList();
        }
        return contextProvider.getSubmittedHistories(formId, excludeIds);
    }
}
