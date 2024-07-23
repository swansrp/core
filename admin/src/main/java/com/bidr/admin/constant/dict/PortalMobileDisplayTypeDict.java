package com.bidr.admin.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: PortalMobileDisplayTypeDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/7/8 10:03
 */

@Getter
@RequiredArgsConstructor
@MetaDict(value = "PORTAL_MOBILE_DISPLAY_TYPE_DICT", remark = "手机卡片显示类型字典")
public enum PortalMobileDisplayTypeDict implements Dict {
    /**
     * 手机卡片显示类型字典
     */
    NONE("0", "不显示"),
    TITLE("1", "标题"),
    SUB_TITLE("2", "副标题"),
    BADGE("3", "徽标"),
    CONTENT("4", "内容(长)"),
    TAG("5", "标签"),
    OWNER("6", "人员"),
    TIME("7", "时间"),
    REMARK("8", "备注文字"),
    LABEL("9", "内容(短)"),
    ;

    private final String value;
    private final String label;

}