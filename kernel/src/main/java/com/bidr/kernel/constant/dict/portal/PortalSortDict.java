package com.bidr.kernel.constant.dict.portal;

import cn.hutool.core.util.EnumUtil;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: PortalSortDict
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/05 15:56
 */
@MetaDict(value = "PORTAL_SORT_DICT", remark = "前端查询排序字典")
@AllArgsConstructor
public enum PortalSortDict implements Dict {
    /**
     * 管理界面排序字典
     */
    ASC(0, "正序"),
    DESC(1, "倒序");

    @Getter
    private final Integer value;
    @Getter
    private final String label;

    public static PortalSortDict of(Integer value) {
        return EnumUtil.getBy(PortalSortDict::getValue, value);
    }
}
