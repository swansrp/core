package com.bidr.admin.service.excel.listener;

import com.bidr.admin.service.excel.handler.PortalExcelInsertHandlerInf;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.admin.vo.PortalWithColumnsRes;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.util.List;
import java.util.Map;

/**
 * Title: PortalExcelUpdateListener
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/15 00:18
 */
@Slf4j
@Getter
@SuppressWarnings("rawtypes, unchecked")
public class PortalExcelInsertListener extends BasePortalExcelListener {
    private final PortalExcelInsertHandlerInf portalExcelHandlerInf;

    public PortalExcelInsertListener(PortalWithColumnsRes portal, PortalExcelUploadProgressInf uploadProgress,
                                     PortalExcelInsertHandlerInf portalExcelInsertHandlerInf,
                                     DataSourceTransactionManager dataSourceTransactionManager,
                                     TransactionDefinition transactionDefinition) {
        super(portal, uploadProgress, dataSourceTransactionManager, transactionDefinition);
        this.portalExcelHandlerInf = portalExcelInsertHandlerInf;
    }

    @Override
    protected Object parse(PortalWithColumnsRes portal, Map<Integer, String> data,
                           Map<String, Map<String, Object>> entityCache) {
        return portalExcelHandlerInf.parseEntity(portal, data, entityCache);
    }

    @Override
    protected void prepare(Object entity) {
        portalExcelHandlerInf.prepareInsert(entity);
    }

    @Override
    protected void validate(Object entity, List<Object> cachedList, Map<Object, Object> validateMap) {
        portalExcelHandlerInf.validateInsert(entity, cachedList, validateMap);
    }

    @Override
    protected void handle(List<Object> cachedList) {
        portalExcelHandlerInf.handleInsert(cachedList);
    }

    @Override
    protected void afterHandle(List<Object> cachedList) {
        portalExcelHandlerInf.afterInsert(cachedList);
    }
}

