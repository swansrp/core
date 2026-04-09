package com.bidr.kafka.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kafka.dao.entity.SysKafka;
import com.bidr.kafka.dao.mapper.SysKafkaMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * Title: SysKafkaService
 * Description: Kafka消息消费记录 Repository Service Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/8
 */
@Slf4j
@Service
public class SysKafkaService extends BaseSqlRepo<SysKafkaMapper, SysKafka> {
    /**
     * 状态常量：处理中
     */
    public static final String STATUS_PROCESSING = "0";
    /**
     * 状态常量：处理成功
     */
    public static final String STATUS_SUCCESS = "1";
    /**
     * 状态常量：处理失败
     */
    public static final String STATUS_FAILED = "-1";
    /**
     * 状态常量：进入死信队列
     */
    public static final String STATUS_DLQ = "-2";

    /**
     * 记录消息接收（带去重：重试时返回已有记录）
     *
     * @param record  Kafka消息记录
     * @param groupId 消费者组ID
     * @return 持久化后的记录
     */
    public SysKafka recordReceived(ConsumerRecord<String, String> record, String groupId) {
        // 去重：重试时同一消息不会重复插入
        SysKafka existing = findByRecord(record, groupId);
        if (existing != null) {
            log.debug("Found existing SysKafka record for topic={}, partition={}, offset={}, returning existing",
                record.topic(), record.partition(), record.offset());
            return existing;
        }

        SysKafka entity = new SysKafka();
        entity.setGroupId(groupId);
        entity.setTopic(record.topic());
        entity.setPartitionNo(record.partition());
        entity.setOffsetNo(record.offset());
        entity.setMessageKey(record.key());
        entity.setMessageValue(truncate(record.value(), 5000));
        // 优化：直接设置为处理中，避免后续立即更新
        entity.setStatus(STATUS_PROCESSING);
        entity.setRetryCount(0);
        entity.setDlqFlag("0");
        entity.setReceivedAt(new Date());
        try {
            insert(entity);
        } catch (Exception e) {
            log.error("Failed to record kafka message received: {}", e.getMessage(), e);
        }
        return entity;
    }

    /**
     * 根据ConsumerRecord查找已有的消费记录
     *
     * @param record  Kafka消息记录
     * @param groupId 消费者组ID
     * @return 已有记录，不存在则返回null
     */
    public SysKafka findByRecord(ConsumerRecord<String, String> record, String groupId) {
        try {
            LambdaQueryWrapper<SysKafka> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysKafka::getTopic, record.topic())
                   .eq(SysKafka::getPartitionNo, record.partition())
                   .eq(SysKafka::getOffsetNo, record.offset())
                   .eq(SysKafka::getGroupId, groupId)
                   .orderByDesc(SysKafka::getCreateAt)
                   .last("LIMIT 1");
            return selectOne(wrapper);
        } catch (Exception e) {
            log.error("Failed to find SysKafka record: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 根据ConsumerRecord更新为进入死信队列
     *
     * @param record   Kafka消息记录
     * @param groupId  消费者组ID
     * @param dlqTopic 死信队列Topic名称
     * @param e        异常
     */
    public void updateDlqByRecord(ConsumerRecord<String, String> record, String groupId,
                                  String dlqTopic, Exception e) {
        SysKafka entity = findByRecord(record, groupId);
        if (entity != null) {
            updateDlq(entity, dlqTopic, e);
        } else {
            log.warn("SysKafka record not found for DLQ update, topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());
        }
    }

    /**
     * 根据ConsumerRecord更新重试次数
     *
     * @param record        Kafka消息记录
     * @param groupId       消费者组ID
     * @param deliveryAttempt 当前重试次数
     * @param e             异常
     */
    public void updateRetryByRecord(ConsumerRecord<String, String> record, String groupId,
                                    int deliveryAttempt, Exception e) {
        SysKafka entity = findByRecord(record, groupId);
        if (entity != null) {
            updateRetry(entity, deliveryAttempt, e);
        }
    }

    /**
     * 更新为处理中状态
     *
     * @param entity 消费记录实体
     */
    public void updateProcessing(SysKafka entity) {
        entity.setStatus(STATUS_PROCESSING);
        try {
            updateById(entity);
        } catch (Exception e) {
            log.error("Failed to update kafka message status to PROCESSING: {}", e.getMessage(), e);
        }
    }

    /**
     * 更新为处理成功
     *
     * @param entity   消费记录实体
     * @param costTime 耗时(毫秒)
     */
    public void updateSuccess(SysKafka entity, long costTime) {
        entity.setStatus(STATUS_SUCCESS);
        entity.setCostTime(costTime);
        entity.setProcessedAt(new Date());
        try {
            updateById(entity);
        } catch (Exception e) {
            log.error("Failed to update kafka message status to SUCCESS: {}", e.getMessage(), e);
        }
    }

    /**
     * 更新为处理失败
     *
     * @param entity    消费记录实体
     * @param e         异常
     * @param costTime  耗时(毫秒)
     */
    public void updateFailed(SysKafka entity, Exception e, long costTime) {
        entity.setStatus(STATUS_FAILED);
        entity.setCostTime(costTime);
        entity.setProcessedAt(new Date());
        fillException(entity, e);
        try {
            updateById(entity);
        } catch (Exception ex) {
            log.error("Failed to update kafka message status to FAILED: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 更新重试次数
     *
     * @param entity      消费记录实体
     * @param retryCount  当前重试次数
     * @param e           异常
     */
    public void updateRetry(SysKafka entity, int retryCount, Exception e) {
        entity.setRetryCount(retryCount);
        entity.setStatus(STATUS_FAILED);
        fillException(entity, e);
        try {
            updateById(entity);
        } catch (Exception ex) {
            log.error("Failed to update kafka message retry count: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 更新为进入死信队列
     *
     * @param entity   消费记录实体
     * @param dlqTopic 死信队列Topic名称
     * @param e        异常
     */
    public void updateDlq(SysKafka entity, String dlqTopic, Exception e) {
        entity.setStatus(STATUS_DLQ);
        entity.setDlqFlag("1");
        entity.setDlqTopic(dlqTopic);
        entity.setProcessedAt(new Date());
        fillException(entity, e);
        try {
            updateById(entity);
        } catch (Exception ex) {
            log.error("Failed to update kafka message status to DLQ: {}", ex.getMessage(), ex);
        }
    }

    /**
     * 填充异常信息
     *
     * @param entity 消费记录实体
     * @param e      异常
     */
    private void fillException(SysKafka entity, Exception e) {
        if (e != null) {
            entity.setExceptionClass(e.getClass().getName());
            entity.setExceptionMessage(truncate(e.getMessage(), 2000));
            entity.setExceptionStack(truncate(getStackTrace(e), 5000));
        }
    }

    /**
     * 获取异常堆栈字符串
     *
     * @param e 异常
     * @return 堆栈字符串
     */
    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * 截断字符串
     *
     * @param str 原始字符串
     * @param max 最大长度
     * @return 截断后的字符串
     */
    private String truncate(String str, int max) {
        if (str == null) {
            return null;
        }
        return str.length() > max ? str.substring(0, max) : str;
    }
}
