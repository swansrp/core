package com.bidr.kafka.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Title: KafkaTopicListener
 * Description: Kafka Topic监听器注解 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface KafkaTopicListener {

    /**
     * Topic名称
     */
    String topic();

    /**
     * 消费者组ID (可选,默认使用全局配置)
     */
    String groupId() default "";

    /**
     * 是否手动ACK
     */
    boolean manualAck() default true;

    /**
     * 并发数
     */
    int concurrency() default 1;

    /**
     * 偏移量重置策略 earliest, latest, none
     */
    String autoOffsetReset() default "latest";

    /**
     * 最大poll记录数
     */
    int maxPollRecords() default 100;

    /**
     * 是否启用（false则不注册消费者）
     */
    boolean enabled() default true;

    /**
     * 是否自动启动（enabled=true时生效，false则注册但不启动）
     */
    boolean autoStart() default true;
}
