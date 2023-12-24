package com.bidr.kernel.config.response;

import cn.hutool.core.bean.BeanUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.constant.err.ErrCodeType;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.annotation.Resource;
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

    @Resource
    private ApplicationContext applicationContext;

    @Value("${bidr.result.format-bean}")
    private String externalFormatBeanName;

    @Value("${my.base-package}")
    private String basePackage;

    private static final Map<String, HttpStatus> STATUS_MAP = new HashMap<>(ErrCodeType.values().length);

    static {
        STATUS_MAP.put(ErrCodeType.SYSTEM.getValue(), HttpStatus.OK);
        STATUS_MAP.put(ErrCodeType.BIZ.getValue(), HttpStatus.OK);
        STATUS_MAP.put(ErrCodeType.AUTH.getValue(), HttpStatus.UNAUTHORIZED);
    }

    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public static ResponseEntity<Response<String>> errorHandler(Exception ex) {
        log.error("", ex);
        Response<String> res = new Response(new ServiceException(ErrCodeSys.SYS_ERR));
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return new ResponseEntity<>(res, status);
    }

    @ResponseBody
    @ExceptionHandler(value = NoticeException.class)
    public static ResponseEntity<Response<String>> errorHandler(NoticeException ex) {
        Response<String> res = new Response<>(null, ex.getMessage());
        HttpStatus status = HttpStatus.OK;
        return new ResponseEntity<>(res, status);
    }

    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public ResponseEntity<Response<String>> errorHandler(MethodArgumentNotValidException ex) {
        ServiceException serviceException = new ServiceException(ex);
        return errorHandler(serviceException);
    }

    @ResponseBody
    @ExceptionHandler(value = BindException.class)
    public ResponseEntity<Response<String>> errorHandler(BindException ex) {
        ServiceException serviceException = new ServiceException(ex);
        return errorHandler(serviceException);
    }

    @ResponseBody
    @ExceptionHandler(value = ServiceException.class)
    public static ResponseEntity<Response<String>> errorHandler(ServiceException ex) {
        log.warn("", ex);
        Response<String> res = new Response<>(ex);
        HttpStatus status = STATUS_MAP.getOrDefault(ex.getErrCode().getErrType(), HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(res, status);
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        if(!applicationContext.containsBean(externalFormatBeanName)) {
            return returnType.getDeclaringClass().getName().startsWith(basePackage);
        } else {
            return false;
        }
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        return body instanceof Response ? body : new Response<>(body);
    }
}
