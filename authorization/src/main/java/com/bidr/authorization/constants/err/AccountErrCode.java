package com.bidr.authorization.constants.err;

import com.bidr.kernel.constant.err.ErrCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: AccountErrCode
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 10:28
 */
@Getter
@AllArgsConstructor
public enum AccountErrCode implements ErrCode {
    /**
     *
     */

    AC_LOCK(1001, "用户已锁定"),

    AC_USER_ALREADY_EXISTED(1002, "用户名已存在"),

    AC_USER_NOT_EXISTED(1003, "用户不存在"),

    AC_GRAPH_CODE_ERROR(1004, "图形验证码错误"),

    AC_GRAPH_CODE_EXPIRED(1005, "图形验证码过期"),

    AC_GRAPH_CODE_NOT_EXISTED(1006, "图形验证码已失效"),

    AC_MSG_CODE_ERROR(1007, "短信验证码错误"),

    AC_MSG_CODE_EXPIRED(1008, "短信验证码过期"),

    AC_MSG_CODE_INTERNAL(1009, "短信验证码已发送,请稍后再试"),

    AC_PASSWORD_NOT_EXISTED(1010, "尚未设置密码"),

    AC_PASSWORD_CONFIRM_DIFF(1011, "确认密码不一致"),

    AC_PASSWORD_IS_EQUIVALENT_DIFFERENT(1012, "密码存在连续数字"),

    AC_PASSWORD_LOGIN_FORMAT(1013, "密码格式不正确"),

    AC_PASSWORD_MAX_ERROR_TIMES(1014, "密码错误尝试次数达到上限,账号已经锁定,24小时后自动解锁"),

    AC_PASSWORD_MAX_ERROR_COUNT_DOWN(1015, "密码错误尝试次数达到上限,账号已经锁定,%s后自动解锁"),

    AC_PASSWORD_NOT_RIGHT(1016, "输入密码不正确,今日可试次数还剩%s次"),

    AC_PASSWORD_OLD_NOT_RIGHT(1017, "原密码错误"),

    AC_PASSWORD_OLD_NEW_SAME(1018, "新密码与旧密码相同"),

    AC_PERMIT_NOT_EXISTED(1019, "没有配置权限,请联系管理员"),

    AC_PHONE_NUMBER_ALREADY_REGISTER(1020, "该手机号已经注册"),

    AC_NO_GET_MSG_CODE(1021, "请先获取短信验证码"),

    AC_IS_NOT_ADMIN(1022, "不是管理员,无权限"),
    AC_DONT_HAVE_PERMIT(1023, "无指定接口权限"),

    AC_ROLE_HAS_USER(1024, "该角色下尚有用户, 不能删除该角色"),

    AC_GROUP_HAS_USER(1025, "该用户组下尚有用户, 不能删除该用户组"),
    AC_PARTNER_NOT_EXISTED(1026, "该渠道不存在"),

    AC_PARTNER_NOT_AVAILABLE(1027, "该渠道账号不可用"),

    AC_USER_NOT_IN_GROUP(1028, "尚未加入用户组"),

    AC_PASSWORD_IS_NOT_INITIAL(1029, "密码已设置"),
    ;

    private final Integer errCode;

    private final String errMsg;
}
