package com.bidr.kernel.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * Title: BaseEvent
 * Description: 事件基类，所有业务事件继承此类
 * Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
public abstract class BaseEvent extends ApplicationEvent {

    /**
     * 事件发生时间
     */
    private final LocalDateTime occurredTime;

    /**
     * 事件来源（模块名或类名）
     */
    private final String source;

    public BaseEvent(Object source) {
        super(source);
        this.occurredTime = LocalDateTime.now();
        this.source = source.getClass().getSimpleName();
    }

    public BaseEvent(Object source, String sourceName) {
        super(source);
        this.occurredTime = LocalDateTime.now();
        this.source = sourceName;
    }

    public LocalDateTime getOccurredTime() {
        return occurredTime;
    }

    public String getSource() {
        return source;
    }

    /**
     * 获取事件类型名称
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
