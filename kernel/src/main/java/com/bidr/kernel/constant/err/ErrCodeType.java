package com.bidr.kernel.constant.err;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Title: ErrCodeType
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:27
 */
@Getter
@AllArgsConstructor
public enum ErrCodeType {
    /**
     * 错误类别。前端可以依据不同错误类别，有不同的错误提示交互。
     */
    SYSTEM("0"),
    BIZ("1"),
    PARAM("2"),
    AUTH("3"),
    VIEW("4"),
    THIRD_PARTY("5");

    private final String value;
    private final HashMap<Object, Enum<?>> map = new HashMap<>();
}
