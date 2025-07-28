package com.bidr.kernel.controller.inf.base;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.mybatis.bo.DynamicColumn;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;
import com.github.yulichang.wrapper.MPJLambdaWrapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: AdminBaseQueryControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseQueryControllerInf<ENTITY, VO> extends AdminBaseInf<ENTITY, VO> {
    default Page<VO> queryByGeneralReq(QueryConditionReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            havingFields = getPortalService().getHavingFields();
            selectApplyMap = getPortalService().getSelectApplyMap();
        }
        return getRepo().select(req, aliasMap, havingFields, selectApplyMap, wrapper, getVoClass());
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

    default String getSql() {
        QueryConditionReq req = new QueryConditionReq();
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            havingFields = getPortalService().getHavingFields();
            selectApplyMap = getPortalService().getSelectApplyMap();
        }
        getRepo().select(req, aliasMap, havingFields, selectApplyMap, wrapper, getVoClass());
        Class<?> mapperClass = ReflectionUtil.getSuperClassGenericType(getRepo().getClass(), 0);
        return DbUtil.getRealSql(mapperClass, "selectJoinPage", wrapper);
    }

    default Page<VO> queryByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Map<String, String> aliasMap = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            selectApplyMap = getPortalService().getSelectApplyMap();
        }
        return getRepo().select(req, aliasMap, selectApplyMap, wrapper, getVoClass());
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
        Map<String, String> aliasMap = null;
        Set<String> havingFields = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            havingFields = getPortalService().getHavingFields();
            selectApplyMap = getPortalService().getSelectApplyMap();
        }
        return getRepo().select(req.getConditionList(), req.getSortList(), req.getSelectColumnCondition(), aliasMap,
                havingFields, selectApplyMap, wrapper, getVoClass());
    }

    default List<VO> selectByAdvancedReq(AdvancedQueryReq req) {
        if (!isAdmin()) {
            beforeQuery(req);
        }
        Map<String, String> aliasMap = null;
        Map<String, List<DynamicColumn>> selectApplyMap = null;
        MPJLambdaWrapper<ENTITY> wrapper = new MPJLambdaWrapper<>(getEntityClass());
        if (FuncUtil.isNotEmpty(getPortalService())) {
            aliasMap = getPortalService().getAliasMap();
            wrapper = getPortalService().getJoinWrapper();
            selectApplyMap = getPortalService().getSelectApplyMap();
        }
        return getRepo().select(req.getCondition(), req.getSortList(), req.getSelectColumnCondition(), aliasMap,
                selectApplyMap, wrapper, getVoClass());
    }
}
