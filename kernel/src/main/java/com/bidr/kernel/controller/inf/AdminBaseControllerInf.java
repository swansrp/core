package com.bidr.kernel.controller.inf;

import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.QueryConditionReq;

/**
 * Title: AdminBaseControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseControllerInf<ENTITY, VO> {
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
     * 数据库repo
     *
     * @return repo
     */
    BaseSqlRepo<? extends MyBaseMapper<ENTITY>, ENTITY> getRepo();

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
