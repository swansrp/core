package com.bidr.kafka.service;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.CompletableFuture;

/**
 * Title: KafkaProducerService
 * Description: Kafka生产者服务 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
@Setter
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);

    /**
     * -- SETTER --
     *  设置Kafka模板
     */
    private KafkaTemplate<String, String> kafkaTemplate;
    /**
     * -- SETTER --
     *  设置事务Kafka模板
     */
    private KafkaTemplate<String, String> transactionalKafkaTemplate;
    /**
     * -- SETTER --
     *  设置是否启用事务
     */
    private boolean transactionEnabled = false;

    /**
     * 根据当前线程是否处于 @Transactional 事务中，自动选择合适的 KafkaTemplate：
     * - 有事务上下文且事务功能已启用 → 使用 transactionalKafkaTemplate
     * - 无事务上下文或事务功能未启用 → 使用普通 kafkaTemplate
     */
    private KafkaTemplate<String, String> resolveTemplate() {
        if (transactionEnabled && transactionalKafkaTemplate != null
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            return transactionalKafkaTemplate;
        }
        return kafkaTemplate;
    }

    /**
     * 发送消息（同步）
     *
     * @param topic Topic名称
     * @param message 消息内容
     * @return 发送结果
     */
    public SendResult<String, String> sendSync(String topic, String message) {
        try {
            return resolveTemplate().send(topic, message).get();
        } catch (Exception e) {
            log.error("Failed to send message to topic: {}", topic, e);
            throw new RuntimeException("Failed to send message to topic: " + topic, e);
        }
    }

    /**
     * 发送消息（同步带Key）
     *
     * @param topic Topic名称
     * @param key 消息Key
     * @param message 消息内容
     * @return 发送结果
     */
    public SendResult<String, String> sendSync(String topic, String key, String message) {
        try {
            return resolveTemplate().send(topic, key, message).get();
        } catch (Exception e) {
            log.error("Failed to send message to topic: {} with key: {}", topic, key, e);
            throw new RuntimeException("Failed to send message to topic: " + topic, e);
        }
    }

    /**
     * 发送消息（异步）
     *
     * @param topic Topic名称
     * @param message 消息内容
     * @return CompletableFuture
     */
    public CompletableFuture<SendResult<String, String>> sendAsync(String topic, String message) {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();

        ListenableFuture<SendResult<String, String>> kafkaFuture = resolveTemplate().send(topic, message);
        kafkaFuture.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                log.debug("Sent message to topic: {} with offset: {}", topic, result.getRecordMetadata().offset());
                future.complete(result);
            }

            @Override
            public void onFailure(Throwable ex) {
                log.error("Failed to send message to topic: {}", topic, ex);
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    /**
     * 发送消息（异步带Key）
     *
     * @param topic Topic名称
     * @param key 消息Key
     * @param message 消息内容
     * @return CompletableFuture
     */
    public CompletableFuture<SendResult<String, String>> sendAsync(String topic, String key, String message) {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();

        ListenableFuture<SendResult<String, String>> kafkaFuture = resolveTemplate().send(topic, key, message);
        kafkaFuture.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                log.debug("Sent message to topic: {} with key: {} offset: {}", topic, key, result.getRecordMetadata().offset());
                future.complete(result);
            }

            @Override
            public void onFailure(Throwable ex) {
                log.error("Failed to send message to topic: {} with key: {}", topic, key, ex);
                future.completeExceptionally(ex);
            }
        });

        return future;
    }

    /**
     * 发送消息（简单方式）
     * 若调用方法上有 @Transactional 且已启用事务，自动使用事务模板发送
     *
     * @param topic Topic名称
     * @param message 消息内容
     */
    public void send(String topic, String message) {
        resolveTemplate().send(topic, message);
    }

    /**
     * 发送消息（带Key）
     * 若调用方法上有 @Transactional 且已启用事务，自动使用事务模板发送
     *
     * @param topic Topic名称
     * @param key 消息Key
     * @param message 消息内容
     */
    public void send(String topic, String key, String message) {
        resolveTemplate().send(topic, key, message);
    }

    /**
     * 发送消息到指定分区
     * 若调用方法上有 @Transactional 且已启用事务，自动使用事务模板发送
     *
     * @param topic Topic名称
     * @param partition 分区号
     * @param key 消息Key
     * @param message 消息内容
     */
    public void send(String topic, Integer partition, String key, String message) {
        resolveTemplate().send(topic, partition, key, message);
    }

    /**
     * 批量发送消息
     * 若调用方法上有 @Transactional 且已启用事务，同步发送保证事务原子性；无事务时异步发送提升吞吐量
     *
     * @param topic Topic名称
     * @param messages 消息列表
     * @return 无事务时返回 Future列表，有事务时返回 null
     */
    public CompletableFuture<Void> sendBatch(String topic, Iterable<String> messages) {
        if (transactionEnabled && transactionalKafkaTemplate != null
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            // 有事务时：同步发送，确保事务提交前所有消息均已发出或因回滚而取消
            for (String message : messages) {
                transactionalKafkaTemplate.send(topic, message);
            }
            return null;
        }
        // 无事务时：异步发送，提升吞吐量
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            java.util.stream.StreamSupport.stream(messages.spliterator(), false)
                .map(message -> sendAsync(topic, message))
                .toArray(CompletableFuture[]::new)
        );
        return allFutures;
    }

    /**
     * 发送消息并等待确认
     * 若调用方法上有 @Transactional 且已启用事务，自动使用事务模板发送
     *
     * @param topic Topic名称
     * @param message 消息内容
     * @param timeoutMillis 超时时间(毫秒)
     * @return 是否发送成功
     */
    public boolean sendWithTimeout(String topic, String message, long timeoutMillis) {
        try {
            resolveTemplate().send(topic, message).get(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            log.error("Failed to send message to topic: {} within {} ms", topic, timeoutMillis, e);
            return false;
        }
    }
}
