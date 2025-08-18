package com.bidr.mcp.config;

import com.bidr.kernel.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.annotation.Param;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP请求日志切面
 *
 * @author ZhangFeihao
 * @since 2025/8/15 11:24
 */
@Aspect
@Component
@Slf4j
public class McpRequestLogAspect {
    /**
     * 切点：拦截所有带有@ToolMapping注解的方法
     */
    @Pointcut("@within(org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint)")
    public void mcpServerEndpoint() {
    }

    /**
     * 环绕通知：记录请求参数、执行时间和返回结果
     */
    @Around("mcpServerEndpoint()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 记录请求信息
        logRequestInfo(joinPoint);

        Object result = null;
        Throwable throwable = null;
        Object[] args = joinPoint.getArgs();

        try {
            if (args != null) {
                result = joinPoint.proceed(args);
            } else {
                result = joinPoint.proceed();
            }
            return result;
        } catch (Throwable ex) {
            throwable = ex;
            throw ex;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            // 记录响应信息
            logResponseInfo(joinPoint, result, throwable, costTime);
        }
    }

    private void logRequestInfo(JoinPoint joinPoint) {
        try {
            Map<String, Object> paramsMap = getMethodParams(joinPoint);
            StringBuffer requestMsg = buildMcpRequestMsg(joinPoint, paramsMap);
            log.info("=======>[MCP]{}", requestMsg);
        } catch (Exception e) {
            log.error("记录MCP请求信息时发生错误", e);
        }
    }

    /**
     * 记录响应信息
     */
    private void logResponseInfo(JoinPoint joinPoint, Object result, Throwable throwable, long costTime) {
        try {
            String methodName = joinPoint.getSignature().getName();
            if (throwable != null) {
                log.error("<=======[{}] 方法: {}, \n异常: {}", costTime, methodName, throwable.getMessage());
            } else {
                log.info("<=======[{}] 方法: {}, \n数据: {}", costTime, methodName,
                        JsonUtil.toJson(result, false, false, true));
            }
        } catch (Exception e) {
            log.error("记录MCP响应信息异常", e);
        }
    }

    /**
     * 获取方法参数名和参数值的映射
     */
    private Map<String, Object> getMethodParams(JoinPoint joinPoint) {
        Map<String, Object> paramsMap = new LinkedHashMap<>();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String paramName;

            // 优先使用@Param注解的description作为参数名
            Param paramAnnotation = parameter.getAnnotation(Param.class);
            if (paramAnnotation != null && !paramAnnotation.description().isEmpty()) {
                paramName = paramAnnotation.description();
            } else {
                paramName = parameter.getName();
            }

            paramsMap.put(paramName, args[i]);
        }

        return paramsMap;
    }

    private StringBuffer buildMcpRequestMsg(JoinPoint joinPoint, Map<String, Object> paramsMap) {
        StringBuffer sb = new StringBuffer();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        McpServerEndpoint annotation = targetClass.getAnnotation(McpServerEndpoint.class);
        sb.append(annotation.sseEndpoint()).append(" ").append(method.getName()).append("\n");
        sb.append(JsonUtil.toJson(paramsMap, false, false, true));
        return sb;
    }
}
