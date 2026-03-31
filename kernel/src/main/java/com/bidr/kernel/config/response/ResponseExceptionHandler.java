package com.bidr.kernel.config.response;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.constant.err.ErrCodeType;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: ResponseExceptionHandler
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 15:54
 */
@Slf4j
@Order(-1)
@ControllerAdvice
public class ResponseExceptionHandler implements ResponseBodyAdvice<Object> {

    private static final Map<String, HttpStatus> STATUS_MAP = new HashMap<>(ErrCodeType.values().length);

    static {
        STATUS_MAP.put(ErrCodeType.SYSTEM.getValue(), HttpStatus.OK);
        STATUS_MAP.put(ErrCodeType.BIZ.getValue(), HttpStatus.OK);
        STATUS_MAP.put(ErrCodeType.AUTH.getValue(), HttpStatus.UNAUTHORIZED);
    }

    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private ResponseResultProperty responseResultProperty;
    @Value("${my.base-package}")
    private String basePackage;

    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<Response<String>> errorHandler(Exception ex) {
        log.error("", ex);
        // 发布异常事件
        publishExceptionEvent(ex);
        ServiceException serviceException = new ServiceException(ex.getMessage(), ex);
        serviceException.setErrCode(ErrCodeSys.SYS_ERR);
        // 保留原始调用栈
        serviceException.setStackTrace(ex.getStackTrace());
        Response<String> res = new Response(serviceException);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(res, status);
    }

    @ResponseBody
    @ExceptionHandler(value = NoticeException.class)
    public ResponseEntity<Response<Object>> errorHandler(NoticeException ex) {
        Response<Object> res = new Response<>(ex.getObj(), ex.getMessage());
        HttpStatus status = HttpStatus.OK;
        return new ResponseEntity<>(res, status);
    }

    @ResponseBody
    @ExceptionHandler(value = ServiceException.class)
    public ResponseEntity<Response<String>> errorHandler(ServiceException ex) {
        ErrCodeLevel.log(log, ex.getErrCode().getErrLevel(), ex);
        // 发布异常事件
        publishExceptionEvent(ex);
        Response<String> res = new Response<>(ex);
        HttpStatus status = STATUS_MAP.getOrDefault(ex.getErrCode().getErrType(), HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(res, status);
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException e) {
        // 客户端断开连接，不处理
    }

    /**
     * 发布异常事件，供业务模块监听处理
     */
    private void publishExceptionEvent(Exception ex) {
        try {
            HttpServletRequest request = null;
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                request = attributes.getRequest();
            }

            if (ex instanceof ServiceException) {
                applicationContext.publishEvent(new ServiceExceptionEvent((ServiceException) ex, request));
            } else {
                // 其他异常包装为 ServiceException 事件
                ServiceException serviceException = new ServiceException(ex.getMessage(), ex);
                serviceException.setErrCode(ErrCodeSys.SYS_ERR);
                // 保留原始调用栈
                serviceException.setStackTrace(ex.getStackTrace());
                applicationContext.publishEvent(new ServiceExceptionEvent(serviceException, request));
            }
        } catch (Exception e) {
            log.debug("发布异常事件失败", e);
        }
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<Response<String>> errorHandler(MethodArgumentNotValidException ex) {
        ServiceException serviceException = new ServiceException(ex);
        // 保留原始调用栈
        serviceException.setStackTrace(ex.getStackTrace());
        return errorHandler(serviceException);
    }

    @ResponseBody
    @ExceptionHandler(value = BindException.class)
    public ResponseEntity<Response<String>> errorHandler(BindException ex) {
        ServiceException serviceException = new ServiceException(ex);
        // 保留原始调用栈
        serviceException.setStackTrace(ex.getStackTrace());
        return errorHandler(serviceException);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if (!applicationContext.containsBean(responseResultProperty.getFormatBean())) {
            return returnType.getDeclaringClass().getName().startsWith(basePackage) && !responseResultProperty.getClassWhiteList().contains(returnType.getDeclaringClass().getName());
        } else {
            return false;
        }
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        Response<?> res = body instanceof Response ? (Response<?>) body : new Response<>(body);
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(selectedConverterType) ? res : JsonUtil.toJson(res, false, false, true);
    }
}
