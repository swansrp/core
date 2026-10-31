package com.bidr.authorization.constants.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: GenderDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/02 09:39
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "GENDER_DICT", remark = "性别字典")
public enum GenderDict implements Dict {
    /**
     * 菜单类型
     */
    MALE("1", "男"),
    FEMALE("2", "女");

    private final String value;
    private final String label;


}
