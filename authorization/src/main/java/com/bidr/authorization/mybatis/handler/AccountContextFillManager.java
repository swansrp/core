package com.bidr.authorization.mybatis.handler;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import com.bidr.authorization.mybatis.anno.AutoFillOperator;
import com.bidr.authorization.mybatis.anno.AutoFillTimestamp;
import com.bidr.kernel.utils.FuncUtil;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;
import java.util.Set;

/**
 * Title: AccountContextFillManager
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/13 21:01
 */
@Configuration
public class AccountContextFillManager implements CommandLineRunner {

    private static final String DEFAULT_CREATE_BY = "createBy";
    private static final String DEFAULT_CREATE_AT = "createAt";
    private static final String DEFAULT_UPDATE_BY = "updateBy";
    private static final String DEFAULT_UPDATE_AT = "updateAt";
    @Value("${my.base-package}")
    private String basePackage;

    @Override
    public void run(String... args) {
        Set<Class<?>> entityClasses = scanEntityClasses();
        String createBy = DEFAULT_CREATE_BY;
        String updateBy = DEFAULT_UPDATE_BY;
        String createAt = DEFAULT_CREATE_AT;
        String updateAt = DEFAULT_UPDATE_AT;
        for (Class<?> entityClass : entityClasses) {
            AccountContextFill anno = entityClass.getAnnotation(AccountContextFill.class);
            if (anno != null) {
                createBy = anno.createBy();
                updateBy = anno.updateBy();
                createAt = anno.createAt();
                updateAt = anno.updateAt();
            }
            Field[] declaredFields = entityClass.getDeclaredFields();
            for (Field field : declaredFields) {
                String fieldName = field.getName();
                if (FuncUtil.equals(fieldName, createAt)) {
                    AccountContextFillHandler.registerAutoTimestampField(entityClass, field);
                } else if (FuncUtil.equals(fieldName, createBy)) {
                    AccountContextFillHandler.registerAutoOperatorField(entityClass, field);
                } else if (FuncUtil.equals(fieldName, updateAt)) {
                    AccountContextFillHandler.registerForceTimestampField(entityClass, field);
                } else if (FuncUtil.equals(fieldName, updateBy)) {
                    AccountContextFillHandler.registerForceOperatorField(entityClass, field);
                } else if (field.isAnnotationPresent(AutoFillTimestamp.class)) {
                    if (field.getAnnotation(AutoFillTimestamp.class).force()) {
                        AccountContextFillHandler.registerForceTimestampField(entityClass, field);
                    } else {
                        AccountContextFillHandler.registerAutoTimestampField(entityClass, field);
                    }
                } else if (field.isAnnotationPresent(AutoFillOperator.class)) {
                    if (field.getAnnotation(AutoFillOperator.class).force()) {
                        AccountContextFillHandler.registerForceOperatorField(entityClass, field);
                    } else {
                        AccountContextFillHandler.registerAutoOperatorField(entityClass, field);
                    }
                }
            }
        }
    }

    private Set<Class<?>> scanEntityClasses() {
        Reflections reflections = new Reflections(basePackage);
        return reflections.getTypesAnnotatedWith(TableName.class);
    }


}