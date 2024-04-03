package com.bidr.wechat.constant;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: WechatMenuTypeConst
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/5/5 16:01
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "WECHAT_MENU_TYPE_DICT", remark = "微信公众号菜单类型")
public enum WechatMenuTypeDict implements Dict {

    MENU(0, "MENU", "菜单"),
    CLICK(0, "click", "click"),
    VIEW(0, "view", "view"),
    SCAN_PUSH(0, "scancode_push", "菜单"),
    SCAN_WAIT(0, "scancode_waitmsg", "菜单"),
    PIC_PHOTO(0, "pic_sysphoto", "菜单"),
    PIC_PHOTO_ALBUM(0, "pic_photo_or_album", "菜单"),
    PIC_WEIXIN(0, "pic_weixin", "菜单"),
    LOCATION(0, "location_select", "菜单"),
    MEDIA(0, "media_id", "菜单"),
    VIEW_LIMITED(0, "view_limited", "菜单"),
    MINI(0, "miniprogram", "菜单");


    private final Integer value;
    private final String text;
    private final String label;

}
