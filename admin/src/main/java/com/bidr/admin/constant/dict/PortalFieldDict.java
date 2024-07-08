package com.bidr.admin.constant.dict;

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
    STRING("1", "单行文本"),
    ENUM("4", "下拉选择"),
    ENUM_MULTI_IN_ONE("18", "下拉选择(逗号隔开)"),
    ENTITY("11", "关联实体"),
    BOOLEAN("2", "真值"),
    NUMBER("3", "数字"),
    MONEY("16", "货币"),
    PERCENT("17", "百分比"),
    TREE("5", "树形下拉选择"),
    TREE_MULTI_IN_ONE("19", "树形下拉选择(逗号隔开)"),
    DATE("6", "日期"),
    DATETIME("7", "日期时间"),
    LINK("8", "超链接"),
    HTML("9", "富文本"),
    TEXT("10", "多行文本"),
    IMAGE("12", "图片"),
    VIDEO("13", "视频"),
    AUDIO("14", "音频"),
    FILE("15", "文件"),
    ENTITY_CONDITION("20", "实体条件"),
    DEFAULT("0", "默认");

    private final String value;
    private final String label;
}
