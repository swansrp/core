package com.bidr.kernel.constant.dict.portal;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: PortalFieldDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/05 09:07
 */
@MetaDict(value = "PORTAL_FIELD_DICT", remark = "字段类型字典")
@AllArgsConstructor
@Getter
public enum PortalFieldDict implements Dict {
    /**
     *
     */
    STRING(1, "单行文本"),
    BOOLEAN(2, "真值"),
    NUMBER(3, "数字"),
    ENUM(4, "下拉选择"),
    TREE(5, "树形下拉选择"),
    DATE(6, "日期"),
    DATETIME(7, "日期时间"),
    LINK(8, "超链接"),
    HTML(9, "富文本"),
    TEXT(10, "多行文本"),
    DEFAULT(0, "默认");

    private final Integer value;
    private final String label;
}
