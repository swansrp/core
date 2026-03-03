package com.bidr.kafka.config;

import com.bidr.kafka.annotation.KafkaTopicListener;
import com.bidr.kafka.service.KafkaConsumerManager;
import com.bidr.kafka.service.KafkaProducerService;
import com.bidr.kafka.service.KafkaTopicConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

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
        return factory;
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
