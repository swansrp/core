package com.bidr.authorization.annotation.data.scope;

import com.bidr.authorization.dao.repository.join.AcUserGroupJoinService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.mybatis.anno.DataPermission;
import com.bidr.authorization.mybatis.permission.DataPermissionHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Title: GroupDataScopeAspect
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/14 15:46
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class GroupDataScopeAspect {

    private final AcUserGroupJoinService acUserGroupJoinService;

    @Pointcut("@annotation(com.bidr.authorization.annotation.data.scope.GroupDataScope)")
    public void groupDataScope() {
    }

    @Before("groupDataScope()")
    public void before(JoinPoint point) {
        GroupDataScope groupDataScope = getGroupDataScope(point);
        String group = groupDataScope.value();
        String operator = AccountContext.getOperator();
        List<String> authorList = acUserGroupJoinService.getCustomerNumberListFromDataScope(operator, group);
        GroupDataScopeHolder.set(group, operator, authorList);
    }

    private GroupDataScope getGroupDataScope(JoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        return AnnotationUtils.findAnnotation(method, GroupDataScope.class);
    }

    @After("groupDataScope()")
    public void after(JoinPoint point) {
        GroupDataScopeHolder.clear();
    }

    @AfterThrowing(pointcut = "groupDataScope()", throwing = "e")
    public void afterThrow(JoinPoint point, Throwable e) {

    }

    @AfterReturning("groupDataScope()")
    public void afterReturning() {
    }
}
