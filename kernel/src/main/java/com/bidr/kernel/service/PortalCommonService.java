package com.bidr.kernel.service;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: PortalCommonService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/15 16:17
 */
public interface PortalCommonService<ENTITY, VO> {
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
     * 管理员-添加前处理
     *
     * @param entity 添加参数
     */
    default void adminBeforeAdd(ENTITY entity) {
        beforeAdd(entity);
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
     * 管理员-编辑前处理
     *
     * @param entity 编辑参数
     */
    default void adminBeforeUpdate(ENTITY entity) {
        beforeUpdate(entity);
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
     * 管理员-删除前处理
     *
     * @param vo 删除参数
     */
    default void adminBeforeDelete(IdReqVO vo) {
        beforeDelete(vo);
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
     * 查询前处理
     *
     * @param req 查询条件
     */
    default void beforeQuery(AdvancedQueryReq req) {

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

    /**
     * 生成联表查询wrapper
     *
     * @return
     */
    default MPJLambdaWrapper<ENTITY> getJoinWrapper() {
        return new MPJLambdaWrapper<>();
    }

    /**
     * 生成联表查询别名
     *
     * @return
     */
    default Map<String, String> getAliasMap() {
        return null;
    }

    /**
     * 导出文件流
     *
     * @param dataList 数据集
     * @return
     * @throws IOException
     */
    default byte[] export(List<VO> dataList) throws IOException {
        return null;
    }

}
