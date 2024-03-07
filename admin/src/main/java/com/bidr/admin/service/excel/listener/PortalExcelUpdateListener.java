package com.bidr.admin.service.excel.listener;

import com.bidr.admin.service.excel.handler.PortalExcelParseHandlerInf;
import com.bidr.admin.service.excel.handler.PortalExcelUpdateHandlerInf;
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
public class PortalExcelUpdateListener<EXCEL> extends BasePortalExcelListener<EXCEL> {
    private final PortalExcelUpdateHandlerInf portalExcelHandlerInf;

    public PortalExcelUpdateListener(PortalWithColumnsRes portal, PortalExcelUploadProgressInf uploadProgress,
                                     PortalExcelParseHandlerInf portalExcelParseHandlerInf,
                                     PortalExcelUpdateHandlerInf portalExcelInsertHandlerInf) {
        super(portal, portalExcelParseHandlerInf, uploadProgress);
        this.portalExcelHandlerInf = portalExcelInsertHandlerInf;
    }

    @Override
    protected Object parse(PortalWithColumnsRes portal, EXCEL data, Map<String, EXCEL> entityCache) {
        return portalExcelParseHandlerInf.parseEntity(portal, data, entityCache);
    }

    @Override
    protected void prepare(Object entity) {
        portalExcelHandlerInf.prepareUpdate(entity);
    }

    @Override
    protected void validate(Object entity, List<Object> cachedList, Map<Object, Object> validateMap) {
        portalExcelHandlerInf.validateUpdate(entity, cachedList, validateMap);
    }

    @Override
    protected void handle(List<Object> cachedList) {
        portalExcelHandlerInf.handleUpdate(cachedList);
    }

    @Override
    protected void afterHandle(List<Object> cachedList) {
        portalExcelHandlerInf.afterUpdate(cachedList);
    }
}

