package com.bidr.kernel.controller.inf.base;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DbUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;

import java.util.Date;
import java.util.Map;

/**
 * Title: AdminBaseUpdateControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseUpdateControllerInf<ENTITY, VO> extends AdminBaseInf<ENTITY, VO> {
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

    default void updateEntity(VO vo, Boolean strict) {
        ENTITY entity = ReflectionUtil.copy(vo, getEntityClass());
        ENTITY originalEntity = getRepo().selectById(entity);
        entity = ReflectionUtil.copyAndMerge(entity, originalEntity, strict);
        if (isAdmin()) {
            adminBeforeUpdate(entity);
        } else {
            beforeUpdate(entity);
        }
        DbUtil.setUpdateAtTimeStamp(entity, new Date());
        Boolean result = getRepo().updateById(entity, !strict);
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "更新失败");
        afterUpdate(entity);
    }
}
