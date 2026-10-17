package com.bidr.agent.runtime.client;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.constant.err.ErrCodeType;

/**
 * 平台错误码的 ErrCode 适配：**把平台业务码原样带进框架 status.code**（D6），
 * 平台 msg 进 status.details（前端读 details）。仅会话语义类平台码走此直通，
 * 我方 Key/平台内部类错误由 {@link AgentRuntimeErrors} 转内部错误。
 *
 * @author sharp
 * @since 2026/9/22
 */
public class AgentRuntimeErrorCode implements ErrCode {

    private final int code;
    private final String msg;

    public AgentRuntimeErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg == null ? "平台返回失败" : msg;
    }

    @Override
    public Integer getErrCode() {
        return code;
    }

    @Override
    public String name() {
        return "AGENT_RUNTIME_" + code;
    }

    @Override
    public String getErrMsg() {
        return msg;
    }

    /**
     * 平台消息可能含 %，一律按原文返回不做 String.format（否则会抛格式化异常）
     */
    @Override
    public String getErrText(Object... parameterArr) {
        return msg;
    }

    @Override
    public ErrCodeLevel getErrLevel() {
        return ErrCodeLevel.WARN;
    }

    /**
     * 业务类错误：HTTP 200 + status.code（bidr 惯例）
     */
    @Override
    public String getErrType() {
        return ErrCodeType.SYSTEM.getValue();
    }
}
