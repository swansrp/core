package com.bidr.kernel.controller.inf.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.mybatis.bo.DynamicColumn;
import com.bidr.kernel.mybatis.repository.inf.PortalSelectRepo;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.Query;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.bidr.kernel.vo.query.QueryReqVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bidr.kernel.utils.DbUtil.extractSelectAliases;

/**
 * Title: AdminBaseQueryControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseQueryControllerInf<ENTITY, VO> extends AdminBaseInf<ENTITY, VO>, PortalSelectRepo<ENTITY> {
    default Page<VO> queryByGeneralReq(QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return query(new Query(req), req);
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

    default Page<VO> query(Query query, QueryReqVO page) {
        defaultQuery(query);
        Map<String, String> aliasMap = null;
        Set<String> havingFields;
        Map<String, List<DynamicColumn>> selectApplyMap;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            havingFields = getPortalService().getHavingFields();
            selectApplyMap = getPortalService().getSelectApplyMap();
        } else {
            selectApplyMap = null;
            havingFields = null;
        }
        filterSelectColumn(query.getSelectColumnList(), query.getDistinct(), wrapper);
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), aliasMap, selectApplyMap, wrapper);
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        boolean conditionHaveHavingFields = hasHavingFields(query.getCondition(), havingFields);
        if (defaultHaveHavingFields || conditionHaveHavingFields) {
            Map<String, String> newAliasMap = new LinkedHashMap<>();
            wrapper = buildSubWrapperWithoutHavingField(query, havingFields, selectAliasMap, defaultHaveHavingFields, conditionHaveHavingFields, newAliasMap);
            return getRepo().select(query, page.getCurrentPage(), page.getPageSize(), newAliasMap, null, wrapper, getVoClass());
        } else {
            return getRepo().select(query, page.getCurrentPage(), page.getPageSize(), selectAliasMap, havingFields, wrapper, getVoClass());
        }
    }

    default MPJLambdaWrapper<ENTITY> buildSubWrapperWithoutHavingField(Query query, Set<String> havingFields, Map<String, String> selectAliasMap,
                                                                       boolean defaultHaveHavingFields, boolean conditionHaveHavingFields,
                                                                       Map<String, String> newAliasMap) {
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        buildSubWrapperWithoutHavingField(wrapper, query, havingFields, selectAliasMap, defaultHaveHavingFields, conditionHaveHavingFields, newAliasMap);
        return wrapper;
    }

    default void buildSubWrapperWithoutHavingField(MPJLambdaWrapper<ENTITY> wrapper, Query query, Set<String> havingFields,
                                                   Map<String, String> selectAliasMap, boolean defaultHaveHavingFields, boolean conditionHaveHavingFields,
                                                   Map<String, String> newAliasMap) {
        Query newQuery = new Query();
        newQuery.setConditionList(query.getConditionList());
        newQuery.setDefaultQuery(defaultHaveHavingFields ? null : query.getDefaultQuery());
        newQuery.setCondition(conditionHaveHavingFields ? null : query.getCondition());
        wrapper.from(from -> {
            if (FuncUtil.isNotEmpty(getPortalService())) {
                getPortalService().getJoinWrapper(from);
            }
            for (String value : extractSelectAliases(from).values()) {
                newAliasMap.put(value, value);
                wrapper.getSelectColum().add(new SelectString(value, wrapper.getAlias()));
            }
            getRepo().buildPortalWrapper(newQuery, selectAliasMap, havingFields, from);
            return from;
        });
    }

    /**
     * 基础查询条件
     *
     * @param req 查询条件
     */
    default void defaultQuery(Query req) {
        if (FuncUtil.isNotEmpty(getPortalService())) {
            getPortalService().defaultQuery(req);
        }
    }

    /**
     * 获取查询sql语句
     *
     * @return sql语句
     */
    default String getSql() {
        Query query = new Query();
        defaultQuery(query);
        Map<String, String> aliasMap;
        Set<String> havingFields;
        Map<String, List<DynamicColumn>> selectApplyMap;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            havingFields = getPortalService().getHavingFields();
            selectApplyMap = getPortalService().getSelectApplyMap();
        } else {
            selectApplyMap = null;
            havingFields = null;
            aliasMap = null;
        }
        MPJLambdaWrapper<ENTITY> wr;
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), aliasMap, selectApplyMap, wrapper);
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        boolean conditionHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        if (hasHavingFields(query.getDefaultQuery(), havingFields)) {
            Map<String, String> newAliasMap = new LinkedHashMap<>();
            wrapper = buildSubWrapperWithoutHavingField(query, havingFields, selectAliasMap, defaultHaveHavingFields, conditionHaveHavingFields, newAliasMap);
            wr = getRepo().buildPortalWrapper(query, newAliasMap, null, wrapper);
        } else {
            wr = getRepo().buildPortalWrapper(query, selectAliasMap, havingFields, wrapper);
        }
        Class<?> mapperClass = ReflectionUtil.getSuperClassGenericType(getRepo().getClass(), 0);
        return DbUtil.getRealSql(mapperClass, "selectJoinPage", wr);
    }

    default Page<VO> queryByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return query(new Query(req), req);
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

    default List<VO> selectByGeneralReq(QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return select(new Query(req));
    }

    default List<VO> select(Query query) {
        defaultQuery(query);
        Map<String, String> aliasMap = null;
        Set<String> havingFields;
        Map<String, List<DynamicColumn>> selectApplyMap;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            havingFields = getPortalService().getHavingFields();
            selectApplyMap = getPortalService().getSelectApplyMap();
        } else {
            selectApplyMap = null;
            havingFields = null;
        }
        filterSelectColumn(query.getSelectColumnList(), query.getDistinct(), wrapper);
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), aliasMap, selectApplyMap, wrapper);
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        boolean conditionHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        if (defaultHaveHavingFields || conditionHaveHavingFields) {
            Map<String, String> newAliasMap = new LinkedHashMap<>();
            wrapper = buildSubWrapperWithoutHavingField(query, havingFields, selectAliasMap, defaultHaveHavingFields, conditionHaveHavingFields, newAliasMap);
            return getRepo().select(query, newAliasMap, null, wrapper, getVoClass());
        } else {
            return getRepo().select(query, selectAliasMap, havingFields, wrapper, getVoClass());
        }
    }

    default List<VO> selectByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        return select(new Query(req));
    }
}
