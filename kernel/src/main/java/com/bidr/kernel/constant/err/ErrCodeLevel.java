package com.bidr.kernel.constant.err;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: ErrCodeLevel
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:27
 */
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

    @Getter
    @Setter
    private String value;
}
