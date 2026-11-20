package com.bidr.mcp.client;

/**
 * Title: McpToolException
 * Description: MCP 工具调用异常。区分「可重试」（传输/超时/SSE 断流重试仍失败）与
 * 「不可重试」（配置缺失、业务错误、工具返回 isError）；{@link #getUserMessage()} 为
 * 面向用户/模型的可读文案，不含凭证与内部堆栈信息。
 *
 * @author sharp
 * @since 2026/11/20
 */
public class McpToolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 是否可重试
     */
    private final boolean retryable;

    /**
     * 面向用户的可读文案
     */
    private final String userMessage;

    public McpToolException(String userMessage, boolean retryable) {
        this(userMessage, retryable, null);
    }

    public McpToolException(String userMessage, boolean retryable, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
        this.retryable = retryable;
    }

    /**
     * @return true＝可重试（传输类错误且自动重试一次仍失败）
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * @return 面向用户/模型的可读文案
     */
    public String getUserMessage() {
        return userMessage;
    }
}