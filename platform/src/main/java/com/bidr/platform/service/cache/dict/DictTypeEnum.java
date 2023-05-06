package com.bidr.platform.service.cache.dict;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.platform.constant.err.DictErrorCode;
import com.bidr.platform.dao.entity.SysDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: DictTypeEnum
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/26 11:06
 */
@Getter
@AllArgsConstructor
public enum DictTypeEnum {
    /**
     * 字典缓存类型
     */
    NAME(DictErrorCode.DICT_NAME_IS_NOT_EXISTED, SysDict::getDictItem),
    VALUE(DictErrorCode.DICT_VALUE_IS_NOT_EXISTED, SysDict::getDictValue),
    LABEL(DictErrorCode.DICT_LABEL_IS_NOT_EXISTED, SysDict::getDictLabel);

    private final DictErrorCode errorCode;
    private final GetFunc<SysDict, String> getFunc;
}
