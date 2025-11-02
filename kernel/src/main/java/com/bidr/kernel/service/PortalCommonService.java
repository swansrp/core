package com.bidr.kernel.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.controller.inf.base.AdminBaseInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.exception.NoImplementsException;
import com.bidr.kernel.mybatis.bo.DynamicColumn;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.mybatis.repository.inf.PortalSelectRepo;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: PortalCommonService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/15 16:17
 */
@SuppressWarnings("unchecked")
public interface PortalCommonService<ENTITY, VO> extends AdminBaseInf<ENTITY, VO>, AdminBaseQueryControllerInf<ENTITY, VO>, PortalSelectRepo<ENTITY> {

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
     * 基础查询处理
     *
     * @param req 查询条件
     */
    @Override
    default void defaultQuery(Query req) {

    }

    /**
     * 查询前处理
     *
     * @param req 查询条件
     */
    @Override
    default void beforeQuery(QueryConditionReq req) {

    }

    /**
     * 查询前处理
     *
     * @param req 查询条件
     */
    @Override
    default void beforeQuery(AdvancedQueryReq req) {

    }

    /**
     * 获取全部数据 生成树
     *
     * @return 全部数据
     */
    default List<ENTITY> getAllData() {
        throw new NoImplementsException("没有配置获取全部数据生成树形结构的方法");
    }

    /**
     * Join Wrapper 查询是否需要对结果进行group
     *
     * @return 需要group的Columns
     */
    default List<String> groupColumns() {
        return null;
    }

    /**
     * 通用获取group列的方法
     *
     * @return
     */
    default List<String> defaultGroupColumns() {
        List<String> entityFieldList = selectColumns();
        if (FuncUtil.isNotEmpty(getAliasMap()) && FuncUtil.isNotEmpty(getAliasMap().values())) {
            entityFieldList.addAll(getAliasMap().values());
        }
        return entityFieldList;
    }

    /**
     * 配置select字段
     * null 代表使用全体字段
     *
     * @return 字段列表
     */
    default List<String> selectColumns() {
        return getRepo().getFieldSql("t");
    }

    /**
     * 生成联表查询别名
     *
     * @return 别名map
     */
    default Map<String, String> getAliasMap() {
        return null;
    }

    /**
     * 生成汇总别名表
     *
     * @return 别名map
     */
    default Map<String, String> getSummaryAliasMap() {
        return null;
    }

    /**
     * 导出文件流
     *
     * @param dataList   数据集
     * @param portalName 配置名称
     * @return 导出字节流
     */
    default byte[] export(List<VO> dataList, String portalName) {
        return null;
    }

    /**
     * 导出数据模版
     *
     * @param portalName 配置名称
     * @return 导出字节流
     */
    default byte[] templateExport(String portalName) {
        return null;
    }

    /**
     * 批量添加
     * 大量数据时可以考虑使用mysql.insertBatch
     * getRepo().getMapper().insertBatchSomeColumn(dataList);
     *
     * @param dataList 数据列表
     */
    default void batchInsert(List<ENTITY> dataList) {
        getRepo().saveBatch(dataList);
    }

    /**
     * 批量修改
     *
     * @param dataList 数据列表
     */
    default void batchUpdate(List<ENTITY> dataList) {
        getRepo().updateById(dataList);
    }

    /**
     * 读取excel 完成数据有效性判断
     *
     * @param is     excel文件
     * @param portal 配置
     */
    void readExcelForInsert(InputStream is, String portal);

    /**
     * 读取excel 完成数据有效性判断
     *
     * @param is     excel文件
     * @param portal 配置
     */
    void readExcelForUpdate(InputStream is, String portal);

    /**
     * 判断当前是否有Excel读取任务
     */
    void validateReadExcel();

    /**
     * 获取上传处理进度
     *
     * @param portal 配置
     * @return 当前进度
     */
    Object getUploadProgressRes(String portal);

    /**
     * 获取分页数据
     *
     * @param req 条件/排序/分页
     * @return 数据
     */
    default Page<VO> query(AdvancedQueryReq req) {
        Query query = new Query(req);
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getAliasMap(), getSelectApplyMap(), getJoinWrapper());
        return getRepo().select(query, req.getCurrentPage(), req.getPageSize(), selectAliasMap, getHavingFields(), getJoinWrapper(), getVoClass());
    }

    /**
     * 生成需要HAVING查询的字段
     *
     * @return having字段
     */
    default Set<String> getHavingFields() {
        return null;
    }

    /**
     * 生成需要select条件的字段map
     *
     * @return select apply 字段
     */
    default Map<String, List<DynamicColumn>> getSelectApplyMap() {
        return null;
    }

    /**
     * 生成联表查询wrapper
     *
     * @return 联表wrapper
     */
    default MPJLambdaWrapper<ENTITY> getJoinWrapper() {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        getJoinWrapper(wrapper);
        return wrapper;
    }

    @Override
    default Class<VO> getVoClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    /**
     * 提供 wrapper处理函数
     *
     * @param wrapper 联表wrapper
     */
    default void getJoinWrapper(MPJLambdaWrapper<ENTITY> wrapper) {
    }

    /**
     * 是否查看全局数据
     *
     * @return 判断是否能够查看全局数据
     */
    @Override
    default boolean isAdmin() {
        try {
            return (Boolean) ReflectionUtil.invoke(BeanUtil.getBean("permitService"), "isAdmin");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 数据库repo
     *
     * @return repo
     */
    @Override
    BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo();

    /**
     * 获取entity类型
     *
     * @return entity类型
     */
    @Override
    default Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    /**
     * 根据id获取数据
     *
     * @param id id
     * @return 数据
     */
    default VO selectById(String id) {
        return getRepo().selectById(id, getJoinWrapper(), getVoClass());
    }

    /**
     * 获取指定数据
     *
     * @param condition 条件
     * @return 数据
     */
    default List<VO> select(AdvancedQuery condition) {
        Query query = new Query(new AdvancedQueryReq(condition));
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getAliasMap(), getSelectApplyMap(), getJoinWrapper());
        return getRepo().select(query, selectAliasMap, getHavingFields(), getJoinWrapper(), getVoClass());
    }

    /**
     * 获取指定数据
     *
     * @param condition 条件
     * @param sortList  排序
     * @return 数据
     */
    default List<VO> query(AdvancedQuery condition, List<SortVO> sortList) {
        Query query = new Query(new AdvancedQueryReq(condition, sortList));
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getAliasMap(), getSelectApplyMap(), getJoinWrapper());
        return getRepo().select(query, selectAliasMap, getHavingFields(), getJoinWrapper(), getVoClass());
    }

    /**
     * 获取指定数据
     *
     * @param condition             条件
     * @param sortList              排序
     * @param selectColumnCondition 动态字段
     * @return 数据
     */
    default List<VO> query(AdvancedQuery condition, List<SortVO> sortList, Map<String, Object> selectColumnCondition) {
        Query query = new Query(new AdvancedQueryReq(condition, sortList, selectColumnCondition));
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getAliasMap(), getSelectApplyMap(), getJoinWrapper());
        return getRepo().select(query, selectAliasMap, getHavingFields(), getJoinWrapper(), getVoClass());
    }

}
