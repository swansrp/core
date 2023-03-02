/**
 * Bhfae.com Inc. Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.bidr.authorization.config.log;


import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.utils.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.http.fileupload.FileUploadBase;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * description 请求日志
 * 1. 导出接口api必须含有export
 *
 * @author sharuopeng
 * @version V1.0.0
 */
@Slf4j
@Component
public class LogFilter extends OncePerRequestFilter {

    private boolean isJsonResponse(HttpServletResponse response) {
        return response.getContentType().contains("json");
    }


    private String getOperator(HttpServletRequest request) {
        return request.getHeader(RequestConst.USER_ID);
    }

    private boolean isFileDownload(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getRequestURI().matches(".*/(export|captcha).*");
    }

    private boolean recordIgnore(HttpServletRequest httpServletRequest) {
        return httpServletRequest.getRequestURI().matches(".*/(actuator|export|webjars|v2|captcha.*|csrf|swagger.*).*");
    }

    private String extractResultPayload(MultiReadHttpServletResponse response) {
        byte[] buf = response.getBody();
        String payload = "";
        if (buf.length > 0) {
            try {
                payload = new String(buf, 0, buf.length, response.getCharacterEncoding());
            } catch (UnsupportedEncodingException ex) {
                payload = "[unknown]";
            }
        }
        return payload;
    }

    private String formatGetMsg(HttpServletRequest request, Map<String, String[]> parameterMap) {
        return buildRequestMsg(request, parameterMap).toString();
    }

    private String formatPostMsg(HttpServletRequest request, Map<String, String[]> parameterMap, String requestJson) {
        StringBuffer sb = buildRequestMsg(request, parameterMap);
        if (!StringUtils.isEmpty(requestJson)) {
            sb.append("\n");
            sb.append("[");
            sb.append(requestJson);
            sb.append("]");
        }
        return sb.toString();
    }

    private StringBuffer buildRequestMsg(HttpServletRequest request, Map<String, String[]> parameterMap) {
        StringBuffer sb = new StringBuffer();
        sb.append("=======>").append("[").append(request.getMethod()).append("]").append(request.getRequestURI());
        if (MapUtils.isNotEmpty(parameterMap)) {
            sb.append("?").append(request.getQueryString());
        }
        sb.append(" {");
        sb.append("token:").append(AuthTokenUtil.extractToken(request)).append(" operator:")
                .append(getOperator(request));
        sb.append("}");
        return sb;
    }

    private String formatGetRspMsg(String totalTime, String resultJson) {
        return "<=======" + "[" + totalTime + "]" + resultJson;
    }

    private String formatPostRspMsg(String totalTime, String resultJson) {
        return "<=======" + "[" + totalTime + "]" + resultJson;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String reqToken = RandomUtil.getUUID();
        MultiReadHttpServletRequest wrappedRequest;
        MultiReadHttpServletResponse wrappedResponse;

        StopWatch stopWatch = new StopWatch();
        String requestBodyStr = "";
        try {
            stopWatch.start();
            // 记录请求的消息体
            MDC.put("logToken", AuthTokenUtil.extractToken(request));
            MDC.put("reqToken", reqToken);
            MDC.put("URI", request.getRequestURI());
            MDC.put("method", request.getMethod());
            MDC.put("status", String.valueOf(response.getStatus()));

            HttpServletRequest temRequest = request;
            HttpServletResponse temResponse = response;
            boolean isMultipartContent = FileUploadBase.isMultipartContent(new ServletRequestContext(request));

            if (recordIgnore(request) || HttpMethod.OPTIONS.matches(request.getMethod())) {
                filterChain.doFilter(temRequest, temResponse);
                return;
            }

            if (!isMultipartContent) {
                wrappedRequest = new MultiReadHttpServletRequest(request);
                requestBodyStr = wrappedRequest.getBodyJsonStrByJson(wrappedRequest);
                temRequest = wrappedRequest;
            }
            if (!isFileDownload(request)) {
                wrappedResponse = new MultiReadHttpServletResponse(response);
                temResponse = wrappedResponse;
            }

            if (HttpMethod.GET.matches(request.getMethod())) {
                log.info(formatGetMsg(request, request.getParameterMap()));
            } else {
                log.info(formatPostMsg(request, request.getParameterMap(), requestBodyStr));
            }

            filterChain.doFilter(temRequest, temResponse);

            stopWatch.stop();
            MDC.put("totalTime", String.valueOf(stopWatch.getTotalTimeMillis()));
            // 记录响应的消息体
            String resultBodyStr;

            if (isJsonResponse(temResponse) && temResponse instanceof MultiReadHttpServletResponse) {
                resultBodyStr = extractResultPayload((MultiReadHttpServletResponse) temResponse);
                if (HttpMethod.GET.matches(request.getMethod())) {
                    log.info(formatGetRspMsg(String.valueOf(stopWatch.getTotalTimeMillis()), resultBodyStr));
                } else {
                    log.info(formatPostRspMsg(String.valueOf(stopWatch.getTotalTimeMillis()), resultBodyStr));
                }
            }


        } finally {
            MDC.remove("logToken");
            MDC.remove("reqToken");
            MDC.remove("URI");
            MDC.remove("totalTime");
            MDC.remove("method");
            MDC.remove("status");
        }
    }

}
