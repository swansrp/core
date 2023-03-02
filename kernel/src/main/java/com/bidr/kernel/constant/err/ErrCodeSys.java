package com.bidr.kernel.constant.err;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: ErrCodeSys
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:18
 */
@AllArgsConstructor
public enum ErrCodeSys implements ErrCode {

    /**
     * 系统错误值
     */
    SUCCESS("0000", "SUCCESS"),

    SYS_ERR("0100", "系统错误"),
    SYS_ERR_MSG("0101", "%s"),
    SYS_VALIDATE_NOT_PASS("0102", "参数校验不通过:%s"),

    PA_PARAM_NULL("0010", "%s不能为空"),
    PA_PARAM_FORMAT("0011", "%s格式错误"),
    PA_DATA_HAS_EXIST("0012", "该%s数据已存在"),
    PA_DATA_NOT_EXIST("0013", "该%s数据不存在"),
    PA_DATA_DIFF("0014", "该%s数据不相同"),

    SYS_CONFIG_NOT_EXIST("0020", "当前配置不支持该%s"),
    SYS_SESSION_NOT_SAME("0021", "登录超时,%s不一致"),
    SYS_SESSION_TIME_OUT("0022", "登录信息已过期,请重新登录"),
    SYS_PERMIT_ERROR("0023", "权限错误:%s"),

    STATE_MACHINE_TRANSFER_NOT_ALLOW("0030", "当前状态%s不支持%s操作"),
    STATE_MACHINE_TRANSFER_ROLE_NOT_ALLOW("0031", "该角色不支持%s操作");

    @Getter
    @Setter
    private String errCode;
    @Getter
    @Setter
    private String errMsg;
}
