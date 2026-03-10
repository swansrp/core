package com.bidr.email.listener;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

/**
 * Title: ExceptionAlertEmailBuilder
 * Description: 异常告警邮件内容构建器
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Slf4j
public class ExceptionAlertEmailBuilder {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StringBuilder content = new StringBuilder();
    private ErrCodeLevel errCodeLevel;
    private String severityName;

    private ExceptionAlertEmailBuilder() {
    }

    /**
     * 创建构建器
     */
    public static ExceptionAlertEmailBuilder create() {
        return new ExceptionAlertEmailBuilder();
    }

    /**
     * 设置严重级别（枚举形式）
     */
    public ExceptionAlertEmailBuilder severity(ErrCodeLevel level) {
        this.errCodeLevel = level;
        this.severityName = level.name();
        return this;
    }

    /**
     * 添加HTML头部和样式（根据错误级别动态生成）
     */
    public ExceptionAlertEmailBuilder appendHeader() {
        AlertStyle style = getAlertStyle();

        content.append("<html><head><style>");
        content.append("body{font-family:Arial,sans-serif;line-height:1.6;color:#333;max-width:800px;margin:0 auto;}");
        // 标题样式
        content.append("h3{color:").append(style.titleColor).append(";border-bottom:2px solid ").append(style.borderColor).append(";padding-bottom:10px;" +
                "margin-bottom:20px;}");
        if (style.titleBold) {
            content.append("h3{font-weight:bold;}");
        }
        // section样式
        content.append(".section{margin-bottom:15px;padding:15px;background:").append(style.sectionBg).append(";border-left:4px solid ").append(style.borderColor).append(";border-radius:4px;}");
        content.append(".label{font-weight:bold;color:#555;}");
        content.append("pre{background:#f5f5f5;padding:10px;border-radius:5px;overflow-x:auto;font-size:12px;white-space:pre-wrap;word-wrap:break-word;}");
        // 级别徽章
        content.append(".badge{display:inline-block;padding:4px 8px;border-radius:4px;font-size:12px;font-weight:bold;color:white;background:").append(style.badgeColor).append(";}");
        content.append("</style></head><body>");
        return this;
    }

    /**
     * 添加标题（带级别图标）
     */
    public ExceptionAlertEmailBuilder appendTitle() {
        AlertStyle style = getAlertStyle();
        content.append("<h3>")
                .append(style.icon).append(" 系统异常告警")
                .append(" <span class='badge'>").append(severityName).append("</span>")
                .append("</h3>");
        return this;
    }

    /**
     * 添加异常时间
     */
    public ExceptionAlertEmailBuilder appendOccurredTime(LocalDateTime occurredTime) {
        content.append("<div class='section'><p><span class='label'>异常时间：</span>")
                .append(occurredTime != null ? occurredTime.format(DATE_TIME_FORMATTER) : LocalDateTime.now().format(DATE_TIME_FORMATTER))
                .append("</p>");
        return this;
    }

    /**
     * 添加异常描述
     */
    public ExceptionAlertEmailBuilder appendDescription(String description) {
        if (FuncUtil.isNotEmpty(description)) {
            content.append("<p><span class='label'>异常描述：</span>").append(escapeHtml(description)).append("</p>");
        }
        return this;
    }

    /**
     * 添加错误码信息
     */
    public ExceptionAlertEmailBuilder appendErrCode(Integer errCode, String errName, String errMsg) {
        if (errCode != null) {
            content.append("<p><span class='label'>错误码：</span>").append(errCode).append("</p>");
        }
        if (FuncUtil.isNotEmpty(errName)) {
            content.append("<p><span class='label'>错误名称：</span>").append(errName).append("</p>");
        }
        if (FuncUtil.isNotEmpty(errMsg)) {
            content.append("<p><span class='label'>错误消息：</span>").append(escapeHtml(errMsg)).append("</p>");
        }
        return this;
    }

    /**
     * 结束当前section
     */
    public ExceptionAlertEmailBuilder endSection() {
        content.append("</div>");
        return this;
    }

    /**
     * 添加用户信息（从参数获取）
     */
    public ExceptionAlertEmailBuilder appendUserInfo(Long userId, String userName, String name,
                                                     String customerNumber, String email, String phoneNumber) {
        boolean hasInfo = userId != null
                || FuncUtil.isNotEmpty(userName)
                || FuncUtil.isNotEmpty(name)
                || FuncUtil.isNotEmpty(customerNumber)
                || FuncUtil.isNotEmpty(email)
                || FuncUtil.isNotEmpty(phoneNumber);
        if (!hasInfo) {
            return this;
        }
        content.append("<div class='section'><p><span class='label'>用户信息：</span></p>");
        if (userId != null) {
            content.append("<p>用户ID：").append(userId).append("</p>");
        }
        if (FuncUtil.isNotEmpty(userName)) {
            content.append("<p>用户名：").append(escapeHtml(userName)).append("</p>");
        }
        if (FuncUtil.isNotEmpty(name)) {
            content.append("<p>姓名：").append(escapeHtml(name)).append("</p>");
        }
        if (FuncUtil.isNotEmpty(customerNumber)) {
            content.append("<p>客户编号：").append(escapeHtml(customerNumber)).append("</p>");
        }
        if (FuncUtil.isNotEmpty(email)) {
            content.append("<p>邮箱：").append(escapeHtml(email)).append("</p>");
        }
        if (FuncUtil.isNotEmpty(phoneNumber)) {
            content.append("<p>手机号：").append(escapeHtml(phoneNumber)).append("</p>");
        }
        content.append("</div>");
        return this;
    }

    /**
     * 添加请求信息（从参数获取）
     */
    public ExceptionAlertEmailBuilder appendRequestInfo(String requestUrl, String requestMethod, String clientIp, String queryString) {
        if (FuncUtil.isNotEmpty(requestUrl)) {
            content.append("<div class='section'><p><span class='label'>请求信息：</span></p>");
            content.append("<p>请求URL：").append(escapeHtml(requestUrl)).append("</p>");
            content.append("<p>请求方法：").append(requestMethod != null ? requestMethod : "").append("</p>");
            content.append("<p>客户端IP：").append(escapeHtml(clientIp)).append("</p>");
            if (FuncUtil.isNotEmpty(queryString)) {
                content.append("<p>查询参数：").append(escapeHtml(queryString)).append("</p>");
            }
            content.append("</div>");
        }
        return this;
    }

    /**
     * 添加方法信息
     */
    public ExceptionAlertEmailBuilder appendMethodInfo(String className, String methodName, Object[] args) {
        content.append("<div class='section'><p><span class='label'>异常位置：</span>")
                .append(escapeHtml(className)).append(".").append(escapeHtml(methodName)).append("</p>");
        if (args != null && args.length > 0) {
            content.append("<p><span class='label'>方法参数：</span></p>");
            content.append("<pre>").append(escapeHtml(Arrays.toString(args))).append("</pre>");
        }
        content.append("</div>");
        return this;
    }

    /**
     * 添加异常信息
     */
    public ExceptionAlertEmailBuilder appendExceptionInfo(String exceptionType, String exceptionMessage) {
        content.append("<div class='section'>");
        if (FuncUtil.isNotEmpty(exceptionType)) {
            content.append("<p><span class='label'>异常类型：</span>").append(exceptionType).append("</p>");
        }
        if (exceptionMessage != null) {
            content.append("<p><span class='label'>异常信息：</span>").append(escapeHtml(exceptionMessage)).append("</p>");
        }
        content.append("</div>");
        return this;
    }

    /**
     * 添加堆栈信息
     */
    public ExceptionAlertEmailBuilder appendStackTrace(Throwable e, int maxDepth) {
        content.append("<div class='section'><p><span class='label'>堆栈信息：</span></p>");
        content.append("<pre>").append(escapeHtml(getStackTraceString(e, maxDepth))).append("</pre></div>");
        return this;
    }

    /**
     * 添加堆栈信息（字符串形式，带行数限制）
     */
    public ExceptionAlertEmailBuilder appendStackTrace(String stackTrace, int maxLines) {
        content.append("<div class='section'><p><span class='label'>堆栈信息：</span></p>");
        content.append("<pre>").append(escapeHtml(limitStackTrace(stackTrace, maxLines))).append("</pre></div>");
        return this;
    }

    /**
     * 添加堆栈信息（字符串形式，已处理行数限制）
     */
    public ExceptionAlertEmailBuilder appendStackTrace(String stackTrace) {
        content.append("<div class='section'><p><span class='label'>堆栈信息：</span></p>");
        content.append("<pre>").append(escapeHtml(stackTrace)).append("</pre></div>");
        return this;
    }

    /**
     * 添加HTML尾部
     */
    public ExceptionAlertEmailBuilder appendFooter() {
        content.append("</body></html>");
        return this;
    }

    /**
     * 构建最终内容
     */
    public String build() {
        return content.toString();
    }

    /**
     * 根据错误级别获取样式配置
     */
    private AlertStyle getAlertStyle() {
        if (errCodeLevel == null) {
            errCodeLevel = ErrCodeLevel.ERROR;
        }
        switch (errCodeLevel) {
            case FATAL:
                // 最严重错误：深红色，标题加粗
                return new AlertStyle(
                        "🔴",
                        "#a94442",
                        "#a94442",
                        "#f2dede",
                        "#a94442",
                        true
                );
            case ERROR:
                // 普通错误：红色
                return new AlertStyle(
                        "❌",
                        "#d9534f",
                        "#d9534f",
                        "#fdf7f7",
                        "#d9534f",
                        false
                );
            case WARN:
                // 警告：橙黄色
                return new AlertStyle(
                        "⚠️",
                        "#f0ad4e",
                        "#f0ad4e",
                        "#fcf8e3",
                        "#f0ad4e",
                        false
                );
            case INFO:
                // 信息：蓝色
                return new AlertStyle(
                        "ℹ️",
                        "#31708f",
                        "#31708f",
                        "#d9edf7",
                        "#31708f",
                        false
                );
            case DEBUG:
            case TRACE:
                // 调试/追踪：灰色
                return new AlertStyle(
                        "🔧",
                        "#777777",
                        "#777777",
                        "#f5f5f5",
                        "#777777",
                        false
                );
            case HIDE:
            default:
                // 默认/隐藏：深灰色
                return new AlertStyle(
                        "📌",
                        "#555555",
                        "#555555",
                        "#f9f9f9",
                        "#555555",
                        false
                );
        }
    }

    /**
     * 告警样式配置类
     */
    private static class AlertStyle {
        // 图标
        final String icon;
        // 标题颜色
        final String titleColor;
        // 边框颜色
        final String borderColor;
        // 背景颜色
        final String sectionBg;
        // 徽章颜色
        final String badgeColor;
        // 标题是否加粗
        final boolean titleBold;

        AlertStyle(String icon, String titleColor, String borderColor, String sectionBg, String badgeColor, boolean titleBold) {
            this.icon = icon;
            this.titleColor = titleColor;
            this.borderColor = borderColor;
            this.sectionBg = sectionBg;
            this.badgeColor = badgeColor;
            this.titleBold = titleBold;
        }
    }

    /**
     * HTML转义
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 获取异常堆栈信息字符串
     */
    private static String getStackTraceString(Throwable e, int maxDepth) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();

        return limitStackTrace(stackTrace, maxDepth);
    }

    /**
     * 限制堆栈深度
     */
    private static String limitStackTrace(String stackTrace, int maxLines) {
        if (stackTrace == null) {
            return "";
        }
        if (maxLines <= 0) {
            return stackTrace;
        }
        String[] lines = stackTrace.split("\n");
        if (lines.length <= maxLines) {
            return stackTrace;
        }
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            limited.append(lines[i]).append("\n");
        }
        limited.append("... ").append(lines.length - maxLines).append(" more lines omitted");
        return limited.toString();
    }
}
