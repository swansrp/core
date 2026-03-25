package com.bidr.platform.redis.aop.redisson;

/**
 * 防重复提交异常
 * <p>
 * 当检测到重复请求时抛出此异常
 *
 * @author sharuopeng
 * @since 2026/03/25
 */
public class RepeatSubmitException extends RuntimeException {

    public RepeatSubmitException(String message) {
        super(message);
    }

    public RepeatSubmitException(String message, Throwable cause) {
        super(message, cause);
    }
}
