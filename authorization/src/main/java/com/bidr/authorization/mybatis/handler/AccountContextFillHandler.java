package com.bidr.authorization.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Date;

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

    @Override
    public void insertFill(MetaObject metaObject) {
        Object classObj = metaObject.getOriginalObject();
        AccountContextFill contextFill = classObj.getClass().getAnnotation(AccountContextFill.class);
        if (contextFill != null) {
            Field[] declaredFields = classObj.getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                String fieldName = field.getName();
                Long userId = AccountContext.getUserId();
                if (FuncUtil.isNotEmpty(userId)) {
                    if (FuncUtil.equals(fieldName, contextFill.createBy())) {
                        this.fillStrategy(metaObject, fieldName, userId);
                    } else if (FuncUtil.equals(fieldName, contextFill.updateBy())) {
                        this.fillStrategy(metaObject, fieldName, userId);
                    }
                }
                if (FuncUtil.equals(fieldName, contextFill.createAt())) {
                    if (field.getType().equals(Date.class)) {
                        this.fillStrategy(metaObject, fieldName, new Date());
                    } else if (field.getType().equals(LocalDateTime.class)) {
                        this.fillStrategy(metaObject, fieldName, LocalDateTime.now());
                    }
                } else if (FuncUtil.equals(fieldName, contextFill.updateAt())) {
                    if (field.getType().equals(Date.class)) {
                        this.fillStrategy(metaObject, fieldName, new Date());
                    } else if (field.getType().equals(LocalDateTime.class)) {
                        this.fillStrategy(metaObject, fieldName, LocalDateTime.now());
                    }
                }
            }
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Object classObj = metaObject.getOriginalObject();
        AccountContextFill contextFill = classObj.getClass().getAnnotation(AccountContextFill.class);
        if (contextFill != null) {
            Field[] declaredFields = classObj.getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                String fieldName = field.getName();
                Long userId = AccountContext.getUserId();
                if (FuncUtil.isNotEmpty(userId)) {
                    if (FuncUtil.equals(fieldName, contextFill.updateBy())) {
                        this.fillStrategy(metaObject, fieldName, userId);
                    }
                }
                if (FuncUtil.equals(fieldName, contextFill.updateAt())) {
                    if (field.getType().equals(Date.class)) {
                        this.fillStrategy(metaObject, fieldName, new Date());
                    } else if (field.getType().equals(LocalDateTime.class)) {
                        this.fillStrategy(metaObject, fieldName, LocalDateTime.now());
                    }
                }
            }
        }
    }

}


