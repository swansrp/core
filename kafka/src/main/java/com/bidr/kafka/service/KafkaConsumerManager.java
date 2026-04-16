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
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
     * 默认topic不存在时的最大重试次数
     */
    private static final int DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS = 3;

    /**
     * 默认topic不存在时的冷却时间（毫秒），1小时
     */
    private static final long DEFAULT_COOLDOWN_MILLIS = 60 * 60 * 1000L;

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
     * 消费者工厂缓存：topic -> ConsumerFactory
     */
    private final Map<String, DefaultKafkaConsumerFactory<String, String>> consumerFactoryCache = new ConcurrentHashMap<>();

    /**
     * topic不存在重试计数：topic -> 已重试次数
     */
    private final Map<String, AtomicInteger> topicAbsentCounters = new ConcurrentHashMap<>();

    /**
     * topic不存在首次发现时间：topic -> 首次发现时间
     */
    private final Map<String, Instant> topicAbsentFirstSeen = new ConcurrentHashMap<>();

    /**
     * topic不存在已被停止（达到最大重试次数）：topic -> 停止时间
     */
    private final Map<String, Instant> topicAbsentStopped = new ConcurrentHashMap<>();

    /**
     * 快速重试调度器（用于topic不存在时的立即重试）
     */
    private final ScheduledExecutorService quickRetryScheduler = Executors.newScheduledThreadPool(2);

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

    // ==================== 注册与创建 ====================

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

        if (topicConfig.isAutoStart()) {
            createAndStartContainer(topicConfig, consumer);
            log.info("Registered and started Kafka consumer for topic: {}", topicConfig.getName());
        } else {
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
        containerProps.setMessageListener(
            (org.springframework.kafka.listener.AcknowledgingMessageListener<String, String>) (record, acknowledgment) -> {
                onMessageConsumed(topicConfig, record, acknowledgment, consumer, groupId);
            });

        // 使用topic特定配置创建消费者工厂（带缓存）
        DefaultKafkaConsumerFactory<String, String> topicConsumerFactory = consumerFactoryCache.computeIfAbsent(
            topicConfig.getName(),
            key -> {
                Map<String, Object> consumerProps = kafkaProperties.buildConsumerProps(topicConfig);
                consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG, groupId);
                log.info("Created ConsumerFactory for topic: {} with autoOffsetReset: {}",
                    topicConfig.getName(),
                    topicConfig.getAutoOffsetReset() != null ? topicConfig.getAutoOffsetReset() : kafkaProperties.getConsumer().getAutoOffsetReset());
                return new DefaultKafkaConsumerFactory<>(consumerProps);
            }
        );

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

        log.info("Created Kafka consumer container for topic: {} with groupId: {}",
            topicConfig.getName(), groupId);
    }

    /**
     * 消息消费处理
     */
    private void onMessageConsumed(KafkaProperties.TopicConfig topicConfig,
                                    ConsumerRecord<String, String> record,
                                    org.springframework.kafka.support.Acknowledgment acknowledgment,
                                    KafkaTopicConsumer consumer, String groupId) {
        SysKafka sysKafka = null;
        long startTime = System.currentTimeMillis();
        if (persistenceEnabled && sysKafkaService != null) {
            sysKafka = sysKafkaService.recordReceived(record, groupId);
        }

        try {
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

            if (!topicConfig.isManualAck() && acknowledgment != null) {
                acknowledgment.acknowledge();
            }

            if (sysKafka != null) {
                long costTime = System.currentTimeMillis() - startTime;
                sysKafkaService.updateSuccess(sysKafka, costTime);
            }
        } catch (Exception e) {
            log.error("Error consuming message from topic {}: {}", topicConfig.getName(), e.getMessage(), e);
            consumer.onError(record, e);
            throw e;
        }
    }

    /**
     * 创建并启动消费者容器
     */
    private void createAndStartContainer(KafkaProperties.TopicConfig topicConfig, KafkaTopicConsumer consumer) {
        String topic = topicConfig.getName();
        
        // 启动前先检查topic是否存在
        if (!checkTopicExists(topic)) {
            log.warn("Topic '{}' does not exist on startup, will not start container. Will retry later.", topic);
            // 记录首次发现时间
            topicAbsentFirstSeen.put(topic, Instant.now());
            // 创建容器但不启动
            createContainer(topicConfig, consumer);
            // 启动快速重试检查
            scheduleQuickRetryCheck(topic);
            return;
        }
        
        // topic存在,正常启动
        createContainer(topicConfig, consumer);
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (container != null) {
            container.start();
            String groupId = topicConfig.getGroupId() != null
                ? topicConfig.getGroupId()
                : kafkaProperties.getConsumer().getGroupId();
            log.info("Started Kafka consumer container for topic: {} with groupId: {}", topic, groupId);
        }
    }

    /**
     * 启动快速重试检查（立即执行,每10秒重试一次,最多3次）
     * 用于在容器未启动时检查topic是否存在,存在则启动容器
     */
    private void scheduleQuickRetryCheck(String topic) {
        quickRetryScheduler.scheduleAtFixedRate(() -> {
            try {
                // 如果已经标记为停止,静默跳过,不打印任何日志
                if (topicAbsentStopped.containsKey(topic)) {
                    return;
                }
                
                ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
                KafkaProperties.TopicConfig config = topicConfigs.get(topic);
                KafkaTopicConsumer consumer = consumers.get(topic);
                
                // 容器或配置不存在,放弃重试
                if (container == null || config == null || consumer == null) {
                    return;
                }
                
                // 如果容器已经在运行,不需要快速重试
                if (container.isRunning()) {
                    return;
                }
                
                // 检查topic是否存在
                if (checkTopicExists(topic)) {
                    // topic存在,清理计数并启动容器
                    topicAbsentCounters.remove(topic);
                    topicAbsentFirstSeen.remove(topic);
                    container.start();
                    String groupId = config.getGroupId() != null
                        ? config.getGroupId()
                        : kafkaProperties.getConsumer().getGroupId();
                    log.info("Topic '{}' is now available, container STARTED with groupId: {}", topic, groupId);
                } else {
                    // topic仍不存在,增加计数
                    AtomicInteger counter = topicAbsentCounters.computeIfAbsent(topic, k -> new AtomicInteger(0));
                    int attempt = counter.incrementAndGet();

                    if (attempt >= DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS) {
                        // 达到最大次数,标记为已停止
                        topicAbsentStopped.put(topic, Instant.now());
                        log.warn("Topic '{}' still does not exist after {} quick checks, will retry after cooldown ({} hour).",
                            topic, DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS, DEFAULT_COOLDOWN_MILLIS / 3600000);
                        // 立即返回,不再继续
                        return;
                    } else {
                        log.warn("Topic '{}' does not exist, quick check {}/{}", topic, attempt, DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS);
                    }
                }
            } catch (Exception e) {
                log.debug("Quick retry check failed for topic '{}': {}", topic, e.getMessage());
            }
        }, 0, 10, TimeUnit.SECONDS); // 立即执行,每10秒一次
    }

    // ==================== topic不存在时的有限重试 ====================

    /**
     * 检查topic是否存在（通过AdminClient）
     *
     * @param topicName topic名称
     * @return true=存在，false=不存在
     */
    private boolean checkTopicExists(String topicName) {
        try {
            // 创建专门的AdminClient配置（只包含必要的配置）
            Map<String, Object> adminProps = new java.util.HashMap<>();
            adminProps.put("bootstrap.servers", kafkaProperties.getBootstrapServers());
            
            // 添加安全配置（如果有）
            if (kafkaProperties.getSecurity() != null && kafkaProperties.getSecurity().isEnabled()) {
                adminProps.put("security.protocol", kafkaProperties.getSecurity().getProtocol());
                if (kafkaProperties.getSecurity().getMechanism() != null) {
                    adminProps.put("sasl.mechanism", kafkaProperties.getSecurity().getMechanism());
                }
                if (kafkaProperties.getSecurity().getUsername() != null && kafkaProperties.getSecurity().getPassword() != null) {
                    adminProps.put("sasl.jaas.config", kafkaProperties.getSecurity().buildJaasConfig());
                }
            }
            
            try (org.apache.kafka.clients.admin.AdminClient adminClient =
                     org.apache.kafka.clients.admin.AdminClient.create(adminProps)) {
                Set<String> topics = adminClient.listTopics().names().get(10, java.util.concurrent.TimeUnit.SECONDS);
                return topics.contains(topicName);
            }
        } catch (Exception e) {
            log.debug("Failed to check topic existence for '{}': {}", topicName, e.getMessage());
            return false;
        }
    }

    /**
     * topic不存在时处理：计数并决定是否停止容器
     * 由定时任务调用
     *
     * @param topic topic名称
     */
    private void handleTopicAbsent(String topic) {
        KafkaProperties.TopicConfig config = topicConfigs.get(topic);
        ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
        if (config == null || container == null || !container.isRunning()) {
            return;
        }

        // 跳过从未出现过问题的topic（无需检查）
        if (!topicAbsentCounters.containsKey(topic) 
            && !topicAbsentFirstSeen.containsKey(topic) 
            && !topicAbsentStopped.containsKey(topic)) {
            return;
        }

        // 检查topic是否已存在
        if (checkTopicExists(topic)) {
            // topic已恢复，清理计数
            topicAbsentCounters.remove(topic);
            topicAbsentFirstSeen.remove(topic);
            topicAbsentStopped.remove(topic);
            log.info("Topic '{}' is now available, counters cleared", topic);
            return;
        }

        // topic仍不存在，增加计数
        AtomicInteger counter = topicAbsentCounters.computeIfAbsent(topic, k -> new AtomicInteger(0));
        topicAbsentFirstSeen.computeIfAbsent(topic, k -> Instant.now());
        int attempt = counter.incrementAndGet();

        if (attempt >= DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS) {
            // 达到最大次数，停止容器
            container.stop();
            topicAbsentStopped.put(topic, Instant.now());
            log.warn("Topic '{}' still does not exist after {} checks, container STOPPED. Will retry after cooldown.",
                topic, DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS);
        } else {
            log.warn("Topic '{}' does not exist, check {}/{}", topic, attempt, DEFAULT_MAX_TOPIC_RETRY_ATTEMPTS);
        }
    }

    /**
     * 定时检查所有运行中的消费者topic是否存在
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000)
    public void scheduledCheckTopicAvailability() {
        containers.forEach((topic, container) -> {
            // 跳过非运行中的容器
            if (!container.isRunning()) {
                return;
            }
            // 跳过已达到最大重试次数被停止的topic(由冷却恢复逻辑处理)
            if (topicAbsentStopped.containsKey(topic)) {
                return;
            }
            // 检查topic是否存在
            handleTopicAbsent(topic);
        });
        
        // 同时检查被停止的topic是否可以恢复
        recoverStoppedTopics();
    }

    /**
     * 检查被停止的topic是否冷却时间已过，尝试恢复
     */
    private void recoverStoppedTopics() {
        if (topicAbsentStopped.isEmpty()) {
            return;
        }
        
        Iterator<Map.Entry<String, Instant>> it = topicAbsentStopped.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Instant> entry = it.next();
            String topic = entry.getKey();
            Instant stoppedAt = entry.getValue();
            long millisSinceStop = Duration.between(stoppedAt, Instant.now()).toMillis();

            // 冷却时间未到
            if (millisSinceStop < DEFAULT_COOLDOWN_MILLIS) {
                continue;
            }

            long minutesSinceStop = Duration.between(stoppedAt, Instant.now()).toMinutes();
            log.info("Topic '{}' cooldown period elapsed ({} minutes), checking availability...",
                topic, minutesSinceStop);

            // 检查topic是否存在
            if (checkTopicExists(topic)) {
                // topic已存在，重置计数器并重启容器
                topicAbsentCounters.remove(topic);
                topicAbsentFirstSeen.remove(topic);
                it.remove();

                ConcurrentMessageListenerContainer<String, String> container = containers.get(topic);
                if (container != null && !container.isRunning()) {
                    container.start();
                    log.info("Topic '{}' is now available, container RESTARTED", topic);
                }
            } else {
                // 仍然不存在，重置停止时间，继续下一轮冷却
                entry.setValue(Instant.now());
                log.info("Topic '{}' still not available after cooldown, will check again later", topic);
            }
        }
    }

    // ==================== 消费者控制 ====================

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

    // ==================== 状态查询 ====================

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
            // 检查是否因为topic不存在而被停止
            if (topicAbsentStopped.containsKey(topic)) {
                return "TOPIC_ABSENT";
            }
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

    // ==================== 批量操作 ====================

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

    /**
     * 手动触发重置指定topic的重试状态并尝试启动
     *
     * @param topic Topic名称
     */
    public void resetAndStartTopic(String topic) {
        topicAbsentCounters.remove(topic);
        topicAbsentFirstSeen.remove(topic);
        topicAbsentStopped.remove(topic);
        log.info("Reset topic absent state for: {}", topic);
        startConsumer(topic);
    }
}
