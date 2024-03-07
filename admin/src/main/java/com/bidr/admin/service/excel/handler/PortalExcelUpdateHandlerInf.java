package com.bidr.admin.service.excel.handler;

import java.util.List;
import java.util.Map;

/**
 * Title: PortalExcelHandlerInf
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/15 22:56
 */
public interface PortalExcelUpdateHandlerInf<ENTITY> extends PortalExcelHandlerInf {

    /**
     * 预处理修改数据
     *
     * @param entity 数据
     */
    void prepareUpdate(ENTITY entity);

    /**
     * 检查数据是否有效
     *
     * @param entity      当前行数据
     * @param cachedList  历史数据
     * @param validateMap 校验map
     */
    void validateUpdate(ENTITY entity, List<ENTITY> cachedList, Map<Object, Object> validateMap);

    /**
     * 处理数据列表
     *
     * @param entityList 数据列表
     */
    void handleUpdate(List<ENTITY> entityList);

    /**
     * 落库后处理
     *
     * @param entityList 数据列表
     */
    void afterUpdate(List<ENTITY> entityList);
}
