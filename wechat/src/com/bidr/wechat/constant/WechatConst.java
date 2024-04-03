package com.bidr.wechat.constant;

import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: WechatParam
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/23 23:04
 * @description Project Name: Mall
 * @Package: com.srct.service.wechat.constant
 */
@Getter
@AllArgsConstructor
public enum WechatConst implements Param {
    /**
     *
     */
    WECHAT_MINI_APP_ID("微信小程序接入ID", "", "微信小程序接入ID"),

    WECHAT_MINI_APP_SECRET("微信小程序接入密码", "", "微信小程序接入密码"),

    WECHAT_PUBLIC_APP_ID("微信公众号接入ID", "", "微信公众号接入ID"),

    WECHAT_PUBLIC_APP_SECRET("微信公众号接入密码", "", "微信公众号接入密码"),

    WECHAT_PUBLIC_TOKEN("微信公众号接入token", "", "微信公众号接入token"),

    WECHAT_PUBLIC_AES_KEY("微信公众号接入加密密码", "", "微信公众号接入加密密码"),

    WECHAT_OPENID_ACCOUNT("是否独立账户", "", "是否独立账户");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
