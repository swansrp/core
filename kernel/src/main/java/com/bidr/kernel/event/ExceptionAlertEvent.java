package com.bidr.kernel.event;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

/**
 * Title: ExceptionAlertEvent
 * Description: 异常告警事件，由 @ExceptionAlert 注解触发，包含完整的异常告警信息
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Getter
@Setter
public class ExceptionAlertEvent extends BaseEvent {

    /**
     * 异常描述
     */
    private final String description;

    /**
     * 异常严重级别
     */
    private final ErrCodeLevel severity;

    /**
     * 异常对象
     */
    private final Throwable throwable;

    /**
     * 异常类型（类名）
     */
    private final String exceptionType;

    /**
     * 异常消息
     */
    private final String exceptionMessage;

    /**
     * 堆栈信息
     */
    private final String stackTrace;
    /**
     * 发生异常的类名
     */
    private final String className;
    /**
     * 发生异常的方法名
     */
    private final String methodName;
    /**
     * 方法参数
     */
    private final Object[] args;
    /**
     * 是否包含方法参数
     */
    private final boolean includeArgs;
    /**
     * 是否包含堆栈信息
     */
    private final boolean includeStackTrace;
    /**
     * 指定的通知邮箱（可为空，使用系统默认配置）
     */
    private final String notifyEmails;
    /**
     * 指定的通知微信接收人（可为空，使用系统默认配置）
     */
    private final String notifyWx;
    /**
     * 堆栈深度限制
     */
    private int stackTraceDepth;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 用户名
     */
    private String userName;
    /**
     * 姓名
     */
    private String name;
    /**
     * 客户编号
     */
    private String customerNumber;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 手机号
     */
    private String phoneNumber;
    /**
     * 请求URL
     */
    private String requestUrl;
    /**
     * 请求方法
     */
    private String requestMethod;
    /**
     * 客户端IP
     */
    private String clientIp;
    /**
     * 查询参数
     */
    private String queryString;

    /**
     * 构造函数
     *
     * @param builder 事件构建器
     */
    public ExceptionAlertEvent(Builder builder) {
        super(builder.source, "ExceptionAlert");
        this.description = builder.description;
        this.severity = builder.severity;
        this.throwable = builder.throwable;
        this.exceptionType = builder.throwable != null ? builder.throwable.getClass().getName() : null;
        this.exceptionMessage = builder.throwable != null ? builder.throwable.getMessage() : null;
        this.stackTrace = builder.stackTrace;
        this.stackTraceDepth = builder.stackTraceDepth;
        this.className = builder.className;
        this.methodName = builder.methodName;
        this.args = builder.args;
        this.includeArgs = builder.includeArgs;
        this.includeStackTrace = builder.includeStackTrace;
        this.userId = builder.userId;
        this.userName = builder.userName;
        this.name = builder.name;
        this.customerNumber = builder.customerNumber;
        this.email = builder.email;
        this.phoneNumber = builder.phoneNumber;
        this.requestUrl = builder.requestUrl;
        this.requestMethod = builder.requestMethod;
        this.clientIp = builder.clientIp;
        this.queryString = builder.queryString;
        this.notifyEmails = builder.notifyEmails;
        this.notifyWx = builder.notifyWx;
    }

    /**
     * 获取堆栈信息（按深度限制截取）
     */
    public String getLimitedStackTrace() {
        if (stackTrace == null || stackTraceDepth <= 0) {
            return stackTrace;
        }
        String[] lines = stackTrace.split("\n");
        if (lines.length <= stackTraceDepth) {
            return stackTrace;
        }
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < stackTraceDepth; i++) {
            limited.append(lines[i]).append("\n");
        }
        limited.append("... ").append(lines.length - stackTraceDepth).append(" more lines omitted");
        return limited.toString();
    }

    /**
     * 获取方法参数字符串表示
     */
    public String getArgsString() {
        if (args == null || args.length == 0) {
            return null;
        }
        return Arrays.toString(args);
    }

    /**
     * 是否包含用户信息
     */
    public boolean hasUserInfo() {
        return userId != null || userName != null || name != null || customerNumber != null || email != null || phoneNumber != null;
    }

    /**
     * 是否包含请求信息
     */
    public boolean hasRequestInfo() {
        return requestUrl != null || requestMethod != null || clientIp != null || queryString != null;
    }

    /**
     * 事件构建器
     */
    public static class Builder {
        private Object source;
        private String description;
        private ErrCodeLevel severity = ErrCodeLevel.ERROR;
        private Throwable throwable;
        private String stackTrace;
        private int stackTraceDepth = 50;
        private String className;
        private String methodName;
        private Object[] args;
        private boolean includeArgs = true;
        private boolean includeStackTrace = true;
        private Long userId;
        private String userName;
        private String name;
        private String customerNumber;
        private String email;
        private String phoneNumber;
        private String requestUrl;
        private String requestMethod;
        private String clientIp;
        private String queryString;
        private String notifyEmails;
        private String notifyWx;

        public Builder source(Object source) {
            this.source = source;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder severity(ErrCodeLevel severity) {
            this.severity = severity;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
            return this;
        }

        public Builder stackTraceDepth(int stackTraceDepth) {
            this.stackTraceDepth = stackTraceDepth;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        public Builder args(Object[] args) {
            this.args = args;
            return this;
        }

        public Builder includeArgs(boolean includeArgs) {
            this.includeArgs = includeArgs;
            return this;
        }

        public Builder includeStackTrace(boolean includeStackTrace) {
            this.includeStackTrace = includeStackTrace;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder userName(String userName) {
            this.userName = userName;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder customerNumber(String customerNumber) {
            this.customerNumber = customerNumber;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder requestUrl(String requestUrl) {
            this.requestUrl = requestUrl;
            return this;
        }

        public Builder requestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder queryString(String queryString) {
            this.queryString = queryString;
            return this;
        }

        public Builder notifyEmails(String notifyEmails) {
            this.notifyEmails = notifyEmails;
            return this;
        }

        public Builder notifyWx(String notifyWx) {
            this.notifyWx = notifyWx;
            return this;
        }

        public ExceptionAlertEvent build() {
            return new ExceptionAlertEvent(this);
        }
    }
}
