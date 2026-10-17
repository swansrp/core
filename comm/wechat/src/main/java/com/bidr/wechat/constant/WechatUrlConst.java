package com.bidr.wechat.constant;

/**
 * Title: WechatUrlConst
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 13:16
 * @description Project Name: Seed
 * @Package: com.srct.service.wechat.constant
 */
public class WechatUrlConst {
    /**
     * 微信小程序获取OPENID地址
     */
    public static final String WECHAT_MINI_OPENID_GET_URL_FORMAT =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&grant_type=authorization_code&js_code=%s";
    /**
     * 微信公众号获取token地址
     */
    public static final String WECHAT_PUBLIC_ACCESS_TOKEN_GET_URL_FORMAT =
            "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";

    /**
     * 微信公众号OAuth地址
     */
    public static final String WECHAT_PUBLIC_OAUTH_URL_FORMAT =
            "https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_userinfo&state=%s#wechat_redirect";

    /**
     * 微信公众号Oauth获取accessToken地址
     */
    public static final String WECHAT_PUBLIC_OAUTH_ACCESS_TOKEN_GET_URL_FORMAT =
            "https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code";

    /**
     * 微信公众号Oauth获取用户信息地址
     */
    public static final String WECHAT_PUBLIC_OAUTH_USER_INFO_GET_URL =
            "https://api.weixin.qq.com/sns/userinfo";

    /**
     * 微信公众号js sdk ticket获取地址
     */
    public static final String WECHAT_PUBLIC_JS_API_TICKET_GET_URL =
            "https://api.weixin.qq.com/cgi-bin/ticket/getticket";

    /**
     * 微信公众号推送消息模板地址
     */
    public static final String WECHAT_PUBLIC_MSG_TEMPLATE_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/message/template/send";
    /**
     * 微信公众号获取用户基本信息地址
     */
    public static final String WECHAT_PUBLIC_USER_INFO_GET_URL =
            "https://api.weixin.qq.com/cgi-bin/user/info";
    /**
     * 微信公众号获取用户列表
     */
    public static final String WECHAT_PUBLIC_USER_INFO_LIST_GET_URL =
            "https://api.weixin.qq.com/cgi-bin/user/get";

    /**
     * 微信公众号创建用户标签
     */
    public static final String WECHAT_PUBLIC_CREATE_USER_TAG_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/tags/create";

    /**
     * 微信公众号获取用户标签
     */
    public static final String WECHAT_PUBLIC_GET_USER_TAG_GET_URL =
            "https://api.weixin.qq.com/cgi-bin/tags/get";

    /**
     * 微信公众号编辑用户标签
     */
    public static final String WECHAT_PUBLIC_UPDATE_USER_TAG_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/tags/update";

    /**
     * 微信公众号删除用户标签
     */
    public static final String WECHAT_PUBLIC_DELETE_USER_TAG_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/tags/delete";

    /**
     * 微信公众号为用户打标签
     */
    public static final String WECHAT_PUBLIC_SET_USER_TAG_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/tags/members/batchtagging";

    /**
     * 微信公众号创建菜单
     */
    public static final String WECHAT_PUBLIC_CREATE_MENU_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/menu/create";

    /**
     * 微信公众号删除所有菜单
     */
    public static final String WECHAT_PUBLIC_DELETE_MENU_GET_URL =
            "https://api.weixin.qq.com/cgi-bin/menu/delete";

    /**
     * 微信公众号创建自定义菜单
     */
    public static final String WECHAT_PUBLIC_CREATE_CONDITIONAL_MENU_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/menu/addconditional";

    /**
     * 微信公众号删除自定义菜单
     */
    public static final String WECHAT_PUBLIC_DELETE_CONDITIONAL_MENU_POST_URL =
            "https://api.weixin.qq.com/cgi-bin/menu/delconditional";

    /**
     * 微信公众号身份证识别
     */
    public static final String WECHAT_OCR_ID_CARD =
            "https://api.weixin.qq.com/cv/ocr/idcard";
}
