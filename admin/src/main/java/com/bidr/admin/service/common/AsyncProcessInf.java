package com.bidr.admin.service.common;

import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AsyncProcessInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/10 19:25
 */

public interface AsyncProcessInf<T> extends PortalExcelUploadProgressInf {

    /**
     * 异步处理
     *
     * @param items 处理数据列表
     */
    @Async
    default void handle(List<T> items) {
        try {
            if (FuncUtil.isNotEmpty(items)) {
                startUploadProgress(items.size());
                startValidateRecord(items.size());
                List<T> entityList = new ArrayList<>();
                int i = 1;
                for (T item : items) {
                    if (validate(item)) {
                        entityList.add(item);
                        addUploadProgress(i++);
                    }
                }
                TransactionStatus status = null;
                try {
                    startSaveRecord();
                    i = 1;
                    for (T item : entityList) {
                        status = getTransactionStatus();
                        handle(item);
                        getTransactionManager().commit(status);
                        addUploadProgress(i++);
                    }
                    uploadProgressFinish();
                } catch (Exception e) {
                    if (status != null) {
                        getTransactionManager().rollback(status);
                    }
                    uploadProgressException(e.getMessage());
                }
            } else {
                uploadProgressFinish();
            }
        } catch (Exception e) {
            uploadProgressException(e.getMessage());
        }
    }

    /**
     * 校验数据
     *
     * @param item
     * @return 是否可以进行处理
     */
    default boolean validate(T item) {
        return true;
    }

    /**
     * 获取TransactionStatus
     *
     * @return 获取TransactionStatus
     */
    default TransactionStatus getTransactionStatus() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return getTransactionManager().getTransaction(def);
    }

    /**
     * 数据处理
     *
     * @param item
     */
    void handle(T item);

    /**
     * 获取PlatformTransactionManager
     *
     * @return PlatformTransactionManager
     */
    default PlatformTransactionManager getTransactionManager() {
        return BeanUtil.getBean(PlatformTransactionManager.class);
    }

    /**
     * 获取token service
     *
     * @return Convert to Basic Latin
     */
    @Override
    default TokenService getTokenService() {
        return BeanUtil.getBean(TokenService.class);
    }

    /**
     * 配置上传key
     *
     * @return 上传key
     */
    @Override
    default String getProgressKey() {
        return "UPLOAD_PROGRESS_" + this.getClass().getSimpleName();
    }
}
