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
 * @since 2023/04/27 09:00
 */
@Getter
@MetaParam("账号认证")
@AllArgsConstructor
public enum AccountParam implements Param {
    /**
     *
     */
    TEST_MODE_VALIDATE_SWITCH("测试模式开关", "0",
            "【🔴 生产必须置 1】0＝验证码校验恒过固定码（开发联调用的后门）；生产环境留 0 等于短信验证形同虚设。改后立即生效"),

    TEST_MODE_VALIDATE_DEFAULT_CODE("测试模式验证码默认值", "0000",
            "【出厂默认即可】配合上一开关使用：测试模式下放行的固定验证码。开关为 1 时本项无作用"),

    TEST_MODE_SMS_SEND_SWITCH("模拟短信开关", "1",
            "【未启用短信则不动】1＝短信只记日志不真发（防开发/演示环境误发短信）；接了真实短信网关才置 0"),

    PHONE_NUMBER_REGISTER("通过手机号码注册", "0",
            "【按需调整】注册是否必填手机号：0 不必填、1 必填。只影响之后的新注册"),

    LOGIN_SINGLETON("渠道单一用户登录控制", "1",
            "【按需调整】1＝同一账号同渠道只允许一处在线，重复登录会顶掉旧会话（多人共用账号调试时会被互相踢下线，届时可置 0）；改后立即生效"),

    APP_ACCESS_TOKEN_EXPIRED("APP接入token过期时间", "43200",
            "【出厂默认即可】APP 端登录态有效期（秒），默认 12 小时；调大＝免登录更久、被盗用窗口更长"),

    APP_REFRESH_TOKEN_EXPIRED("APP刷新token过期时间", "604800",
            "【出厂默认即可】APP 端续期凭证有效期（秒），默认 7 天；到 access 过期前靠它静默续期"),

    WEB_ACCESS_TOKEN_EXPIRED("WEB接入token过期时间", "86400",
            "【按需调整】网页端登录态有效期（秒），默认 24 小时；觉得“老要重新登录”调这个"),

    WEB_REFRESH_TOKEN_EXPIRED("WEB刷新token过期时间",  "604800",
            "【出厂默认即可】网页端续期凭证有效期（秒），默认 7 天"),

    WECHAT_TOKEN_EXPIRED("微信接入token过期时间", "604800",
            "【未启用微信则不动】微信渠道登录态有效期（秒），默认 7 天"),

    PLATFORM_TOKEN_EXPIRED("平台对接接入token过期时间", "604800",
            "【未做平台对接则不动】第三方系统以平台身份接入时的 Token 有效期（秒），默认 7 天"),

    CHANGE_PWD_TOKEN_EXPIRED("更换密码token过期时间", "86400",
            "【出厂默认即可】修改密码链接/凭证有效期（秒），默认 24 小时"),

    ACCOUNT_LOCK_MISTAKE_NUMBER("密码输入错误锁定次数", "5",
            "【按需调整】连续输错几次密码锁定账号；防爆破与误锁的折中，一般不动"),

    ACCOUNT_ADMIN_ROLE_ID("系统管理员默认角色ID", "1",
            "【装机核对】系统管理员账号默认绑定的角色 ID，对应角色表种子数据；重建过角色表就要改成实际 ID，配错会把管理员权限挂到错误角色上"),

    ACCOUNT_DEFAULT_ROLE_ID("新注册用户默认角色ID", "2",
            "【装机核对】新注册用户自动获得的角色 ID，同上对应角色表种子；决定“注册进来能干什么”"),

    ACCOUNT_LOCK_INTERVAL("密码错误锁定时间", "14000",
            "【出厂默认即可】触发锁定后的锁定时长（毫秒），默认 14 秒"),

    EMAIL_SET_PWD_TITLE("设置密码邮件标题", "SHARP系统用户密码设置验证",
            "【未用邮件则不动】“设置密码”邮件的标题；用了邮件通道再按产品名改"),

    EMAIL_SET_PWD_TEXT_FORMAT("设置密码邮件正文", "请您点击下方地址完成账户密码设置\r\n%s",
            "【未用邮件则不动】“设置密码”邮件正文；⚠️ 必须保留 %s 占位符（链接注入点）"),

    EMAIL_SET_PWD_CONFIRM_URL("邮件确认地址", "https://***/#/changePwd?userId=%s&token=%s",
            "【现场必配·启用邮件时】邮件内跳转的改密页地址，出厂是占位星号——必须改成实际访问域名，且保留 userId/token 两个 %s 参数"),

    PASSWORD_EXPIRED("密码有效期", "365",
            "【按需调整】密码多少天未改强制过期（天），默认 365；有等保/合规要求时按其数值设置");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
