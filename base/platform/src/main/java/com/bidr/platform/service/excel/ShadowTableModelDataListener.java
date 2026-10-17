package com.bidr.platform.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.bidr.kernel.config.db.DynamicTableNameHolder;
import com.bidr.kernel.mybatis.service.TableSyncService;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 影子表数据监听器
 * <p>
 * 在解析前后自动处理影子表的准备和切换
 *
 * @author Sharp
 * @since 2026/3/1 10:29
 */
@Slf4j
public class ShadowTableModelDataListener<T, VO> extends ModelDataListener<T, VO> {
    private final TableSyncService tableSyncService;
    private final List<String> syncTableNames;

    /**
     * 构造函数（默认不开启事务）
     */
    public ShadowTableModelDataListener(EasyExcelHandler<T, VO> handler,
                                        Map<String, Object> handleContext,
                                        TableSyncService tableSyncService,
                                        List<String> syncTableNames) {
        this(handler, handleContext, null, false, tableSyncService, syncTableNames);
    }

    /**
     * 构造函数（兼容旧接口，transactionManager 不为空时自动开启事务）
     */
    public ShadowTableModelDataListener(EasyExcelHandler<T, VO> handler,
                                        Map<String, Object> handleContext,
                                        org.springframework.transaction.PlatformTransactionManager transactionManager,
                                        TableSyncService tableSyncService,
                                        List<String> syncTableNames) {
        this(handler, handleContext, transactionManager, transactionManager != null, tableSyncService, syncTableNames);
    }

    /**
     * 构造函数
     *
     * @param handler            业务处理器
     * @param handleContext      上下文
     * @param transactionManager 事务管理器
     * @param enableTransaction  是否开启事务（需 transactionManager 不为空）
     * @param tableSyncService   表同步服务
     * @param syncTableNames     需要同步的表名列表
     */
    public ShadowTableModelDataListener(EasyExcelHandler<T, VO> handler,
                                        Map<String, Object> handleContext,
                                        org.springframework.transaction.PlatformTransactionManager transactionManager,
                                        boolean enableTransaction,
                                        TableSyncService tableSyncService,
                                        List<String> syncTableNames) {
        super(handler, handleContext, transactionManager, enableTransaction);
        this.tableSyncService = tableSyncService;
        this.syncTableNames = syncTableNames != null ? syncTableNames : Collections.emptyList();
    }

    @Override
    protected void onPrepare(AnalysisContext context) {
        prepareShadowTables();
    }

    @Override
    protected void onFinish(AnalysisContext context) {
        finishShadowTables();
    }

    @Override
    protected void onError(Exception exception, AnalysisContext context) {
        clearShadowTables();
    }

    private void prepareShadowTables() {
        for (String tableName : syncTableNames) {
            String shadowTableName = tableSyncService.generateShadowTableName(tableName);
            tableSyncService.prepare(tableName, shadowTableName);
            DynamicTableNameHolder.set(tableName, shadowTableName);
        }
    }

    private void finishShadowTables() {
        clearShadowTables();
        for (String tableName : syncTableNames) {
            tableSyncService.atomicSwitch(tableName);
        }
    }

    private void clearShadowTables() {
        for (String tableName : syncTableNames) {
            DynamicTableNameHolder.clear(tableName);
        }
    }
}
