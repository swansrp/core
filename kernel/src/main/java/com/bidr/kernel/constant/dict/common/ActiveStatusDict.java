package com.bidr.kernel.constant.dict.common;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ActiveStatusDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 10:35
 */
@AllArgsConstructor
@Getter
@MetaDict(value = "ACTIVE_STATUS_DICT", remark = "激活状态字典")
public enum ActiveStatusDict implements Dict {
    /**
     * 激活状态字典
     */
    ACTIVATE(1, "已启用"),
    PENDING(2, "待启用"),
    DEACTIVATE(0, "已停用"),
    LOCKING(3, "已锁定"),;

    private final Integer value;
    private final String label;
}
