package com.bidr.authorization.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.mybatis.handler.MetaObjectHandlerManager;
import com.bidr.kernel.mybatis.intercept.ExecutorUpdateIntercept;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Title: AccountContextFillHandler
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/28 13:11
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountContextFillHandler implements MetaObjectHandler {
    private final static Set<Class<?>> AUTO_FILL_CLASS = new HashSet<>();
    private final static Map<Class<?>, Set<Field>> AUTO_TIMESTAMP_MAP = new HashMap<>();
    private final static Map<Class<?>, Set<Field>> AUTO_OPERATOR_MAP = new HashMap<>();
    private final static Map<Class<?>, Set<Field>> FORCE_TIMESTAMP_MAP = new HashMap<>();
    private final static Map<Class<?>, Set<Field>> FORCE_OPERATOR_MAP = new HashMap<>();

    @Lazy
    @Autowired
    private MetaObjectHandlerManager metaObjectHandlerManager;

    public static void registerAutoTimestampField(Class<?> entityClass, Field field) {
        AUTO_FILL_CLASS.add(entityClass);
        AUTO_TIMESTAMP_MAP.computeIfAbsent(entityClass, k -> new HashSet<>()).add(field);
    }

    public static void registerAutoOperatorField(Class<?> entityClass, Field field) {
        AUTO_FILL_CLASS.add(entityClass);
        AUTO_OPERATOR_MAP.computeIfAbsent(entityClass, k -> new HashSet<>()).add(field);
    }

    public static void registerForceTimestampField(Class<?> entityClass, Field field) {
        AUTO_FILL_CLASS.add(entityClass);
        FORCE_TIMESTAMP_MAP.computeIfAbsent(entityClass, k -> new HashSet<>()).add(field);
    }

    public static void registerForceOperatorField(Class<?> entityClass, Field field) {
        AUTO_FILL_CLASS.add(entityClass);
        FORCE_OPERATOR_MAP.computeIfAbsent(entityClass, k -> new HashSet<>()).add(field);
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        Class<?> clazz = metaObject.getOriginalObject().getClass();
        if (AUTO_FILL_CLASS.contains(clazz)) {
            autoFill(metaObject, clazz);
        }
    }

    private void autoFill(MetaObject metaObject, Class<?> clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            String fieldName = field.getName();
            String operator = AccountContext.getOperator();
            if (FuncUtil.isNotEmpty(operator)) {
                if (AUTO_OPERATOR_MAP.getOrDefault(clazz, new HashSet<>()).contains(field)) {
                    this.fillStrategy(metaObject, fieldName, operator);
                }
                if (FORCE_OPERATOR_MAP.getOrDefault(clazz, new HashSet<>()).contains(field)) {
                    metaObject.setValue(fieldName, operator);
                }
            }
            if (AUTO_TIMESTAMP_MAP.getOrDefault(clazz, new HashSet<>()).contains(field)) {
                if (field.getType().equals(Date.class)) {
                    this.fillStrategy(metaObject, fieldName, new Date());
                } else if (field.getType().equals(LocalDateTime.class)) {
                    this.fillStrategy(metaObject, fieldName, LocalDateTime.now());
                }
            }
            if (FORCE_TIMESTAMP_MAP.getOrDefault(clazz, new HashSet<>()).contains(field)) {
                if (field.getType().equals(Date.class)) {
                    metaObject.setValue(fieldName, new Date());
                } else if (field.getType().equals(LocalDateTime.class)) {
                    metaObject.setValue(fieldName, LocalDateTime.now());
                }
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Class<?> clazz = metaObject.getOriginalObject().getClass();
        if (AUTO_FILL_CLASS.contains(clazz)) {
            autoFill(metaObject, clazz);
        }
    }


}


