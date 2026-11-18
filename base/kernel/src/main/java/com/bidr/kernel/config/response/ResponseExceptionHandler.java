package com.bidr.kernel.config.response;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.constant.err.ErrCodeType;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.exception.RepeatSubmitException;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.PackageScanUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.MethodParameter;
import org.springframework.core.NestedExceptionUtils;
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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
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
        if (isClientAbort(ex)) {
            // 客户端传到一半就断开（点「停止上传」/刷新/断网）属正常客户端行为，不当服务端故障记 ERROR＋触发告警
            log.debug("客户端中止 multipart 请求传输: {}", ex.getMessage());
            return null;
        }
        log.error("", ex);
        // 发布异常事件
        publishExceptionEvent(ex);
        // msg 即原始异常信息，走单参构造避免与 cause 信息重复拼接
        ServiceException serviceException = new ServiceException(ex.getMessage());
        serviceException.initCause(ex);
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
     * multipart 解析阶段读请求体遇 EOF/连接重置＝客户端传到一半就断开，属正常客户端行为而非服务端故障。
     * 判定仅限请求解析阶段：业务代码抛出的 EOFException（如磁盘文件损坏）仍是服务端故障，照常告警。
     */
    private boolean isClientAbort(Throwable ex) {
        if (!(ex instanceof MultipartException)) {
            return false;
        }
        Throwable root = NestedExceptionUtils.getMostSpecificCause(ex);
        return root instanceof ClientAbortException
                || root instanceof EOFException
                || (root instanceof IOException && isAbortMessage(root.getMessage()));
    }

    private boolean isAbortMessage(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("broken pipe") || lower.contains("connection reset");
    }

    @ResponseBody
    @ExceptionHandler(RepeatSubmitException.class)
    public ResponseEntity<Response<String>> errorHandler(RepeatSubmitException ex) {
        // 拒绝类失败必须回错误信封：返回 void＝HTTP 200＋空 body，前端读不到 status 会判成"成功"（假成功）。
        // 有意不调 errorHandler(ServiceException)＝不发 ServiceExceptionEvent，免得客户端连点被当服务端故障触发告警邮件。
        ServiceException serviceException = new ServiceException(ex.getMessage());
        serviceException.setStackTrace(ex.getStackTrace());
        Response<String> res = new Response(serviceException);
        return new ResponseEntity<>(res, HttpStatus.OK);
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
                ServiceException serviceException = new ServiceException(ex.getMessage());
                serviceException.initCause(ex);
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
            return PackageScanUtil.inBasePackage(returnType.getDeclaringClass().getName(), basePackage) && !responseResultProperty.getClassWhiteList().contains(returnType.getDeclaringClass().getName());
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
