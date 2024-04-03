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
    CLICK(1, "click", "点击"),
    VIEW(2, "view", "页面跳转"),
    SCAN_PUSH(3, "scancode_push", "扫码push"),
    SCAN_WAIT(4, "scancode_waitmsg", "扫码wait"),
    PIC_PHOTO(5, "pic_sysphoto", "照片"),
    PIC_PHOTO_ALBUM(6, "pic_photo_or_album", "相册照片"),
    PIC_WEIXIN(7, "pic_weixin", "微信照片"),
    LOCATION(8, "location_select", "定位"),
    MEDIA(9, "media_id", "媒体"),
    VIEW_LIMITED(10, "view_limited", "页面"),
    MINI(11, "miniprogram", "小程序");


    private final Integer value;
    private final String text;
    private final String label;

}
