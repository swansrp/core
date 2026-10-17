package com.bidr.kafka.dao.schema;

import com.bidr.kafka.dao.entity.SysKafka;
import com.bidr.kernel.mybatis.repository.BaseMybatisSchema;
import org.springframework.stereotype.Service;

/**
 * Title: SysKafkaSchema
 * Description: Kafka消息消费记录 Schema Service（数据库初始化） Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/8
 */
@Service
public class SysKafkaSchema extends BaseMybatisSchema<SysKafka> {
    static {
        setCreateDDL("CREATE TABLE IF NOT EXISTS `sys_kafka` (\n" +
                "  `id` varchar(32) NOT NULL COMMENT 'id',\n" +
                "  `group_id` varchar(100) DEFAULT NULL COMMENT '消费者组ID',\n" +
                "  `topic` varchar(200) NOT NULL COMMENT 'Topic名称',\n" +
                "  `partition_no` int(11) DEFAULT NULL COMMENT '分区',\n" +
                "  `offset_no` bigint(20) DEFAULT NULL COMMENT '偏移量',\n" +
                "  `message_key` varchar(500) DEFAULT NULL COMMENT '消息Key',\n" +
                "  `message_value` text DEFAULT NULL COMMENT '消息内容',\n" +
                "  `status` varchar(20) NOT NULL DEFAULT 'RECEIVED' COMMENT '处理状态: RECEIVED-已接收, PROCESSING-处理中, SUCCESS-成功, FAILED-失败, DLQ-进入死信队列',\n" +
                "  `retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '重试次数',\n" +
                "  `exception_class` varchar(500) DEFAULT NULL COMMENT '异常类型',\n" +
                "  `exception_message` varchar(2000) DEFAULT NULL COMMENT '异常信息',\n" +
                "  `exception_stack` text DEFAULT NULL COMMENT '异常堆栈',\n" +
                "  `dlq_topic` varchar(200) DEFAULT NULL COMMENT '死信队列Topic名称',\n" +
                "  `dlq_flag` char(1) NOT NULL DEFAULT '0' COMMENT '是否已进入死信队列',\n" +
                "  `cost_time` bigint(20) DEFAULT NULL COMMENT '消费耗时(毫秒)',\n" +
                "  `received_at` datetime(3) DEFAULT NULL COMMENT '接收时间',\n" +
                "  `processed_at` datetime(3) DEFAULT NULL COMMENT '处理完成时间',\n" +
                "  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n" +
                "  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n" +
                "  PRIMARY KEY (`id`),\n" +
                "  KEY `idx_topic` (`topic`),\n" +
                "  KEY `idx_status` (`status`),\n" +
                "  KEY `idx_group_topic` (`group_id`, `topic`),\n" +
                "  KEY `idx_create_at` (`create_at`),\n" +
                "  KEY `idx_dlq_flag` (`dlq_flag`)\n" +
                ") COMMENT='Kafka消息消费记录';");
    }
}
