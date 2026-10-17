package com.bidr.admin.service.excel.handler;

import com.bidr.admin.vo.PortalWithColumnsRes;

import java.util.Map;

/**
 * Title: PortalExcelParseHandlerInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/07 09:02
 */
public interface PortalExcelParseHandlerInf<ENTITY, EXCEL> {

    /**
     * 当前行数据转换成entity
     *
     * @param portal      配置名称
     * @param data        当前行数据
     * @param entityCache 关联数据缓存
     * @return
     */
    ENTITY parseEntity(PortalWithColumnsRes portal, EXCEL data, Map<String, Map<String, Object>> entityCache);
}
