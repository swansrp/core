package com.bidr.wechat.constant;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

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
@MetaParam("微信对接")
@AllArgsConstructor
public enum WechatParamConst implements Param {
    /**
     *
     */
    WECHAT_MINI_APP_ID("微信小程序接入ID", "", "【未用小程序则不动】微信小程序 AppID，在微信公众平台申请；不接小程序留空即可"),

    WECHAT_MINI_APP_SECRET("微信小程序接入密码", "", "【未用小程序则不动】微信小程序 AppSecret（与 AppID 成对）。⚠️ 敏感值：页面回显注意遮挡，泄漏需在公众平台重置"),

    WECHAT_PUBLIC_APP_ID("微信公众号接入ID", "", "【未用公众号则不动】微信公众号 AppID，在微信公众平台申请"),

    WECHAT_PUBLIC_APP_SECRET("微信公众号接入密码", "", "【未用公众号则不动】微信公众号 AppSecret（与 AppID 成对）。⚠️ 敏感值，泄漏需在公众平台重置"),

    WECHAT_PUBLIC_TOKEN("微信公众号接入token", "", "【未用公众号则不动】公众号服务器配置的校验 Token（自定字符串，与公众平台后台填同一个）"),

    WECHAT_PUBLIC_AES_KEY("微信公众号接入加密密码", "", "【未用公众号则不动】公众号消息加解密 EncodingAESKey（公众平台后台生成，两边填同一个）"),

    WECHAT_OPENID_ACCOUNT("是否独立账户", "", "【按需调整·接了微信才定】微信用户是否以独立账户进入系统账户体系的开关；未接微信留空"),

    WECHAT_SYSTEM_NAME("系统名称", "SHARP系统", "【按需调整】微信侧文案里出现的系统名（公众号关注自动回复等）；部署时改成实际产品名");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
