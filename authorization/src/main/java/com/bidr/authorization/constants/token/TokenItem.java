/**
 * Title: TokenItem.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @description Project Name: Grote
 * @Package: com.srct.service.account.constants
 * @since 2019-7-28 10:52
 */
package com.bidr.authorization.constants.token;

public enum TokenItem {
    // TOKEN
    TOKEN,
    // 客户端类型
    CLIENT_TYPE,
    // 用户编码
    OPERATOR,
    // 用户id,
    USER_ID,
    // 用户名
    USER_NAME,
    // 显示名称,
    NICK_NAME,
    // 头像
    AVATAR,
    // 手机号码
    PHONE_NUMBER,
    // 邮箱
    EMAIL,
    // 权限树,
    PERMIT_TREE,
    // 账号信息,
    USER_INFO,
    // 登录短信验证码,
    LOGIN_MSG_CODE,
    // 找回密码短信验证码,
    FIND_PASSWORD_MSG_CODE,
    // IOT token刷新时间戳
    IOT_TOKEN_NEED_UPDATE_TIMESTAMP,
    // IOT deviceId
    IOT_DEVICE_ID,
    // IOT 待更新的token
    IOT_UPDATE_TOKEN,
    // IOT 待删除旧token
    IOT_NEED_CLEAN_OLD_TOKEN,
    // websocket session
    SESSION_ID,
    // 对接平台PLATFORM
    PLATFORM,
    /*
     * 权限列表
     */
    PERMIT_LIST,
    /**
     * 角色列表
     */
    ROLE_MAP,

    TIMESTAMP,
    EXPIRED
}
