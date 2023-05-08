package com.bidr.kernel.constant.err;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ErrCodeLevel
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 11:27
 */
@Getter
@AllArgsConstructor
public enum ErrCodeLevel {
    /**
     * errCode等级
     */
    FATAL("5"),
    ERROR("4"),
    WARN("3"),
    INFO("2"),
    DEBUG("1"),
    TRACE("0");

    private final String value;
}
