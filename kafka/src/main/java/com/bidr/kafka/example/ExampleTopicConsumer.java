package com.bidr.kafka.example;

import com.bidr.kafka.service.KafkaTopicConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Title: ExampleTopicConsumer
 * Description: 示例Kafka消费者 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * 使用方式：
 * 1. 在你的应用中引入kafka模块依赖
 * 2. 创建消费者类实现 KafkaTopicConsumer 接口
 * 3. 在消费者类上添加 @KafkaTopicListener 注解配置topic
 *
 * @author Sharp
 * @since 2025/3/1
 */
// @KafkaTopicListener(
//     topic = "example-topic",
//     groupId = "example-group",        // 可选，默认使用全局 groupId 配置
//     manualAck = true,                  // 是否手动 ACK，默认 true
//     concurrency = 1,                   // 并发消费线程数，默认 1
//     autoOffsetReset = "latest",        // 偏移量重置策略：latest / earliest，默认 latest
//     maxPollRecords = 100,              // 单次 poll 最大记录数，默认 100
//     enabled = true,                    // 是否启用该消费者，默认 true
//     autoStart = true                   // 是否随应用启动自动开始消费，默认 true；false 则注册但不启动，需手动调用 startConsumer()
// )
public class ExampleTopicConsumer implements KafkaTopicConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExampleTopicConsumer.class);

    @Override
    public void consume(ConsumerRecord<String, String> record, AckCallback ack) {
        try {
            log.info("Received message from topic: {}, partition: {}, offset: {}, key: {}, value: {}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());

            // 处理消息逻辑
            processMessage(record.value());

            // 手动确认消息
            ack.acknowledge();

        } catch (Exception e) {
            log.error("Error processing message: {}", record.value(), e);
            // 处理失败可以选择不确认或重试
            // ack.nack(); // 不确认
            // ack.nack(1000); // 等待1秒后重试
        }
    }

    @Override
    public void onError(ConsumerRecord<String, String> record, Exception exception) {
        log.error("Error in consumer for topic: {}, message: {}", record.topic(), record.value(), exception);
        // 可以在这里实现错误处理逻辑，如发送到死信队列等
    }

    private void processMessage(String message) {
        // 实现你的消息处理逻辑
        log.info("Processing message: {}", message);
    }
}
