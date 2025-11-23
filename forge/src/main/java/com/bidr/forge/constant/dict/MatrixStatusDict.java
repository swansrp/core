package com.bidr.forge.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 矩阵表状态字典
 *
 * @author sharp
 * @since 2025-11-21
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "MATRIX_STATUS_DICT", remark = "矩阵表状态")
public enum MatrixStatusDict implements Dict {
    /**
     * 矩阵表状态字典
     */

    NOT_CREATED("0", "未创建"),
    CREATED("1", "已创建"),
    SYNCED("2", "已同步"),
    PENDING_SYNC("3", "待同步");

    private final String value;
    private final String label;
}
