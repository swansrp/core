package com.bidr.authorization.constants.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: UserTypeDict
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/25 16:02
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "MENU_TYPE_DICT", remark = "菜单类型")
public enum UserTypeDict implements Dict {
    /**
     * 菜单类型
     */

    ACCOUNT("0", "主数据同步"),
    SSO("1", "单点登录同步"),
    REGISTER("2", "自主注册"),
    OTHER("3", "其他");

    private final String value;
    private final String label;


}
