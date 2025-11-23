package com.bidr.forge.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 矩阵变更类型字典
 *
 * @author sharp
 * @since 2025-11-21
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "JOIN_TYPE_DICT", remark = "矩阵变更类型")
public enum MatrixChangeTypeDict implements Dict {
    /**
     * 矩阵变更类型字典
     */

    CREATE_TABLE("1", "创建表"),
    ADD_COLUMN("2", "添加字段"),
    MODIFY_COLUMN("3", "调整字段顺序"),
    ADD_INDEX("4", "添加索引"),
    DROP_INDEX("5", "删除索引"),
    DROP_COLUMN("6", "删除字段"),
    MODIFY_TABLE_COMMENT("7", "修改表注释");

    private final String value;
    private final String label;
}
