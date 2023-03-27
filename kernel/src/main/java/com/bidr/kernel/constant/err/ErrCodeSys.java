package com.bidr.kernel.constant.err;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Title: ErrCodeSys
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:18
 */
@AllArgsConstructor
@Getter
public enum ErrCodeSys implements ErrCode {

    /**
     * 系统错误值
     */
    SUCCESS(0, "SUCCESS"),

    SYS_ERR(100, "系统错误"),
    SYS_ERR_MSG(101, "%s"),
    SYS_VALIDATE_NOT_PASS(102, "参数校验不通过:%s"),

    PA_PARAM_NULL(10, "%s不能为空"),
    PA_PARAM_FORMAT(11, "%s格式错误"),
    PA_DATA_HAS_EXIST(12, "该%s数据已存在"),
    PA_DATA_NOT_EXIST(13, "该%s数据不存在"),
    PA_DATA_DIFF(14, "该%s数据不相同"),

    SYS_CONFIG_NOT_EXIST(20, "当前配置不支持该%s"),
    SYS_SESSION_NOT_SAME(21, "登录超时,%s不一致"),
    SYS_SESSION_TIME_OUT(22, "登录信息已过期,请重新登录"),
    SYS_PERMIT_ERROR(23, "权限错误:%s"),

    STATE_MACHINE_TRANSFER_NOT_ALLOW(30, "当前状态%s不支持%s操作"),
    STATE_MACHINE_TRANSFER_ROLE_NOT_ALLOW(31, "该角色不支持%s操作");

    private final Integer errCode;
    private final String errMsg;
}
