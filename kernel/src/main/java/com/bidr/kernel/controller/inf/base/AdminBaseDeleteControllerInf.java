package com.bidr.kernel.controller.inf.base;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;

import static com.bidr.kernel.constant.db.SqlConstant.VALID_FIELD;

/**
 * Title: AdminBaseDeleteControllerInf
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/28 14:55
 */

public interface AdminBaseDeleteControllerInf<ENTITY, VO> extends AdminBaseControllerInf<ENTITY, VO> {

    default void deleteEntity(IdReqVO vo) {
        if (isAdmin()) {
            adminBeforeDelete(vo);
        } else {
            beforeDelete(vo);
        }
        boolean result;
        if (ReflectionUtil.existedField(getEntityClass(), VALID_FIELD)) {
            ENTITY entity = getRepo().selectById(vo.getId());
            result = getRepo().disable(entity);
        } else {
            result = getRepo().deleteById(vo.getId());
        }
        Validator.assertTrue(result, ErrCodeSys.SYS_ERR_MSG, "删除失败");
        afterDelete(vo);
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
}
