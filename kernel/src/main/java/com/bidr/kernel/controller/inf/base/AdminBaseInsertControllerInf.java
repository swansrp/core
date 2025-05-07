package com.bidr.kernel.controller.inf.base;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;

import java.util.Date;

/**
 * Title: AdminBaseInsertControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseInsertControllerInf<ENTITY, VO> extends AdminBaseInf<ENTITY, VO> {

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

    default void insertEntity(VO vo) {
        ENTITY entity = ReflectionUtil.copy(vo, getEntityClass());
        if (isAdmin()) {
            adminBeforeAdd(entity);
        } else {
            beforeAdd(entity);
        }
        DbUtil.setCreateAtTimeStamp(entity, new Date());
        DbUtil.setUpdateAtTimeStamp(entity, new Date());
        Boolean result = getRepo().insert(entity);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "新增失败");
        afterAdd(entity);
    }
}
