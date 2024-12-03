package com.bidr.platform.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.read.metadata.holder.ReadSheetHolder;
import com.alibaba.excel.util.ListUtils;
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
 * Title: NoModelDataListener
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/10 11:41
 */
@Slf4j
public class ModelDataListener<T, VO> extends AnalysisEventListener<VO> {
    private final EasyExcelHandler<T, VO> handler;
    private final Map<String, Object> handleContext;
    private final PlatformTransactionManager transactionManager;
    private final TransactionStatus transactionStatus;
    private Integer maxLine;
    private Integer loaded;
    private List<T> cachedDataList = ListUtils.newArrayList();

    public ModelDataListener(EasyExcelHandler<T, VO> handler, Map<String, Object> handleContext,
                             PlatformTransactionManager transactionManager) {
        this.handler = handler;
        this.handleContext = handleContext;
        this.maxLine = -1;
        this.loaded = 0;
        this.transactionManager = transactionManager;
        if (FuncUtil.isNotEmpty(transactionManager)) {
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
        // 完全的隐藏逻辑 通过4个参数的null的情况 一个函数实现各种情形的进度控制
        handler.setProgress(UploadProgressStep.SUCCESS, null, null, null);
    }

    /**
     * 加上存储数据库
     */
    private void saveData() {
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
        handler.setProgress(UploadProgressStep.FAILED, maxLine, loaded, "上传数据第" + row + "行数据出错,请检查");
        handler.setProgress(UploadProgressStep.FAILED, maxLine, loaded, exception.getMessage());
        if (FuncUtil.isNotEmpty(transactionStatus)) {
            transactionManager.rollback(transactionStatus);
        }
        super.onException(exception, context);
    }
}
