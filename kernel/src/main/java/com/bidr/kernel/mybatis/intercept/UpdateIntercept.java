package com.bidr.kernel.mybatis.intercept;

import com.bidr.kernel.mybatis.handler.MetaObjectHandlerManager;
import com.bidr.kernel.mybatis.intercept.ExecutorUpdateIntercept;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

/**
 * Title: UpdateIntercept
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/7/14 14:27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateIntercept implements ExecutorUpdateIntercept {

    private final MetaObjectHandlerManager metaObjectHandlerManager;

    @Override
    public void proceed(MappedStatement ms, Object parameter) {
        SqlCommandType sqlCommandType = ms.getSqlCommandType();
        if (parameter != null) {
            MetaObject metaObject = SystemMetaObject.forObject(parameter);
            if (sqlCommandType == SqlCommandType.INSERT) {
                metaObjectHandlerManager.insertFill(metaObject);
            } else if (sqlCommandType == SqlCommandType.UPDATE) {
                metaObjectHandlerManager.updateFill(metaObject);
            }
        }
    }
}
