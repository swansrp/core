package com.bidr.kafka.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * Title: KafkaTopicConsumer
 * Description: Kafka消费者接口 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
public interface KafkaTopicConsumer {

    /**
     * 消费消息
     *
     * @param record Kafka消息记录
     * @param ack 确认回调
     */
    void consume(ConsumerRecord<String, String> record, AckCallback ack);

    /**
     * 消费异常处理
     *
     * @param record Kafka消息记录
     * @param exception 异常
     */
    default void onError(ConsumerRecord<String, String> record, Exception exception) {
        // 默认实现可以被子类覆盖
    }

    /**
     * ACK确认回调接口
     */
    interface AckCallback {
        /**
         * 确认消息已处理
         */
        void acknowledge();

        /**
         * 确认消息已处理（带偏移量）
         *
         * @param offset 偏移量
         */
        default void acknowledge(long offset) {
            acknowledge();
        }

        /**
         * 标记消息处理失败，不确认
         */
        default void nack() {
            // 默认不做任何操作
        }

        /**
         * 标记消息处理失败，并在指定时间后重新消费
         *
         * @param sleep 重试等待时间(毫秒)
         */
        default void nack(long sleep) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
