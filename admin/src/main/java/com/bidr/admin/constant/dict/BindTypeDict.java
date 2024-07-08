package com.bidr.admin.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: BindTypeDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/7/2 19:44
 */

@Getter
@RequiredArgsConstructor
@MetaDict(value = "PORTAL_BIND_TYPE_DICT", remark = "实体关系")
public enum BindTypeDict implements Dict {
    /**
     * 对齐方式字典
     */
    MASTER_ATTACH("0", "本实体主键:目标实体字段"),
    MASTER_ATTACH_MULTI_IN_ONE("3", "本实体主键:目标实体字段(逗号隔开)"),
    ATTACH_MASTER("1", "本实体字段:目标实体主键"),
    ATTACH_MASTER_MULTI_IN_ONE("4", "本实体字段:目标实体主键(逗号隔开)"),
    _N_N("2", "多对多");

    private final String value;
    private final String label;

}