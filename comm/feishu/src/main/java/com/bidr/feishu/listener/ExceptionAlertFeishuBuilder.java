package com.bidr.feishu.listener;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.utils.FuncUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Title: ExceptionAlertFeishuBuilder
 * Description: 飞书异常告警内容构建器（对标 ExceptionAlertWxBuilder），输出 lark_md 格式
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
public class ExceptionAlertFeishuBuilder {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private final StringBuilder sb = new StringBuilder();

    private ExceptionAlertFeishuBuilder() {
    }

    public static ExceptionAlertFeishuBuilder create() {
        return new ExceptionAlertFeishuBuilder();
    }

    /**
     * 追加严重级别标题行
     */
    public ExceptionAlertFeishuBuilder severity(ErrCodeLevel severity) {
        sb.append("**级别**: ").append(severity == null ? "UNKNOWN" : severity.name()).append("\n");
        return this;
    }

    /**
     * 追加项目/模块信息
     */
    public ExceptionAlertFeishuBuilder project(String projectId, String moduleId) {
        if (FuncUtil.isNotEmpty(projectId)) {
            sb.append("**项目**: ").append(projectId).append("\n");
        }
        if (FuncUtil.isNotEmpty(moduleId)) {
            sb.append("**模块**: ").append(moduleId).append("\n");
        }
        return this;
    }

    /**
     * 追加发生时间
     */
    public ExceptionAlertFeishuBuilder occurredTime(LocalDateTime occurredTime) {
        sb.append("**时间**: ").append(DateTimeFormatter.ofPattern(DATE_FORMAT).format(occurredTime)).append("\n");
        return this;
    }

    /**
     * 追加错误码信息
     */
    public ExceptionAlertFeishuBuilder errCode(String errCode, String errName, String errorMessage) {
        if (FuncUtil.isNotEmpty(errCode)) {
            sb.append("**错误码**: ").append(errCode);
            if (FuncUtil.isNotEmpty(errName)) {
                sb.append(" (").append(errName).append(")");
            }
            sb.append("\n");
        }
        if (FuncUtil.isNotEmpty(errorMessage)) {
            sb.append("**错误信息**: ").append(errorMessage).append("\n");
        }
        return this;
    }

    /**
     * 追加异常类型与消息
     */
    public ExceptionAlertFeishuBuilder exception(String exceptionType, String exceptionMessage) {
        if (FuncUtil.isNotEmpty(exceptionType)) {
            sb.append("**异常类型**: ").append(exceptionType).append("\n");
        }
        if (FuncUtil.isNotEmpty(exceptionMessage)) {
            sb.append("**异常消息**: ").append(exceptionMessage).append("\n");
        }
        return this;
    }

    /**
     * 追加描述与位置信息
     */
    public ExceptionAlertFeishuBuilder location(String description, String className, String methodName) {
        if (FuncUtil.isNotEmpty(description)) {
            sb.append("**描述**: ").append(description).append("\n");
        }
        if (FuncUtil.isNotEmpty(className)) {
            sb.append("**位置**: ").append(className);
            if (FuncUtil.isNotEmpty(methodName)) {
                sb.append("#").append(methodName);
            }
            sb.append("\n");
        }
        return this;
    }

    /**
     * 追加用户信息
     */
    public ExceptionAlertFeishuBuilder userInfo(String userName, String name, String customerNumber) {
        StringBuilder userInfo = new StringBuilder();
        if (FuncUtil.isNotEmpty(name)) {
            userInfo.append(name);
        }
        if (FuncUtil.isNotEmpty(userName)) {
            if (userInfo.length() > 0) {
                userInfo.append("/");
            }
            userInfo.append(userName);
        }
        if (FuncUtil.isNotEmpty(customerNumber)) {
            if (userInfo.length() > 0) {
                userInfo.append("/");
            }
            userInfo.append(customerNumber);
        }
        if (userInfo.length() > 0) {
            sb.append("**用户**: ").append(userInfo).append("\n");
        }
        return this;
    }

    /**
     * 追加请求信息
     */
    public ExceptionAlertFeishuBuilder requestInfo(String requestUrl, String requestMethod, String clientIp,
                                                   String queryString) {
        if (FuncUtil.isNotEmpty(requestUrl)) {
            sb.append("**请求**: ").append(FuncUtil.isEmpty(requestMethod) ? "" : requestMethod + " ")
                    .append(requestUrl).append("\n");
        }
        if (FuncUtil.isNotEmpty(queryString)) {
            sb.append("**参数**: ").append(queryString).append("\n");
        }
        if (FuncUtil.isNotEmpty(clientIp)) {
            sb.append("**IP**: ").append(clientIp).append("\n");
        }
        return this;
    }

    /**
     * 追加堆栈信息（按深度限制截取）
     */
    public ExceptionAlertFeishuBuilder stackTrace(String stackTrace, int stackDepth) {
        if (FuncUtil.isNotEmpty(stackTrace)) {
            sb.append("**堆栈**:\n```\n").append(limitStackTrace(stackTrace, stackDepth)).append("\n```\n");
        }
        return this;
    }

    /**
     * 按深度限制截取堆栈
     */
    private String limitStackTrace(String stackTrace, int stackDepth) {
        if (stackDepth <= 0) {
            return stackTrace;
        }
        String[] lines = stackTrace.split("\n");
        if (lines.length <= stackDepth) {
            return stackTrace;
        }
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < stackDepth; i++) {
            limited.append(lines[i]).append("\n");
        }
        limited.append("... ").append(lines.length - stackDepth).append(" more lines omitted");
        return limited.toString();
    }

    public String build() {
        return sb.toString();
    }
}
