package com.bidr.authorization.constants.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: MenuTypeDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:07
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "MENU_TYPE_DICT", remark = "菜单类型")
public enum MenuTypeDict implements Dict {
    /**
     * 菜单类型
     */

    MENU(0, "导航"),
    SUB_MENU(1, "菜单"),
    CONTENT(2, "目录"),
    BUTTON(3, "按钮");

    private final Integer value;
    private final String label;


}
