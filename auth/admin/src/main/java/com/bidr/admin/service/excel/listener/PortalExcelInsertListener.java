package com.bidr.admin.service.excel.listener;

import com.bidr.admin.service.excel.handler.PortalExcelInsertHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelParseHandlerInf;
import com.bidr.admin.service.excel.progress.PortalExcelUploadProgressInf;
import com.bidr.admin.vo.PortalWithColumnsRes;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
public class PortalExcelInsertListener<EXCEL> extends BasePortalExcelListener<EXCEL> {
    private final PortalExcelInsertHandlerInf portalExcelHandlerInf;

    public PortalExcelInsertListener(PortalWithColumnsRes portal, PortalExcelUploadProgressInf uploadProgress,
                                     PortalExcelParseHandlerInf portalExcelParseHandlerInf,
                                     PortalExcelInsertHandlerInf portalExcelInsertHandlerInf) {
        super(portal, portalExcelParseHandlerInf, uploadProgress);
        this.portalExcelHandlerInf = portalExcelInsertHandlerInf;
    }

    public PortalExcelInsertListener(PortalWithColumnsRes portal, PortalExcelUploadProgressInf uploadProgress,
                                     PortalExcelParseHandlerInf portalExcelParseHandlerInf,
                                     PortalExcelInsertHandlerInf portalExcelInsertHandlerInf, Integer recordBatchSize) {
        super(portal, portalExcelParseHandlerInf, uploadProgress, recordBatchSize);
        this.portalExcelHandlerInf = portalExcelInsertHandlerInf;
    }

    @Override
    protected Object parse(PortalWithColumnsRes portal, EXCEL data, Map<String, EXCEL> entityCache) {
        return portalExcelParseHandlerInf.parseEntity(portal, data, entityCache);
    }

    @Override
    protected void prepare(Object entity) {
        portalExcelHandlerInf.prepareInsert(entity);
    }

    @Override
    protected boolean validate(Object entity, List<Object> cachedList, Map<Object, Object> validateMap) {
        return portalExcelHandlerInf.validateInsert(entity, cachedList, validateMap);
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

