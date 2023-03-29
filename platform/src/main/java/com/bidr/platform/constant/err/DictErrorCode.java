package com.bidr.platform.constant.err;

import com.bidr.kernel.constant.err.ErrCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: DictErrorCode
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/27 19:33
 */
@AllArgsConstructor
@Getter
public enum DictErrorCode implements ErrCode {
    /**
     *
     */

    DICT_IS_NOT_EXISTED(50, "[%s]字典不存在"),
    DICT_NAME_IS_NOT_EXISTED(51, "[%s]字典不存在名字为[%s]的条目"),
    DICT_VALUE_IS_NOT_EXISTED(52, "[%s]字典不存在值为[%s]的条目"),
    DICT_LABEL_IS_NOT_EXISTED(53, "[%s]字典不存在显示为[%s]的条目"),

    DICT_IS_ALREADY_EXISTED(55, "[%s]字典已存在"),
    DICT_ITEM_IS_ALREADY_EXISTED(56, "[%s]字典[%s]项已存在"),
    ;

    private final Integer errCode;
    private final String errMsg;
}
