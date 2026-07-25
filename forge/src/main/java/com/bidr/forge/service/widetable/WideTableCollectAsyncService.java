package com.bidr.forge.service.widetable;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.dao.entity.FormWideTableConfig;
import com.bidr.forge.dao.entity.FormWideTableConfigAttr;
import com.bidr.forge.dao.entity.FormWideTableSyncLog;
import com.bidr.forge.dao.repository.FormDataService;
import com.bidr.forge.dao.repository.FormWideTableSyncLogService;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.upload.UploadProgressStep;
import com.bidr.platform.vo.upload.PortalUploadProgressRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 宽表异步收集服务（带进度上报）
 * <p>
 * 通过 TokenService 将进度写入 Redis，前端轮询进度接口展示进度条。
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WideTableCollectAsyncService {

    private final FormWideTableManager formWideTableManager;
    private final FormDataService formDataService;
    private final FormWideTableSyncLogService syncLogService;
    private final JdbcConnectService jdbcConnectService;
    private final TokenService tokenService;

    private static final String PROGRESS_KEY = "UPLOAD_PROGRESS_WideTableCollect";

    /**
     * 业务上下文提供者
     */
    private WideTableBusinessContextProvider contextProvider;

    /**
     * 宽表配置提供者
     */
    private WideTableConfigProvider configProvider;

    public void setContextProvider(WideTableBusinessContextProvider provider) {
        this.contextProvider = provider;
    }

    public void setConfigProvider(WideTableConfigProvider provider) {
        this.configProvider = provider;
    }

    /**
     * 获取进度
     */
    public PortalUploadProgressRes getProgress() {
        PortalUploadProgressRes res = tokenService.getItem(PROGRESS_KEY, PortalUploadProgressRes.class);
        if (res == null) {
            res = new PortalUploadProgressRes(UploadProgressStep.INIT, 0, 0, new ArrayList<>());
        }
        return res;
    }

    private void setProgress(PortalUploadProgressRes progress) {
        tokenService.putItem(PROGRESS_KEY, progress);
    }

    /**
     * 异步执行重新收集（全量：清除旧数据 + 重建表 + 全量收集）
     */
    @Async
    public void reCollectAsync(Long configId) {
        try {
            FormWideTableConfig config = formWideTableManager.getConfigById(configId);
            if (config == null) {
                setProgress(new PortalUploadProgressRes(UploadProgressStep.FAILED, 0, 0,
                        new ArrayList<>(Collections.singletonList("宽表配置不存在: " + configId))));
                return;
            }

            List<FormWideTableConfigAttr> attrs = formWideTableManager.getConfigAttrs(configId);
            if (FuncUtil.isEmpty(attrs)) {
                setProgress(new PortalUploadProgressRes(UploadProgressStep.FAILED, 0, 0,
                        new ArrayList<>(Collections.singletonList("宽表字段配置为空"))));
                return;
            }

            // 阶段1: 清除旧数据
            setProgress(new PortalUploadProgressRes(UploadProgressStep.UPLOAD, 3, 0, new ArrayList<>()));

            // 清除同步日志
            LambdaQueryWrapper<FormWideTableSyncLog> delLogWrapper = syncLogService.getQueryWrapper();
            delLogWrapper.eq(FormWideTableSyncLog::getConfigId, configId);
            syncLogService.delete(delLogWrapper);

            // 重建物理表
            if (FuncUtil.isNotEmpty(config.getTableName())) {
                formWideTableManager.dropPhysicalTable(config.getTableName());
                String ddl = formWideTableManager.generateDDL(config.getTableName(), config.getTitle(), attrs);
                formWideTableManager.createPhysicalTable(ddl);
            }
            addProgress(1);

            // 阶段2: 获取待收集记录
            setProgressStep(UploadProgressStep.VALIDATE);
            List<WideTableBusinessContext> contexts = Collections.emptyList();
            if (contextProvider != null) {
                contexts = contextProvider.getSubmittedHistories(config.getFormId(), new ArrayList<>());
            }
            if (FuncUtil.isEmpty(contexts)) {
                // 没有数据，直接成功
                setProgress(new PortalUploadProgressRes(UploadProgressStep.SUCCESS, 0, 0, new ArrayList<>()));
                return;
            }

            // 阶段3: 逐条收集
            int total = contexts.size();
            setProgress(new PortalUploadProgressRes(UploadProgressStep.SAVE, total, 0, new ArrayList<>()));

            List<String> errors = new ArrayList<>();
            int successCount = 0;
            for (int i = 0; i < contexts.size(); i++) {
                WideTableBusinessContext context = contexts.get(i);
                try {
                    collectOne(config, attrs, context);
                    saveSyncLog(configId, context.getHistoryId(), "success", null);
                    successCount++;
                } catch (Exception e) {
                    log.error("收集记录失败: historyId={}", context.getHistoryId(), e);
                    saveSyncLog(configId, context.getHistoryId(), "fail", e.getMessage());
                    errors.add("记录 " + context.getHistoryId() + " 收集失败: " + e.getMessage());
                }
                // 更新进度
                PortalUploadProgressRes progress = getProgress();
                progress.setLoaded(i + 1);
                progress.setComments(errors);
                setProgress(progress);
            }

            // 完成
            PortalUploadProgressRes finalProgress = getProgress();
            if (errors.isEmpty()) {
                finalProgress.setStep(UploadProgressStep.SUCCESS);
            } else {
                finalProgress.setStep(UploadProgressStep.FAILED);
            }
            finalProgress.setLoaded(total);
            setProgress(finalProgress);

            log.info("宽表异步重新收集完成: configId={}, 总数={}, 成功={}", configId, total, successCount);

        } catch (Exception e) {
            log.error("宽表异步收集异常: configId={}", configId, e);
            setProgress(new PortalUploadProgressRes(UploadProgressStep.FAILED, 0, 0,
                    new ArrayList<>(Collections.singletonList("收集异常: " + e.getMessage()))));
        }
    }

    private void addProgress(int loaded) {
        PortalUploadProgressRes progress = getProgress();
        progress.setLoaded(loaded);
        setProgress(progress);
    }

    private void setProgressStep(UploadProgressStep step) {
        PortalUploadProgressRes progress = getProgress();
        progress.setStep(step);
        progress.setLoaded(0);
        setProgress(progress);
    }

    /**
     * 收集单条记录
     */
    private void collectOne(FormWideTableConfig config, List<FormWideTableConfigAttr> attrs, WideTableBusinessContext context) {
        Set<Long> attrIds = attrs.stream()
                .map(FormWideTableConfigAttr::getAttributeId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<FormData> dataWrapper = formDataService.getQueryWrapper();
        dataWrapper.eq(FormData::getHistoryId, context.getHistoryId())
                .in(FormData::getAttributeId, attrIds)
                .eq(FormData::getValid, "1");
        List<FormData> dataList = formDataService.select(dataWrapper);

        Map<Long, String> valueMap = new HashMap<>();
        for (FormData data : dataList) {
            valueMap.put(data.getAttributeId(), data.getValue());
        }

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        columns.append("history_id");
        placeholders.append(":historyId");
        params.put("historyId", context.getHistoryId());

        // 业务固定列
        List<WideTableFixedColumn> fixedColumns = configProvider != null ? configProvider.getFixedColumns() : Collections.emptyList();
        for (WideTableFixedColumn fc : fixedColumns) {
            String colName = fc.getColumnName();
            Object value = context.getContextValue(fc.getContextKey());
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

        String deleteSql = "DELETE FROM `" + config.getTableName() + "` WHERE history_id = :historyId";
        jdbcConnectService.executeUpdate(deleteSql, params);

        String insertSql = "INSERT INTO `" + config.getTableName() + "` (" + columns + ") VALUES (" + placeholders + ")";
        jdbcConnectService.executeUpdate(insertSql, params);
    }

    private void saveSyncLog(Long configId, String historyId, String status, String errorMsg) {
        LambdaQueryWrapper<FormWideTableSyncLog> delWrapper = syncLogService.getQueryWrapper();
        delWrapper.eq(FormWideTableSyncLog::getConfigId, configId)
                .eq(FormWideTableSyncLog::getHistoryId, historyId);
        syncLogService.delete(delWrapper);

        FormWideTableSyncLog syncLog = new FormWideTableSyncLog();
        syncLog.setConfigId(configId);
        syncLog.setHistoryId(historyId);
        syncLog.setStatus(status);
        syncLog.setErrorMsg(errorMsg);
        syncLog.setSyncedAt(new Date());
        syncLogService.insert(syncLog);
    }
}
