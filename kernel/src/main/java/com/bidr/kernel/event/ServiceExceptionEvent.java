package com.bidr.kernel.event;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.constant.err.ErrCodeType;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.HttpUtil;
import lombok.Getter;

import javax.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Title: ServiceExceptionEvent
 * Description: 服务异常事件，当 ServiceException 发生时发布此事件
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@Getter
public class ServiceExceptionEvent extends BaseEvent {

    /**
     * 异常对象
     */
    private final ServiceException exception;

    /**
     * 错误码
     */
    private final ErrCode errCode;

    /**
     * 错误级别
     */
    private final ErrCodeLevel errLevel;

    /**
     * 错误类型
     */
    private final String errType;

    /**
     * 错误消息
     */
    private final String errorMessage;

    /**
     * 堆栈信息
     */
    private final String stackTrace;

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
     * 请求参数
     */
    private String queryString;

    /**
     * 构造函数
     *
     * @param exception 服务异常
     */
    public ServiceExceptionEvent(ServiceException exception) {
        super(exception);
        this.exception = exception;
        this.errCode = exception.getErrCode();
        this.errLevel = exception.getErrCode().getErrLevel();
        this.errType = exception.getErrCode().getErrType();
        this.errorMessage = exception.getMessage();
        this.stackTrace = getStackTraceString(exception);
    }

    /**
     * 构造函数（包含请求信息）
     *
     * @param exception 服务异常
     * @param request   HTTP请求
     */
    public ServiceExceptionEvent(ServiceException exception, HttpServletRequest request) {
        this(exception);
        if (request != null) {
            this.requestUrl = request.getRequestURL().toString();
            this.requestMethod = request.getMethod();
            this.clientIp = HttpUtil.getRemoteIp(request);
            this.queryString = request.getQueryString();
        }
    }

    /**
     * 是否为严重错误（FATAL 或 ERROR 级别）
     */
    public boolean isSevere() {
        return errLevel == ErrCodeLevel.FATAL || errLevel == ErrCodeLevel.ERROR;
    }

    /**
     * 是否为业务异常
     */
    public boolean isBizException() {
        return ErrCodeType.BIZ.getValue().equals(errType);
    }

    /**
     * 获取堆栈信息字符串
     */
    private String getStackTraceString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
