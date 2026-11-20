package com.bidr.kernel.controller.inf.statistic;

import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.kernel.controller.inf.base.AdminBaseInf;
import com.bidr.kernel.controller.inf.base.AdminBaseQueryControllerInf;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.Query;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Title: AdminStatisticBaseInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/29 8:42
 */

public interface AdminStatisticBaseInf<ENTITY, VO> extends AdminBaseInf<ENTITY, VO>, AdminBaseQueryControllerInf<ENTITY, VO> {
    /**
     * 查询子表构建方法
     *
     * @param query 请求
     * @param from  查询wrapper
     * @return 带有查询子表的wrapper
     */
    default MPJLambdaWrapper<ENTITY> buildSubFromWrapper(Query query, MPJLambdaWrapper<ENTITY> from) {
        Set<String> havingFields = FuncUtil.isNotEmpty(getPortalService()) ? getPortalService().getHavingFields() : null;
        boolean defaultHaveHavingFields = hasHavingFields(query.getDefaultQuery(), havingFields);
        boolean conditionHaveHavingFields = hasHavingFields(query.getCondition(), havingFields);
        return buildSubFromWrapper(query, from, defaultHaveHavingFields, conditionHaveHavingFields);
    }

    default MPJLambdaWrapper<ENTITY> buildSubFromWrapper(Query query, MPJLambdaWrapper<ENTITY> from,
                                                         boolean defaultHaveHavingFields, boolean conditionHaveHavingFields) {
        if (FuncUtil.isEmpty(getPortalService())) {
            // 未挂 PortalCommonService 的通用 admin 控制器(如 AdminConfigController):
            // 没有 having/join/动态列扩展, 但仍要把查询条件构建进子查询;
            // 静默返回会丢掉 count/summary/statistic/pivot 的全部过滤条件。
            buildSubSelectColumns(from);
            Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), null, null, from);
            getRepo().parseQuery(query, selectAliasMap, null, from);
            return from;
        }
        Map<String, String> selectAliasMap = parseSelectApply(query.getSelectColumnCondition(), getPortalService().getAliasMap(),
                getPortalService().getSelectApplyMap(), from);
        if (defaultHaveHavingFields || conditionHaveHavingFields) {
            Map<String, String> newAliasMap = new LinkedHashMap<>();
            buildSubWrapperWithoutHavingField(from, query, getPortalService().getHavingFields(), selectAliasMap, defaultHaveHavingFields,
                    conditionHaveHavingFields, newAliasMap);
            getRepo().parseQuery(query, newAliasMap, null, from);
        } else {
            getPortalService().getJoinWrapper(from);
            from = getPortalService().getDynamicJoinWrapper(from, query.getSelectColumnCondition());
            getRepo().parseQuery(query, selectAliasMap, getPortalService().getHavingFields(), from);
        }
        return from;
    }

    /**
     * 无 PortalCommonService 时给子查询补列。外层统计 SQL(metricColumn 分组/metricCondition case-when/
     * statisticColumn 求和)与结果解析都按前端传来的属性名口径引用字段，所以子查询把每个实体列
     * 别名为属性名(如 config_group AS configGroup)；别不暴露裸 DB 列名，两种口径混发时以属性名为准
     *
     * @param from 子查询wrapper
     */
    default void buildSubSelectColumns(MPJLambdaWrapper<ENTITY> from) {
        TableInfo tableInfo = TableInfoHelper.getTableInfo(getEntityClass());
        if (tableInfo == null) {
            return;
        }
        if (FuncUtil.isNotEmpty(tableInfo.getKeyColumn())) {
            from.getSelectColum().add(new SelectString(String.format("%s AS %s",
                    tableInfo.getKeyColumn(), tableInfo.getKeyProperty()), tableInfo.getKeyProperty()));
        }
        for (TableFieldInfo fieldInfo : tableInfo.getFieldList()) {
            from.getSelectColum().add(new SelectString(String.format("%s AS %s",
                    fieldInfo.getColumn(), fieldInfo.getProperty()), fieldInfo.getProperty()));
        }
    }
}
