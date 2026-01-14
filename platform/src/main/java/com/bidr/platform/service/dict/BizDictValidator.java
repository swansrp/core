package com.bidr.platform.service.dict;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;

/**
 * Title: BizDictValidator
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/1/14 20:09
 */

public interface BizDictValidator {
    /**
     * 当前用户是否有权限查看当前bizId的字典
     *
     * @param bizId 业务id
     * @return 是否有权限
     */
    boolean validate(Object bizId);

    default void validator(Object bizId) {
        if (FuncUtil.isNotEmpty(bizId)) {
            Validator.assertTrue(validate(bizId), ErrCodeSys.PA_DATA_NOT_SUPPORT, "用户查看当前业务的字典");
        }

    }
}