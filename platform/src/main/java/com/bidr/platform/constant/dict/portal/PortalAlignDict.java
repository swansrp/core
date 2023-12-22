package com.bidr.platform.constant.dict.portal;

/**
 * Title: PortalAlignDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/23 15:30
 */

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@MetaDict(value = "PORTAL_ALIGN_DICT", remark = "对齐方式")
public enum PortalAlignDict implements Dict {
    /**
     * 对齐方式字典
     */
    LEFT("left", "左对齐"),
    RIGHT("right", "右对齐"),
    DEFAULT("center", "居中");

    private final String value;
    private final String label;

}
