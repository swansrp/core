package com.bidr.platform.constant.dict.portal;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: PortalTabelSizeDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/22 13:39
 */
@Getter
@RequiredArgsConstructor
@MetaDict(value = "PORTAL_TABLE_SIZE_DICT", remark = "表格大小字典")
public enum PortalTableSizeDict implements Dict {
    /**
     * 表格大小字典
     */
    SMALL("small", "小"),
    MIDDLE("middle", "中"),
    DEFAULT("", "默认");

    private final String value;
    private final String label;

}
