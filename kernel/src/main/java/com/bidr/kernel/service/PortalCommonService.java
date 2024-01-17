package com.bidr.kernel.service;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: PortalCommonService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/15 16:17
 */
public interface PortalCommonService<ENTITY> {
    /**
     * 是否查看全局数据
     *
     * @return 判断是否能够查看全局数据
     */
    default boolean isAdmin() {
        try {
            return (Boolean) ReflectionUtil.invoke(BeanUtil.getBean("permitService"), "isAdmin");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 添加前处理
     *
     * @param entity 添加参数
     */
    default void beforeAdd(ENTITY entity) {

    }

    /**
     * 添加后处理
     *
     * @param entity 添加参数
     */
    default void afterAdd(ENTITY entity) {

    }

    /**
     * 编辑前处理
     *
     * @param entity 编辑参数
     */
    default void beforeUpdate(ENTITY entity) {

    }

    /**
     * 编辑后处理
     *
     * @param entity 编辑参数
     */
    default void afterUpdate(ENTITY entity) {

    }

    /**
     * 删除前处理
     *
     * @param vo 删除参数
     */
    default void beforeDelete(IdReqVO vo) {

    }

    /**
     * 删除后处理
     *
     * @param vo 删除参数
     */
    default void afterDelete(IdReqVO vo) {

    }

    /**
     * 查询前处理
     *
     * @param req 查询条件
     */
    default void beforeQuery(QueryConditionReq req) {

    }

    /**
     * 获取全部数据 生成树
     *
     * @return 全部数据
     */
    default List<ENTITY> getAllData() {
        LoggerFactory.getLogger(this.getClass()).debug("没有配置获取全部数据生成树形结构的方法");
        return new ArrayList<>();
    }
}
