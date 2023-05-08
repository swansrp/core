package com.bidr.platform.constant.err;

import com.bidr.kernel.constant.err.ErrCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ConfigErrorCode
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/27 22:51
 */
@AllArgsConstructor
@Getter
public enum ConfigErrorCode implements ErrCode {
    /**
     *
     */

    PARAM_IS_NOT_EXISTED(60, "[%s]配置项不存在");

    private final Integer errCode;
    private final String errMsg;
}
