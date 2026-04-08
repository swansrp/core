package com.bidr.kafka.service;

import com.bidr.kafka.config.KafkaProperties;
import com.bidr.kafka.dao.entity.SysKafka;
import com.bidr.kafka.dao.repository.SysKafkaService;
import lombok.Setter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: KafkaConsumerManager
 * Description: Kafka消费者管理器，支持动态开启/暂停/停止消费者 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
public class KafkaConsumerManager {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerManager.class);

    /**
     * 消费者容器映射：topic -> 容器
     */
    private final Map<String, ConcurrentMessageListenerContainer<String, String>> containers = new ConcurrentHashMap<>();

    /**
     * 消费者配置映射：topic -> 配置
     */
    private final Map<String, KafkaProperties.TopicConfig> topicConfigs = new ConcurrentHashMap<>();

    /**
     * 消费者实例映射：topic -> 消费者
     */
    private final Map<String, KafkaTopicConsumer> consumers = new ConcurrentHashMap<>();

    /**
     * -- SETTER --
     *  设置消费者工厂
     */
    @Setter
    private ConsumerFactory<String, String> consumerFactory;
    /**
     * -- SETTER --
     *  设置Kafka配置
     */
    @Setter
    private KafkaProperties kafkaProperties;

    /**
     * -- SETTER --
     *  设置错误处理器（用于死信队列）
     */
    @Setter
    private CommonErrorHandler errorHandler;

    /**
     * -- SETTER --
     *  设置消息记录服务（用于自动落库）
     */
    @Setter
    private SysKafkaService sysKafkaService;

    /**
     * -- SETTER --
     *  设置是否启用消息落库
     */
    @Setter
    private boolean persistenceEnabled = false;

    /**
     * 注册消费者
     *
     * @param topicConfig Topic配置
     * @param consumer 消费者实例
     */
    public void registerConsumer(KafkaProperties.TopicConfig topicConfig, KafkaTopicConsumer consumer) {
        if (!topicConfig.isEnabled()) {
            log.info("Kafka consumer for topic '{}' is disabled, skip registration", topicConfig.getName());
            return;
        }

        topicConfigs.put(topicConfig.getName(), topicConfig);
        consumers.put(topicConfig.getName(), consumer);

        // 判断是否自动启动
        if (topicConfig.isAutoStart()) {
            createAndStartContainer(topicConfig, consumer);
            log.info("Registered and started Kafka consumer for topic: {}", topicConfig.getName());
        } else {
            // 只注册容器但不启动
            createContainer(topicConfig, consumer);
            log.info("Registered Kafka consumer for topic: {} (not started, autoStart=false)", topicConfig.getName());
        }
    }

    /**
     * 创建消费者容器（不启动）
     */
    private void createContainer(KafkaProperties.TopicConfig topicConfig, KafkaTopicConsumer consumer) {
        if (consumerFactory == null) {
            log.warn("ConsumerFactory not initialized, container will not be created for topic: {}", topicConfig.getName());
            return;
        }

        ContainerProperties containerProps = new ContainerProperties(topicConfig.getName());

        // 设置消费者组ID
        String groupId = topicConfig.getGroupId() != null
            ? topicConfig.getGroupId()
            : kafkaProperties.getConsumer().getGroupId();
        containerProps.setGroupId(groupId);

        // 设置ACK模式
        containerProps.setAckMode(topicConfig.isManualAck()
            ? ContainerProperties.AckMode.MANUAL
            : ContainerProperties.AckMode.BATCH);

        // 设置消息监听器
        containerProps.setMessageListener((org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>) (record, acknowledgment) -> {
            // 自动落库：记录消息接收（重试时去重，返回已有记录）
            SysKafka sysKafka = null;
            long startTime = System.currentTimeMillis();
            if (persistenceEnabled && sysKafkaService != null) {
                sysKafka = sysKafkaService.recordReceived(record, groupId);
            }

            try {
                // 自动落库：更新为处理中（仅新记录）
                if (sysKafka != null && SysKafkaService.STATUS_RECEIVED.equals(sysKafka.getStatus())) {
                    sysKafkaService.updateProcessing(sysKafka);
                }

                KafkaTopicConsumer.AckCallback ackCallback = new KafkaTopicConsumer.AckCallback() {
                    private boolean acknowledged = false;

                    @Override
                    public void acknowledge() {
                        acknowledged = true;
                        if (acknowledgment != null) {
                            acknowledgment.acknowledge();
                        }
                    }
                };

                consumer.consume(record, ackCallback);

                // 非手动ACK模式下自动确认
                if (!topicConfig.isManualAck() && acknowledgment != null) {
                    acknowledgment.acknowledge();
                }

                // 自动落库：记录处理成功
                if (sysKafka != null) {
                    long costTime = System.currentTimeMillis() - startTime;
                    sysKafkaService.updateSuccess(sysKafka, costTime);
                }
            } catch (Exception e) {
                log.error("Error consuming message from topic {}: {}", topicConfig.getName(), e.getMessage(), e);
                consumer.onError(record, e);
                // 重新抛出异常，让 DefaultErrorHandler 接管重试和死信队列逻辑
                throw e;
            }
        });

        // 使用topic特定配置创建消费者工厂，确保autoOffsetReset等配置生效
        Map<String, Object> consumerProps = kafkaProperties.buildConsumerProps(topicConfig);
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
        DefaultKafkaConsumerFactory<String, String> topicConsumerFactory = 
            new DefaultKafkaConsumerFactory<>(consumerProps);

        ConcurrentMessageListenerContainer<String, String> container =
            new ConcurrentMessageListenerContainer<>(topicConsumerFactory, containerProps);
        container.setConcurrency(topicConfig.getConcurrency());
        container.setBeanName("kafkaContainer-" + topicConfig.getName());
        
        // 设置错误处理器（死信队列支持）
        if (errorHandler != null && kafkaProperties.getDlq() != null && kafkaProperties.getDlq().isEnabled()) {
            container.setCommonErrorHandler(errorHandler);
            log.info("Enabled DLQ error handler for topic: {}", topicConfig.getName());
        }

        containers.put(topicConfig.getName(), container);
        log.info("Created Kafka consumer container for topic: {} with autoOffsetReset: {}", 
            topicConfig.getName(), 
            topicConfig.getAutoOffsetReset() != null ? topicConfig.getAutoOffsetReset() : kafkaProperties.getConsumer().getAutoOffsetReset());
    }

    /**
     * 创建并启动消费者容器
     */
    private void createAndStartContainer(KafkaProperties.TopicConfig topicConfig, KafkaTopicConsumer consumer) {
        createContainer(topicConfig, consumer);
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topicConfig.getName());
        if (container != null) {
            container.start();
            String groupId = topicConfig.getGroupId() != null
                ? topicConfig.getGroupId()
                : kafkaProperties.getConsumer().getGroupId();
            log.info("Started Kafka consumer container for topic: {} with groupId: {}", topicConfig.getName(), groupId);
        }
    }

    /**
     * 启动指定Topic的消费者
     *
     * @param topic Topic名称
     * @return 是否成功启动
     */
    public boolean startConsumer(String topic) {
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (container == null) {
            KafkaProperties.TopicConfig config = topicConfigs.get(topic);
            KafkaTopicConsumer consumer = consumers.get(topic);
            if (config != null && consumer != null) {
                createAndStartContainer(config, consumer);
                return true;
            }
            log.warn("No consumer registered for topic: {}", topic);
            return false;
        }

        if (!container.isRunning()) {
            container.start();
            log.info("Started Kafka consumer for topic: {}", topic);
            return true;
        }

        log.info("Kafka consumer for topic: {} is already running", topic);
        return true;
    }

    /**
     * 暂停指定Topic的消费者
     *
     * @param topic Topic名称
     * @return 是否成功暂停
     */
    public boolean pauseConsumer(String topic) {
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (container == null) {
            log.warn("No container found for topic: {}", topic);
            return false;
        }

        if (container.isRunning()) {
            container.pause();
            log.info("Paused Kafka consumer for topic: {}", topic);
            return true;
        }

        log.info("Kafka consumer for topic: {} is not running", topic);
        return false;
    }

    /**
     * 恢复指定Topic的消费者
     *
     * @param topic Topic名称
     * @return 是否成功恢复
     */
    public boolean resumeConsumer(String topic) {
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (container == null) {
            log.warn("No container found for topic: {}", topic);
            return false;
        }

        if (container.isRunning() && container.isPauseRequested()) {
            container.resume();
            log.info("Resumed Kafka consumer for topic: {}", topic);
            return true;
        }

        log.info("Kafka consumer for topic: {} is not paused", topic);
        return false;
    }

    /**
     * 停止指定Topic的消费者
     *
     * @param topic Topic名称
     * @return 是否成功停止
     */
    public boolean stopConsumer(String topic) {
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (container == null) {
            log.warn("No container found for topic: {}", topic);
            return false;
        }

        if (container.isRunning()) {
            container.stop();
            log.info("Stopped Kafka consumer for topic: {}", topic);
            return true;
        }

        log.info("Kafka consumer for topic: {} is not running", topic);
        return true;
    }

    /**
     * 获取消费者运行状态
     *
     * @param topic Topic名称
     * @return 状态描述
     */
    public String getConsumerStatus(String topic) {
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (container == null) {
            return "NOT_REGISTERED";
        }

        if (!container.isRunning()) {
            return "STOPPED";
        }

        if (container.isPauseRequested()) {
            return "PAUSED";
        }

        return "RUNNING";
    }

    /**
     * 获取所有Topic状态
     *
     * @return Topic状态映射
     */
    public Map<String, String> getAllConsumerStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();
        topicConfigs.keySet().forEach(topic -> status.put(topic, getConsumerStatus(topic)));
        return status;
    }

    /**
     * 停止所有消费者
     */
    public void stopAll() {
        containers.forEach((topic, container) -> {
            if (container.isRunning()) {
                container.stop();
                log.info("Stopped Kafka consumer for topic: {}", topic);
            }
        });
    }

    /**
     * 启动所有消费者
     */
    public void startAll() {
        containers.forEach((topic, container) -> {
            if (!container.isRunning()) {
                container.start();
                log.info("Started Kafka consumer for topic: {}", topic);
            }
        });
    }
}
