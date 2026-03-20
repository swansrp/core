package com.bidr.platform.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.holder.ReadSheetHolder;
import com.alibaba.excel.util.ListUtils;
import com.bidr.kernel.mybatis.service.TableSyncService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.platform.constant.upload.UploadProgressStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;
import java.util.Map;

/**
 * Title: ModelDataListener
 * Description: Excel数据解析监听器
 * Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/10 11:41
 */
@Slf4j
public class ModelDataListener<T, VO> extends AnalysisEventListener<VO> {
    protected final EasyExcelHandler<T, VO> handler;
    protected final Map<String, Object> handleContext;
    protected final PlatformTransactionManager transactionManager;
    protected final TransactionStatus transactionStatus;
    protected Integer maxLine;
    protected Integer loaded;
    protected List<T> cachedDataList = ListUtils.newArrayList();

    /**
     * 构造函数（默认不开启事务）
     */
    public ModelDataListener(EasyExcelHandler<T, VO> handler, Map<String, Object> handleContext) {
        this(handler, handleContext, null, false);
    }

    /**
     * 构造函数
     *
     * @param handler            业务处理器
     * @param handleContext      上下文
     * @param transactionManager 事务管理器
     */
    public ModelDataListener(EasyExcelHandler<T, VO> handler, Map<String, Object> handleContext,
                             PlatformTransactionManager transactionManager) {
        this(handler, handleContext, transactionManager, transactionManager != null);
    }

    /**
     * 构造函数
     *
     * @param handler            业务处理器
     * @param handleContext      上下文
     * @param transactionManager 事务管理器
     * @param enableTransaction  是否开启事务（需 transactionManager 不为空）
     */
    public ModelDataListener(EasyExcelHandler<T, VO> handler, Map<String, Object> handleContext,
                             PlatformTransactionManager transactionManager, boolean enableTransaction) {
        this.handler = handler;
        this.handleContext = handleContext;
        this.maxLine = -1;
        this.loaded = 0;
        this.transactionManager = transactionManager;
        if (enableTransaction && FuncUtil.isNotEmpty(transactionManager)) {
            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transactionStatus = transactionManager.getTransaction(def);
        } else {
            transactionStatus = null;
        }
    }

    @Override
    public void invoke(VO data, AnalysisContext context) {
        if (maxLine == -1) {
            ReadSheetHolder sheetHolder = context.readSheetHolder();
            maxLine = sheetHolder.getApproximateTotalRowNumber() - sheetHolder.getHeadRowNumber();
            onPrepare(context);
            handler.prepare(context, handleContext);
            handler.setProgress(UploadProgressStep.VALIDATE, maxLine, null, null);
        }
        T entity = handler.parse(data, handleContext, context);
        log.trace("解析到一条数据:{}", JsonUtil.toJson(entity));
        if (handler.validate(entity, cachedDataList, handleContext)) {
            cachedDataList.add(entity);
        }
        if (handler.save(entity, cachedDataList, handleContext)) {
            saveData();
            cachedDataList = ListUtils.newArrayList();
        }
    }

    /**
     * 准备阶段回调，子类可覆写
     */
    protected void onPrepare(AnalysisContext context) {
        // 默认空实现
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        try {
            saveData();
            if (FuncUtil.isNotEmpty(transactionStatus)) {
                transactionManager.commit(transactionStatus);
            }
        } catch (Exception e) {
            try {
                onException(e, context);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        log.trace("所有数据解析完成！");
        handleContext.clear();
        cachedDataList.clear();
        handler.setProgress(UploadProgressStep.SUCCESS, null, null, null);
        handler.finish(context);
        onFinish(context);
    }

    /**
     * 完成阶段回调，子类可覆写
     */
    protected void onFinish(AnalysisContext context) {
        // 默认空实现
    }

    /**
     * 异常阶段回调，子类可覆写
     */
    protected void onError(Exception exception, AnalysisContext context) {
        // 默认空实现
    }

    /**
     * 加上存储数据库
     */
    protected void saveData() {
        if (FuncUtil.isNotEmpty(cachedDataList)) {
            log.trace("{}条数据，开始存储数据库！", cachedDataList.size());
            handler.save(cachedDataList, handleContext);
            loaded = loaded + cachedDataList.size();
            handler.setProgress(UploadProgressStep.SAVE, maxLine, loaded, null);
            log.trace("存储数据库成功！");
        }
    }

    @Override
    public void onException(Exception exception, AnalysisContext context) throws Exception {
        int row = context.readRowHolder().getRowIndex() + 1;
        handler.setProgress(UploadProgressStep.FAILED, maxLine, loaded, "上传数据第" + (loaded + 1) + "行 ~ " + row + "行数据出错,请检查");
        handler.setProgress(UploadProgressStep.FAILED, maxLine, loaded, exception.getMessage());
        onError(exception, context);
        if (FuncUtil.isNotEmpty(transactionStatus)) {
            transactionManager.rollback(transactionStatus);
        }
        super.onException(exception, context);
    }

    /**
     * 创建支持影子表切换的 Listener（默认不开启事务）
     *
     * @param handler          业务处理器
     * @param handleContext    上下文
     * @param tableSyncService 表同步服务
     * @param syncTableNames   需要同步的表名列表
     * @return ShadowTableModelDataListener
     */
    public static <T, VO> ShadowTableModelDataListener<T, VO> withShadowTable(
            EasyExcelHandler<T, VO> handler,
            Map<String, Object> handleContext,
            TableSyncService tableSyncService,
            List<String> syncTableNames) {
        return new ShadowTableModelDataListener<>(handler, handleContext, tableSyncService, syncTableNames);
    }

    /**
     * 创建支持影子表切换的 Listener
     *
     * @param handler            业务处理器
     * @param handleContext      上下文
     * @param transactionManager 事务管理器
     * @param enableTransaction  是否开启事务
     * @param tableSyncService   表同步服务
     * @param syncTableNames     需要同步的表名列表
     * @return ShadowTableModelDataListener
     */
    public static <T, VO> ShadowTableModelDataListener<T, VO> withShadowTable(
            EasyExcelHandler<T, VO> handler,
            Map<String, Object> handleContext,
            PlatformTransactionManager transactionManager,
            boolean enableTransaction,
            TableSyncService tableSyncService,
            List<String> syncTableNames) {
        return new ShadowTableModelDataListener<>(handler, handleContext, transactionManager, enableTransaction, tableSyncService, syncTableNames);
    }
}
