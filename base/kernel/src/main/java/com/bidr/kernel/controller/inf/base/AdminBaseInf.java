package com.bidr.kernel.controller.inf.base;

import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.Query;

import java.beans.Introspector;
import java.util.Collection;

/**
 * Title: AdminBaseInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseInf<ENTITY, VO> {
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
     * 数据库repo
     *
     * @return repo
     */
    default BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo() {
        return (BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY>) BeanUtil.getBean(
                Introspector.decapitalize(getEntityClass().getSimpleName()) + "Service");
    }

    /**
     * 数据库字段类
     *
     * @return 字段类
     */
    default Class<ENTITY> getEntityClass() {
        return (Class<ENTITY>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    }

    /**
     * 递归检查AdvancedQuery中是否包含having字段
     *
     * @param advancedQuery 高级查询条件
     * @param havingFields  having字段集合
     * @return true-包含having字段, false-不包含
     */
    default boolean hasHavingFields(AdvancedQuery advancedQuery, Collection<String> havingFields) {
        if (FuncUtil.isEmpty(havingFields) || FuncUtil.isEmpty(advancedQuery)) {
            return false;
        }

        // 检查当前节点的property
        if (FuncUtil.isNotEmpty(advancedQuery.getProperty()) && havingFields.contains(advancedQuery.getProperty())) {
            return true;
        }

        // 递归检查子条件列表
        if (FuncUtil.isNotEmpty(advancedQuery.getConditionList())) {
            for (AdvancedQuery childQuery : advancedQuery.getConditionList()) {
                if (hasHavingFields(childQuery, havingFields)) {
                    return true;
                }
            }
        }

        return false;
    }
}
