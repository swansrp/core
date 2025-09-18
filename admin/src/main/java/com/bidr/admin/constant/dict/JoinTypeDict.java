package com.bidr.admin.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: JoinTypeDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/18 22:10
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "JOIN_TYPE_DICT", remark = "表关联类型")
public enum JoinTypeDict implements Dict {
    /**
     * 对齐方式字典
     */
    INNER("0", "内联"),
    LEFT("1", "左联"),
    RIGHT("2", "右联"),
    FULL("3", "全联");

    private final String value;
    private final String label;

}