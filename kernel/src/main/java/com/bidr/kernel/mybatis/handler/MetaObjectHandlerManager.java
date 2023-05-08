package com.bidr.kernel.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Title: MetaObjectHandlerManager
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/28 11:29
 */
@Primary
@Component
@RequiredArgsConstructor
public class MetaObjectHandlerManager implements MetaObjectHandler {

    @Lazy
    private final List<MetaObjectHandler> handlers;

    @Override
    public void insertFill(MetaObject metaObject) {
        handlers.stream().filter(o -> !o.equals(this)).forEach(o -> o.insertFill(metaObject));
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        handlers.stream().filter(o -> !o.equals(this)).forEach(o -> o.updateFill(metaObject));
    }

}
