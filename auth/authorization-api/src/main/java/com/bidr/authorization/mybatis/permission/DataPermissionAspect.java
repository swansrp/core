package com.bidr.authorization.mybatis.permission;

import com.bidr.authorization.mybatis.anno.DataPermission;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Title: DataPermissionAspect
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/10/19 11:28
 */
@Slf4j
@Aspect
@Component
public class DataPermissionAspect {

    @Pointcut("@annotation(com.bidr.authorization.mybatis.anno.DataPermission)")
    public void dataPermission() {
    }

    @Before("dataPermission()")
    public void before(JoinPoint point) {
        DataPermission dataPermission = getDataPermission(point);
        DataPermissionHolder.set(dataPermission.value());
    }

    private DataPermission getDataPermission(JoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        return AnnotationUtils.findAnnotation(method, DataPermission.class);
    }

    @After("dataPermission()")
    public void after(JoinPoint point) {
        DataPermissionHolder.clear();
    }

    @AfterThrowing(pointcut = "dataPermission()", throwing = "e")
    public void afterThrow(JoinPoint point, Throwable e) {

    }

    @AfterReturning("dataPermission()")
    public void afterReturning() {
    }
}
