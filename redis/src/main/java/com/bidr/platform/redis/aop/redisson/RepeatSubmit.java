package com.bidr.platform.redis.aop.redisson;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 防重复提交注解
 * <p>
 * 基于 X-Request-Id 请求头实现分布式锁，防止网络重试导致的重复提交
 * <p>
 * 使用示例：
 * <pre>
 * &#64;RepeatSubmit(interval = 30000)
 * public void submit(OrderDTO order) {
 *     // 业务逻辑
 * }
 * </pre>
 *
 * @author sharuopeng
 * @since 2026/03/25
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface RepeatSubmit {

    /**
     * 防重复时间窗口（毫秒）
     * <p>
     * 在此时间窗口内，相同的 request-id 会被认为是重复请求
     * 默认30秒
     */
    long interval() default 30 * 1000L;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;

    /**
     * 获取锁失败时的提示信息
     */
    String message() default "请求处理中，请勿重复提交";
}
