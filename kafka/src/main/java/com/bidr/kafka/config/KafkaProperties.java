package com.bidr.kafka.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: KafkaProperties
 * Description: Kafka配置属性类 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
@Data
@Component
@ConfigurationProperties(prefix = "kafka")
public class KafkaProperties {

    /**
     * 当前环境标识（从 spring.profiles.active 获取）
     */
    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * Kafka服务器地址
     */
    private String bootstrapServers;

    /**
     * 生产者配置
     */
    private ProducerConfig producer = new ProducerConfig();

    /**
     * 消费者配置
     */
    private ConsumerConfig consumer = new ConsumerConfig();

    /**
     * 消费者Topic配置列表
     */
    private List<TopicConfig> topics = new ArrayList<>();

    /**
     * 事务配置
     */
    private TransactionConfig transaction = new TransactionConfig();

    /**
     * 安全配置
     */
    private SecurityConfig security = new SecurityConfig();

    /**
     * 死信队列配置
     */
    private DlqConfig dlq = new DlqConfig();

    @Data
    public static class ProducerConfig {
        /**
         * 发送失败重试次数
         */
        private int retries = 3;

        /**
         * 批量发送大小
         */
        private int batchSize = 16384;

        /**
         * 批量发送延迟时间(毫秒)
         */
        private long lingerMs = 1;

        /**
         * 发送缓冲区大小
         */
        private long bufferMemory = 33554432;

        /**
         * Key序列化器
         */
        private String keySerializer = "org.apache.kafka.common.serialization.StringSerializer";

        /**
         * Value序列化器
         */
        private String valueSerializer = "org.apache.kafka.common.serialization.StringSerializer";

        /**
         * Ack模式 all, 1, 0
         */
        private String acks = "all";

        /**
         * 是否启用事务
         */
        private boolean transactional = false;

        /**
         * 事务ID前缀
         */
        private String transactionIdPrefix = "tx-";
    }

    @Data
    public static class ConsumerConfig {
        /**
         * 消费者组ID
         */
        private String groupId = "default-group";

        /**
         * 自动提交偏移量
         */
        private boolean autoCommit = false;

        /**
         * 自动提交间隔(毫秒)
         */
        private long autoCommitInterval = 1000;

        /**
         * 偏移量重置策略 earliest, latest, none
         */
        private String autoOffsetReset = "latest";

        /**
         * Key反序列化器
         */
        private String keyDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

        /**
         * Value反序列化器
         */
        private String valueDeserializer = "org.apache.kafka.common.serialization.StringDeserializer";

        /**
         * 最大poll记录数
         */
        private int maxPollRecords = 100;

        /**
         * poll超时时间(毫秒)
         */
        private long pollTimeout = 3000;

        /**
         * 会话超时时间(毫秒)
         */
        private String sessionTimeoutMs = "30000";

        /**
         * 心跳间隔(毫秒)
         */
        private String heartbeatIntervalMs = "10000";
    }

    @Data
    public static class TopicConfig {
        /**
         * Topic名称
         */
        private String name;

        /**
         * 是否启用消费
         */
        private boolean enabled = true;

        /**
         * 是否自动启动（enabled=true时生效，false则注册但不启动）
         */
        private boolean autoStart = true;

        /**
         * 是否手动ACK
         */
        private boolean manualAck = true;

        /**
         * 消费者组ID (覆盖全局配置)
         */
        private String groupId;

        /**
         * 并发数
         */
        private int concurrency = 1;

        /**
         * 偏移量重置策略 (覆盖全局配置)
         */
        private String autoOffsetReset;

        /**
         * 最大poll记录数 (覆盖全局配置)
         */
        private Integer maxPollRecords;

        /**
         * 消费者Bean名称
         */
        private String consumerBean;

        /**
         * topic不存在时的最大重试次数，0表示无限重试（默认为0）
         */
        private int maxTopicRetryAttempts = 0;

        /**
         * topic不存在时的重试间隔（毫秒），默认为60000毫秒（1分钟）
         */
        private long topicRetryInterval = 60000;
    }

    @Data
    public static class TransactionConfig {
        /**
         * 是否启用事务
         */
        private boolean enabled = false;

        /**
         * 事务ID前缀
         */
        private String transactionIdPrefix = "kafka-tx-";
    }

    @Data
    public static class DlqConfig {
        /**
         * 是否启用死信队列
         */
        private boolean enabled = true;

        /**
         * 死信队列后缀，默认为 "-dlq"
         * 例如：原Topic为 "test"，则死信队列为 "test-dlq"
         */
        private String suffix = "-dlq";

        /**
         * 重试次数，默认3次
         */
        private int retryAttempts = 3;

        /**
         * 重试间隔(毫秒)，默认1秒
         */
        private long retryInterval = 1000;

        /**
         * 是否使用指数退避
         */
        private boolean exponentialBackoff = false;

        /**
         * 指数退避乘数，默认2.0
         */
        private double backoffMultiplier = 2.0;

        /**
         * 最大重试间隔(毫秒)，默认30秒
         */
        private long maxRetryInterval = 30000;
    }

    @Data
    public static class SecurityConfig {
        /**
         * 安全协议: PLAINTEXT, SSL, SASL_PLAINTEXT, SASL_SSL
         */
        private String protocol;

        /**
         * SASL机制: PLAIN, SCRAM-SHA-256, SCRAM-SHA-512, GSSAPI
         */
        private String mechanism;

        /**
         * SASL用户名
         */
        private String username;

        /**
         * SASL密码
         */
        private String password;

        /**
         * 是否启用安全认证
         */
        public boolean isEnabled() {
            return protocol != null && !protocol.isEmpty();
        }

        /**
         * 构建JAAS配置字符串
         */
        public String buildJaasConfig() {
            if ("GSSAPI".equals(mechanism)) {
                return "com.sun.security.auth.module.Krb5LoginModule required useKeyTab=true storeKey=true keyTab=\"" + username + "\" principal=\"" + password + "\";";
            }
            // SCRAM 和 PLAIN 使用 ScramLoginModule 或 PlainLoginModule
            String loginModule = "SCRAM-SHA-256".equals(mechanism) || "SCRAM-SHA-512".equals(mechanism)
                ? "org.apache.kafka.common.security.scram.ScramLoginModule"
                : "org.apache.kafka.common.security.plain.PlainLoginModule";
            return loginModule + " required username=\"" + username + "\" password=\"" + password + "\";";
        }
    }

    /**
     * 获取带环境标识的消费者组ID
     * 格式：{groupId}-{profile}
     * 例如：om-pm-assessment-pre
     *
     * @param baseGroupId 基础groupId
     * @return 带环境标识的groupId
     */
    public String getGroupIdWithProfile(String baseGroupId) {
        if (baseGroupId == null || baseGroupId.isEmpty()) {
            baseGroupId = consumer.getGroupId();
        }
        // 如果已经包含环境标识，则不再添加
        if (baseGroupId.endsWith("-" + activeProfile)) {
            return baseGroupId;
        }
        return baseGroupId + "-" + activeProfile;
    }

    /**
     * 获取消费者配置Map
     */
    public Map<String, Object> buildConsumerProps(TopicConfig topicConfig) {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.deserializer", consumer.getKeyDeserializer());
        props.put("value.deserializer", consumer.getValueDeserializer());
        props.put("enable.auto.commit", String.valueOf(consumer.isAutoCommit()));
        props.put("auto.commit.interval.ms", String.valueOf(consumer.getAutoCommitInterval()));
        
        String offsetReset = topicConfig != null && topicConfig.getAutoOffsetReset() != null 
            ? topicConfig.getAutoOffsetReset() 
            : consumer.getAutoOffsetReset();
        props.put("auto.offset.reset", offsetReset);
        
        props.put("max.poll.records", String.valueOf(topicConfig != null && topicConfig.getMaxPollRecords() != null 
            ? topicConfig.getMaxPollRecords() 
            : consumer.getMaxPollRecords()));
        props.put("session.timeout.ms", consumer.getSessionTimeoutMs());
        props.put("heartbeat.interval.ms", consumer.getHeartbeatIntervalMs());

        // 添加安全配置
        addSecurityProps(props);
        
        return props;
    }

    /**
     * 获取生产者配置Map
     */
    public Map<String, Object> buildProducerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", bootstrapServers);
        props.put("key.serializer", producer.getKeySerializer());
        props.put("value.serializer", producer.getValueSerializer());
        props.put("acks", producer.getAcks());
        props.put("retries", String.valueOf(producer.getRetries()));
        props.put("batch.size", String.valueOf(producer.getBatchSize()));
        props.put("linger.ms", String.valueOf(producer.getLingerMs()));
        props.put("buffer.memory", String.valueOf(producer.getBufferMemory()));
        
        // 注意：transactional.id 不应设置在普通 producerFactory 中
        // 事务 ID 仅由 transactionalProducerFactory 单独设置，避免普通 kafkaTemplate 被强制要求事务上下文

        // 添加安全配置
        addSecurityProps(props);
        
        return props;
    }

    /**
     * 添加安全配置到props
     */
    private void addSecurityProps(Map<String, Object> props) {
        if (security != null && security.isEnabled()) {
            props.put("security.protocol", security.getProtocol());
            if (security.getMechanism() != null) {
                props.put("sasl.mechanism", security.getMechanism());
            }
            if (security.getUsername() != null && security.getPassword() != null) {
                props.put("sasl.jaas.config", security.buildJaasConfig());
            }
        }
    }

    /**
     * 获取死信队列Topic名称
     *
     * @param originalTopic 原始Topic
     * @return 死信队列Topic名称
     */
    public String getDlqTopic(String originalTopic) {
        if (dlq != null && dlq.isEnabled()) {
            return originalTopic + dlq.getSuffix();
        }
        return null;
    }
}
