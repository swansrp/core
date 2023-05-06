package com.bidr.authorization.constants.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: AccountParam
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/27 09:00
 */
@Getter
@MetaParam
@AllArgsConstructor
public enum AccountParam implements Param {
    /**
     *
     */
    TEST_MODE_VALIDATE_SWITCH("测试模式开关", "0",  "0:0000可以通过,1正常验证"),
    TEST_MODE_VALIDATE_DEFAULT_CODE("测试模式验证码默认值","0000", "默认通过校验code"),

    TEST_MODE_SMS_SEND_SWITCH("模拟短信开关","1", "模拟短信开关"),

    LOGIN_SINGLETON("渠道单一用户登录控制","1", "渠道单一用户登录控制"),

    APP_ACCESS_TOKEN_EXPIRED("APP接入token过期时间","43200", "APP access_token有效期"),
    APP_REFRESH_TOKEN_EXPIRED("APP刷新token过期时间","604800", "APP refresh_token有效期"),
    WEB_ACCESS_TOKEN_EXPIRED("WEB接入token过期时间","86400", "网页端access_token有效期"),
    WEB_REFRESH_TOKEN_EXPIRED("WEB刷新token过期时间","604800", "网页端refresh_token有效期"),
    WECHAT_TOKEN_EXPIRED("微信接入token过期时间","604800", "微信 access_token有效期"),
    PLATFORM_TOKEN_EXPIRED("平台对接接入token过期时间","604800", "开发接入平台Token有效期"),
    CHANGE_PWD_TOKEN_EXPIRED("更换密码token过期时间","86400", "修改密码有效期"),
    ACCOUNT_LOCK_MISTAKE_NUMBER("密码输入错误锁定次数","5", "密码输入错误锁定次数"),
    ACCOUNT_ADMIN_ROLE_ID("系统管理员默认角色ID","1", "系统管理员默认角色ID"),
    ACCOUNT_DEFAULT_ROLE_ID("新注册用户默认角色ID","2", "新注册用户默认角色ID"),
    ACCOUNT_LOCK_INTERVAL("密码错误锁定时间","14000", "密码错误锁定时间"),
    EMAIL_SET_PWD_TITLE("设置密码邮件标题", "SHARP系统用户密码设置验证", "设置密码邮件标题"),
    EMAIL_SET_PWD_TEXT_FORMAT("设置密码邮件正文","请您点击下方地址完成账户密码设置\r\n%s", "设置密码邮件正文"),
    EMAIL_SET_PWD_CONFIRM_URL("邮件确认地址", "https://***/#/changePwd?userId=%s&token=%s", "邮件确认地址");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
