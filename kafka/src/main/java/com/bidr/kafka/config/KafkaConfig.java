package com.bidr.kafka.config;

import com.bidr.kafka.annotation.KafkaTopicListener;
import com.bidr.kafka.dao.repository.SysKafkaService;
import com.bidr.kafka.service.KafkaConsumerManager;
import com.bidr.kafka.service.KafkaProducerService;
import com.bidr.kafka.service.KafkaTopicConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: KafkaConfig
 * Description: Kafka配置类 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {

    @Autowired
    private KafkaProperties kafkaProperties;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private SysKafkaService sysKafkaService;

    /**
     * 生产者工厂
     */
    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = kafkaProperties.buildProducerProps();
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * 事务生产者工厂
     */
    @Bean
    @ConditionalOnProperty(prefix = "kafka.transaction", name = "enabled", havingValue = "true")
    public ProducerFactory<String, String> transactionalProducerFactory() {
        Map<String, Object> configProps = kafkaProperties.buildProducerProps();
        configProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, 
            kafkaProperties.getTransaction().getTransactionIdPrefix() + System.currentTimeMillis());
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(configProps);
        factory.setTransactionIdPrefix(kafkaProperties.getTransaction().getTransactionIdPrefix());
        return factory;
    }

    /**
     * Kafka模板
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * 事务Kafka模板
     */
    @Bean
    @ConditionalOnProperty(prefix = "kafka.transaction", name = "enabled", havingValue = "true")
    public KafkaTemplate<String, String> transactionalKafkaTemplate() {
        return new KafkaTemplate<>(transactionalProducerFactory());
    }

    /**
     * 消费者工厂
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProps(null);
        // 设置消费者组ID
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumer().getGroupId());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka监听容器工厂
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(kafkaProperties.getConsumer().isAutoCommit() 
            ? org.springframework.kafka.listener.ContainerProperties.AckMode.BATCH 
            : org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL);
        
        // 设置错误处理器（重试 + 死信队列）
        if (kafkaProperties.getDlq() != null && kafkaProperties.getDlq().isEnabled()) {
            factory.setCommonErrorHandler(kafkaErrorHandler());
        }
        
        return factory;
    }

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * Kafka错误处理器（重试 + 死信队列）
     */
    @Bean
    @ConditionalOnProperty(prefix = "kafka.dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DefaultErrorHandler kafkaErrorHandler() {
        // 创建自定义死信队列发布器（发送DLQ时自动更新sys_kafka状态）
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate(),
            (record, exception) -> {
                // 计算死信队列Topic名称
                String dlqTopic = kafkaProperties.getDlqTopic(record.topic());
                return new TopicPartition(dlqTopic, record.partition());
            }
        ) {
            @Override
            public void accept(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, Exception exception) {
                String dlqTopic = kafkaProperties.getDlqTopic(record.topic());
                // 发送到DLQ之前，更新sys_kafka状态
                if (sysKafkaService != null) {
                    try {
                        @SuppressWarnings("unchecked")
                        ConsumerRecord<String, String> typedRecord = new ConsumerRecord<>(
                            record.topic(), record.partition(), record.offset(),
                            (String) record.key(), (String) record.value()
                        );
                        String groupId = kafkaProperties.getConsumer().getGroupId();
                        sysKafkaService.updateDlqByRecord(typedRecord, groupId, dlqTopic, (Exception) exception);
                        log.info("Updated sys_kafka status to DLQ for topic={}, partition={}, offset={}",
                            record.topic(), record.partition(), record.offset());
                    } catch (Exception e) {
                        log.error("Failed to update sys_kafka DLQ status: {}", e.getMessage(), e);
                    }
                }
                // 调用父类发送到DLQ Topic
                super.accept(record, exception);
            }
        };

        // 创建重试策略
        BackOff backOff = createBackOff();

        // 创建错误处理器
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        
        // 重试监听器：更新重试次数 + 记录日志
        handler.setRetryListeners((record, ex, deliveryAttempt) -> {
            log.warn("Retry {} for topic: {}, key: {}, exception: {}", 
                deliveryAttempt, record.topic(), record.key(), ex.getMessage());
            // 更新sys_kafka重试次数
            if (sysKafkaService != null) {
                try {
                    @SuppressWarnings("unchecked")
                    ConsumerRecord<String, String> typedRecord = new ConsumerRecord<>(
                        record.topic(), record.partition(), record.offset(),
                        (String) record.key(), (String) record.value()
                    );
                    String groupId = kafkaProperties.getConsumer().getGroupId();
                    sysKafkaService.updateRetryByRecord(typedRecord, groupId, deliveryAttempt, (Exception) ex);
                } catch (Exception e) {
                    log.error("Failed to update sys_kafka retry count: {}", e.getMessage(), e);
                }
            }
        });

        return handler;
    }

    /**
     * 创建重试策略
     */
    private BackOff createBackOff() {
        KafkaProperties.DlqConfig dlqConfig = kafkaProperties.getDlq();
        
        if (dlqConfig.isExponentialBackoff()) {
            // 指数退避
            ExponentialBackOff backOff = new ExponentialBackOff();
            backOff.setInitialInterval(dlqConfig.getRetryInterval());
            backOff.setMultiplier(dlqConfig.getBackoffMultiplier());
            backOff.setMaxInterval(dlqConfig.getMaxRetryInterval());
            return backOff;
        } else {
            // 固定间隔重试
            FixedBackOff backOff = new FixedBackOff(
                dlqConfig.getRetryInterval(),
                dlqConfig.getRetryAttempts()
            );
            return backOff;
        }
    }

    /**
     * 生产者服务
     */
    @Bean
    public KafkaProducerService kafkaProducerService() {
        KafkaProducerService service = new KafkaProducerService();
        service.setKafkaTemplate(kafkaTemplate());
        if (kafkaProperties.getTransaction().isEnabled()) {
            service.setTransactionalKafkaTemplate(transactionalKafkaTemplate());
            service.setTransactionEnabled(true);
        }
        return service;
    }

    /**
     * 消费者管理器
     */
    @Bean
    public KafkaConsumerManager kafkaConsumerManager() {
        KafkaConsumerManager manager = new KafkaConsumerManager();
        manager.setConsumerFactory(consumerFactory());
        manager.setKafkaProperties(kafkaProperties);
        // 设置错误处理器（死信队列支持）
        if (kafkaProperties.getDlq() != null && kafkaProperties.getDlq().isEnabled()) {
            manager.setErrorHandler(kafkaErrorHandler());
        }
        // 设置消息落库服务
        if (sysKafkaService != null && kafkaProperties.getConsumer() != null) {
            manager.setSysKafkaService(sysKafkaService);
            manager.setPersistenceEnabled(true);
            org.slf4j.LoggerFactory.getLogger(KafkaConfig.class)
                .info("Kafka message persistence enabled, messages will be recorded to sys_kafka table");
        }
        return manager;
    }

    /**
     * 注册所有标注了@KafkaTopicListener注解的消费者
     */
    @Bean
    public KafkaListenerRegistrar kafkaListenerRegistrar(KafkaConsumerManager consumerManager) {
        return new KafkaListenerRegistrar(consumerManager, applicationContext, kafkaProperties);
    }

    /**
     * 监听器注册器
     */
    public static class KafkaListenerRegistrar {
        
        public KafkaListenerRegistrar(KafkaConsumerManager consumerManager, 
                                       ApplicationContext context,
                                       KafkaProperties properties) {
            Map<String, Object> beans = context.getBeansWithAnnotation(KafkaTopicListener.class);
            
            for (Map.Entry<String, Object> entry : beans.entrySet()) {
                Object bean = entry.getValue();
                KafkaTopicListener annotation = bean.getClass().getAnnotation(KafkaTopicListener.class);
                
                if (annotation.enabled() && bean instanceof KafkaTopicConsumer) {
                    KafkaProperties.TopicConfig topicConfig = new KafkaProperties.TopicConfig();
                    topicConfig.setName(annotation.topic());
                    topicConfig.setGroupId(annotation.groupId().isEmpty() ? null : annotation.groupId());
                    topicConfig.setManualAck(annotation.manualAck());
                    topicConfig.setConcurrency(annotation.concurrency());
                    topicConfig.setAutoOffsetReset(annotation.autoOffsetReset());
                    topicConfig.setMaxPollRecords(annotation.maxPollRecords());
                    topicConfig.setEnabled(annotation.enabled());
                    topicConfig.setAutoStart(annotation.autoStart());
                    topicConfig.setConsumerBean(entry.getKey());
                    
                    consumerManager.registerConsumer(topicConfig, (KafkaTopicConsumer) bean);
                }
            }
        }
    }
}
