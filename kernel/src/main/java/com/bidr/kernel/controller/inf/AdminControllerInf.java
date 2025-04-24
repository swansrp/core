package com.bidr.kernel.controller.inf;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.constant.dict.portal.PortalSortDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.portal.*;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Title: BaseAdminController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 11:42
 */
@SuppressWarnings("rawtypes, unchecked")
public interface AdminControllerInf<ENTITY, VO> {
    /**
     * 添加数据
     *
     * @param vo 数据
     */
    void add(@RequestBody VO vo);

    /**
     * 删除数据
     *
     * @param vo 数据
     */

    void delete(@RequestBody IdReqVO vo);

    /**
     * 删除数据列表
     *
     * @param idList 数据
     */

    void deleteList(@RequestBody List<String> idList);

    /**
     * 修改数据
     *
     * @param vo     数据
     * @param strict 是否更新null
     */

    void update(@RequestBody VO vo, @RequestParam(required = false) boolean strict);

    /**
     * 修改数据列表
     *
     * @param voList 数据列表
     * @param strict 是否更新null
     */
    void update(@RequestBody List<VO> voList, @RequestParam(required = false) boolean strict);

    /**
     * 根据id查询
     *
     * @param req id
     * @return 数据
     */
    VO queryById(IdReqVO req);

    /**
     * 普通查询
     *
     * @param req 查询条件
     * @return 数据
     */
    Page<VO> generalQuery(@RequestBody QueryConditionReq req);

    /**
     * 普通查询(不分页)
     *
     * @param req 查询条件
     * @return 数据
     */
    List<VO> generalSelect(@RequestBody QueryConditionReq req);

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 汇总数据
     */
    Map<String, Object> generalSummary(@RequestBody GeneralSummaryReq req);

    /**
     * 汇总
     *
     * @param req 查询条件
     * @return 汇总数据
     */
    default Map<String, Object> summaryByGeneralReq(GeneralSummaryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(req.getColumns())) {
            for (String column : req.getColumns()) {
                wrapper.getSelectColum()
                        .add(new SelectString(String.format("sum(%s) as %s", column, column), wrapper.getAlias()));
            }
        }
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
                Map<String, String> aliasMap = getRepo().parseSelectApply(req.getConditionList(),
                        getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                if (FuncUtil.isNotEmpty(req.getConditionList())) {
                    getRepo().parseGeneralQuery(req.getConditionList(), aliasMap, getPortalService().getHavingFields(),
                            from);
                } else {
                    getRepo().parseGeneralQuery(req.getConditionList(), null, null, from);
                }
            }
            return from;
        });
        return getRepo().selectJoinMap(wrapper);
    }

    /**
     * 构造汇总sql select
     *
     * @param summaryColumns  汇总字段
     * @param summaryAliasMap 汇总字段别名
     * @param wrapper         wrapper
     */
    default void buildSummaryWrapper(List<String> summaryColumns, Map<String, String> summaryAliasMap,
                                     MPJLambdaWrapper<ENTITY> wrapper) {
        if (FuncUtil.isNotEmpty(summaryColumns)) {
            for (String column : summaryColumns) {
                String columnName = getRepo().getColumnName(column, summaryAliasMap, getEntityClass());
                wrapper.getSelectColum()
                        .add(new SelectString(String.format("sum(%s) as %s", columnName, column), wrapper.getAlias()));
            }
        }
        // 去除group by成分
        wrapper.getExpression().getGroupBy().clear();
        // 去除order by成分
        wrapper.getExpression().getOrderBy().clear();
    }

    /**
     * 统计个数
     *
     * @param req 查询条件
     * @return 统计个数数据
     */

    Long generalCount(@RequestBody QueryConditionReq req);

    /**
     * 指标统计
     *
     * @param req 查询条件
     * @return 指标统计数据
     */
    List<StatisticRes> generalStatistic(@RequestBody GeneralStatisticReq req);

    /**
     * 统计个数
     *
     * @param req 查询条件
     * @return 统计个数数据
     */
    default Long countByGeneralReq(QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.getSelectColum().add(new SelectString("count(1)", wrapper.getAlias()));
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
                if (FuncUtil.isNotEmpty(req.getConditionList())) {
                    Map<String, String> aliasMap = getRepo().parseSelectApply(req.getConditionList(),
                            getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                    getRepo().parseGeneralQuery(req.getConditionList(), aliasMap, getPortalService().getHavingFields(),
                            from);
                } else {
                    getRepo().parseGeneralQuery(req.getConditionList(), null, null, from);
                }
            }
            return from;
        });
        return getRepo().selectJoinCount(wrapper);
    }

    default MPJLambdaWrapper<ENTITY> buildStatisticWrapper(Integer sort, List<String> groupByColumn,
                                                           String statisticColumn) {
        String concatJoinStr = ", ',', ";
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(statisticColumn)) {
            wrapper.getSelectColum().add(new SelectString(String.format("sum(%s) as %s", statisticColumn,
                    LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)), wrapper.getAlias()));
        } else {
            wrapper.getSelectColum().add(new SelectString(
                    String.format("count(1) as %s", LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic)),
                    wrapper.getAlias()));
        }
        Validator.assertNotEmpty(groupByColumn, ErrCodeSys.PA_DATA_NOT_EXIST, "分类指标");
        StringBuilder sql = new StringBuilder("CONCAT(");

        for (String column : groupByColumn) {
            if (FuncUtil.isNotEmpty(column)) {
                sql.append("IFNULL(").append(column).append(", 'NULL')").append(concatJoinStr);
            }
        }
        String s = sql.substring(0, sql.length() - concatJoinStr.length()) + ")";
        wrapper.getSelectColum().add(new SelectString(
                String.format("%s as %s", s, LambdaUtil.getFieldNameByGetFunc(StatisticRes::getMetric)),
                wrapper.getAlias()));
        for (String column : groupByColumn) {
            if (FuncUtil.isNotEmpty(column)) {
                wrapper.groupBy(column);
            }
        }
        switch (PortalSortDict.of(sort)) {
            case ASC:
                wrapper.orderByAsc(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                break;
            case DESC:
                wrapper.orderByDesc(LambdaUtil.getFieldNameByGetFunc(StatisticRes::getStatistic));
                break;
            default:
                for (String column : groupByColumn) {
                    if (FuncUtil.isNotEmpty(column)) {
                        wrapper.orderByAsc(column);
                    }
                }
                break;
        }
        return wrapper;
    }

    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByGeneralReq(GeneralStatisticReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getSort(), req.getGroupByColumn(),
                req.getStatisticColumn());
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
                if (FuncUtil.isNotEmpty(req.getConditionList())) {
                    Map<String, String> aliasMap = getRepo().parseSelectApply(req.getConditionList(),
                            getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                    getRepo().parseGeneralQuery(req.getConditionList(), aliasMap, getPortalService().getHavingFields(),
                            from);
                } else {
                    getRepo().parseGeneralQuery(req.getConditionList(), null, null, from);
                }
            }
            return from;
        });
        return getRepo().selectJoinList(StatisticRes.class, wrapper);
    }

    /**
     * 高级查询
     *
     * @param req 高级查询条件
     * @return 数据
     */
    Page<VO> advancedQuery(@RequestBody AdvancedQueryReq req);

    /**
     * 高级查询(不分页)
     *
     * @param req 高级查询条件
     * @return 数据
     */
    List<VO> advancedSelect(@RequestBody AdvancedQueryReq req);

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 数据
     */
    Map<String, Object> advancedSummary(@RequestBody AdvancedSummaryReq req);

    /**
     * 汇总
     *
     * @param req 高级查询条件
     * @return 汇总数据
     */
    default Map<String, Object> summaryByAdvancedReq(AdvancedSummaryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(req.getColumns())) {
            for (String column : req.getColumns()) {
                wrapper.getSelectColum()
                        .add(new SelectString(String.format("sum(%s) as %s", column, column), wrapper.getAlias()));
            }
        }
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
                Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectApplyList(),
                        getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                if (FuncUtil.isNotEmpty(req.getCondition())) {
                    getRepo().parseAdvancedQuery(req.getCondition(), aliasMap, from);
                } else {
                    getRepo().parseAdvancedQuery(req.getCondition(), null, from);
                }
            }
            return from;
        });
        return getRepo().selectJoinMap(wrapper);
    }

    /**
     * 统计个数
     *
     * @param req 高级查询条件
     * @return 统计个数数据
     */
    Long advancedCount(@RequestBody AdvancedQueryReq req);

    /**
     * 指标统计
     *
     * @param req 高级查询条件
     * @return 指标统计数据
     */
    List<StatisticRes> advancedStatistic(@RequestBody AdvancedStatisticReq req);

    /**
     * 统计个数
     *
     * @param req 高级查询条件
     * @return 统计个数数据
     */
    default Long countByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        wrapper.getSelectColum().add(new SelectString("count(1)", wrapper.getAlias()));
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
                Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectApplyList(),
                        getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                if (FuncUtil.isNotEmpty(req.getCondition())) {
                    getRepo().parseAdvancedQuery(req.getCondition(), aliasMap, from);
                } else {
                    getRepo().parseAdvancedQuery(req.getCondition(), null, from);
                }
            }
            return from;
        });
        return getRepo().selectJoinCount(wrapper);
    }

    /**
     * 指标统计分布
     *
     * @param req 指标统计分布
     * @return 统计个数数据
     */
    default List<StatisticRes> statisticByAdvancedReq(AdvancedStatisticReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        MPJLambdaWrapper<ENTITY> wrapper = buildStatisticWrapper(req.getSort(), req.getGroupByColumn(),
                req.getStatisticColumn());
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
                Map<String, String> aliasMap = getRepo().parseSelectApply(req.getSelectApplyList(),
                        getPortalService().getAliasMap(), getPortalService().getSelectApplyMap(), from);
                if (FuncUtil.isNotEmpty(req.getCondition())) {
                    getRepo().parseAdvancedQuery(req.getCondition(), aliasMap, from);
                } else {
                    getRepo().parseAdvancedQuery(req.getCondition(), null, from);
                }
            }
            return from;
        });
        return getRepo().selectJoinList(StatisticRes.class, wrapper);
    }

    /**
     * 导出
     *
     * @param req      查询条件
     * @param name     配置名称
     * @param request  请求
     * @param response 返回
     */
    void advancedQueryExport(@RequestBody AdvancedQueryReq req, @RequestParam(required = false) String name,
                             HttpServletRequest request, HttpServletResponse response);

    /**
     * 导出模版
     *
     * @param name     配置名称
     * @param request  请求
     * @param response 返回
     */
    void templateExport(@RequestParam(required = false) String name, HttpServletRequest request,
                        HttpServletResponse response);

    /**
     * 导入添加
     *
     * @param name 配置名称
     * @param file 请求文件
     */
    void importAdd(@RequestParam(required = false) String name, MultipartFile file);

    /**
     * 导入新增进度
     *
     * @param name 配置名称
     */
    Object importAddProgress(@RequestParam(required = false) String name);

    /**
     * 导入修改
     *
     * @param name 配置名称
     * @param file 请求文件
     */
    void importUpdate(@RequestParam(required = false) String name, MultipartFile file);

    /**
     * 导入修改进度
     *
     * @param name 配置名称
     */
    Object importUpdateProgress(@RequestParam(required = false) String name);

    /**
     * 数据库字段类
     *
     * @return 字段类
     */
    default Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    /**
     * 显示类
     *
     * @return 显示类
     */
    default Class<VO> getVoClass() {
        return (Class<VO>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    }

    /**
     * 是否管理员
     *
     * @return 是否管理员
     */
    default boolean isAdmin() {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            return getPortalService().isAdmin();
        } else {
            return false;
        }
    }

    /**
     * 增强service
     *
     * @return 自定义处理service
     */
    default PortalCommonService<ENTITY, VO> getPortalService() {
        return null;
    }

    /**
     * 添加前操作-管理员
     *
     * @param entity 添加数据
     */
    default void adminBeforeAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().adminBeforeAdd(entity);
        }
    }

    /**
     * 添加前操作
     *
     * @param entity 添加数据
     */
    default void beforeAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeAdd(entity);
        }
    }

    /**
     * 添加后操作
     *
     * @param entity 添加数据
     */
    default void afterAdd(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterAdd(entity);
        }
    }

    /**
     * 更新前操作-管理员
     *
     * @param entity 修改数据
     */
    default void adminBeforeUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().adminBeforeUpdate(entity);
        }
    }

    /**
     * 更新前操作
     *
     * @param entity 修改数据
     */
    default void beforeUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeUpdate(entity);
        }
    }

    /**
     * 更新后操作
     *
     * @param entity 修改数据
     */
    default void afterUpdate(ENTITY entity) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterUpdate(entity);
        }
    }

    /**
     * 更新指定字段
     *
     * @param vo       id
     * @param bizFunc  字段
     * @param bizValue 数值
     * @param <T>      类型
     * @return 更新是否成功
     */
    default <T> boolean update(IdReqVO vo, SFunction<ENTITY, ?> bizFunc, T bizValue) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        LambdaUtil.setValue(entity, bizFunc, bizValue);
        DbUtil.setUpdateAtTimeStamp(entity, new Date());
        return getRepo().updateById(entity, false);
    }

    /**
     * 数据库repo
     *
     * @return repo
     */
    BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo();

    /**
     * 更新多个字段
     *
     * @param vo       id
     * @param valueMap 数据map
     * @return 更新是否成功
     */
    default boolean update(IdReqVO vo, Map<SFunction<ENTITY, ?>, ?> valueMap) {
        Validator.assertNotNull(vo.getId(), ErrCodeSys.PA_PARAM_NULL, "id");
        ENTITY entity = getRepo().getById(vo.getId());
        Validator.assertNotNull(entity, ErrCodeSys.PA_DATA_NOT_EXIST, "节点");
        if (FuncUtil.isNotEmpty(valueMap)) {
            for (Map.Entry<SFunction<ENTITY, ?>, ?> entry : valueMap.entrySet()) {
                LambdaUtil.setValue(entity, entry.getKey(), entry.getValue());
            }
        }
        DbUtil.setUpdateAtTimeStamp(entity, new Date());
        return getRepo().updateById(entity, false);
    }

    /**
     * 删除前操作-管理员
     *
     * @param vo id
     */
    default void adminBeforeDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().adminBeforeDelete(vo);
        }
    }

    /**
     * 删除前操作
     *
     * @param vo id
     */
    default void beforeDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeDelete(vo);
        }
    }

    /**
     * 删除后操作
     *
     * @param vo id
     */
    default void afterDelete(IdReqVO vo) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().afterDelete(vo);
        }
    }

    /**
     * 查询前操作
     *
     * @param req 查询条件
     */
    default void beforeQuery(QueryConditionReq req) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeQuery(req);
        }
    }

    /**
     * 高级查询前操作
     *
     * @param req 高级查询
     */
    default void beforeQuery(AdvancedQueryReq req) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().beforeQuery(req);
        }
    }
}
