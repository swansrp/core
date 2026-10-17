package com.bidr.agent.runtime.client;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 平台错误 → 框架异常（D6 映射策略）：
 * <ul>
 * <li><b>会话语义类直通平台码</b>（前端按 SDK 口径处置：40402 透明新建、40403 本地收尾、40900/40403 取消视为已收尾）：40010 / 40401 / 40402 / 40403 / 40900 / 40901 / 42900 / 42901；</li>
 * <li><b>我方 Key / 平台内部类转内部错误</b>（40100 / 40101 / 40300 / 50000 / 50300 及未知码）：平台 Key 失效属我方配置问题，
 * 不能当用户登录态处理；平台原文仍进 details 便于排查。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/9/22
 */
public final class AgentRuntimeErrors {

    private static final Set<Integer> PASS_THROUGH = new HashSet<>(Arrays.asList(
            40010, 40401, 40402, 40403, 40900, 40901, 42900, 42901));

    public static final int CODE_SESSION_MISSING = 40402;
    public static final int CODE_TURN_MISSING = 40403;
    public static final int CODE_CONFLICT = 40900;

    private AgentRuntimeErrors() {
    }

    /** 平台信封错误 → 框架异常 */
    public static ServiceException of(Envelope<?> envelope) {
        int code = envelope == null || envelope.getCode() == null ? -1 : envelope.getCode();
        String msg = envelope == null ? null : envelope.getMsg();
        return of(code, msg);
    }

    public static ServiceException of(int code, String msg) {
        if (code == 401) {
            return keyRejected(msg);
        }
        String text = msg == null || msg.isEmpty() ? "Agent 平台返回失败" : msg;
        if (PASS_THROUGH.contains(code)) {
            // 码前缀进消息：框架 request 在 showErr=false 时只把 details 抛给前端（status.code 丢失），
            // 前端按 "[<code>] " 前缀解析平台码来做透明处置（40402 透明新建 / 40403 本地收尾 / 40900 视为已收尾）
            return new ServiceException(new AgentRuntimeErrorCode(code, "[" + code + "] " + text));
        }
        return new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                "Agent 平台调用失败（code=" + code + "）：" + text);
    }

    /** 未接线（地址/密钥未配置） */
    public static ServiceException notConfigured() {
        return new ServiceException(ErrCodeSys.SYS_CONFIG_NOT_EXIST,
                "Agent平台服务地址或密钥（系统参数 AGENT_RUNTIME_BASE_URL / AGENT_RUNTIME_API_KEY 或应用配置 my.agent.runtime.*）");
    }

    /**
     * 平台 401（密钥缺失/无效/停用）→ 内部错误 + 配置指引。
     * <p>
     * 注意：POST 带请求体时 JDK {@code HttpURLConnection} 遇 401 会抛
     * {@code HttpRetryException}（"cannot retry due to server authentication, in streaming mode"）而拿不到响应体，
     * 故这一类**按状态码语义兜底**而不是依赖信封内容——而平台用 401 表示的语义恰好只有"密钥问题"。
     */
    public static ServiceException keyRejected(String detail) {
        String text = detail == null || detail.isEmpty() ? "" : "（" + detail + "）";
        return new ServiceException(ErrCodeSys.SYS_ERR_MSG,
                "Agent平台密钥无效或已停用（HTTP 401）" + text
                        + "：请核对系统参数 AGENT_RUNTIME_API_KEY 或应用配置 my.agent.runtime.api-key");
    }

    /**
     * 判定异常链中是否为 401 拒绝（HttpRetryException / 消息含 401）
     */
    public static boolean isUnauthorized(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            if (current instanceof java.net.HttpRetryException) {
                return true;
            }
            String message = current.getMessage();
            String className = current.getClass().getName();
            if (message != null && (message.contains("401") || message.contains("UNAUTHORIZED"))
                    && (message.contains("authentication") || message.contains("Unauthorized")
                    || className.contains("HttpRetryException"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
