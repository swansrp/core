package com.bidr.kafka.exception;

/**
 * 非重试Kafka异常标记接口
 * <p>
 * 实现此接口的异常类在被 Kafka 消费框架捕获时，不会进行重试，
 * 而是直接进入死信队列（DLQ）并更新数据库记录。
 * <p>
 * 使用场景：当某个异常代表"不可恢复"的错误（如缺少处理器、消息格式永久错误等），
 * 重试无法解决问题，应直接进入死信队列供人工排查。
 *
 * @author Sharp
 * @since 2026/5/8
 */
public class NonRetryableKafkaException extends RuntimeException {
    public NonRetryableKafkaException(String message) {
        super(message);
    }
}
