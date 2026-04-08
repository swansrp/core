package com.bidr.kafka.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: SysKafka
 * Description: Kafka消息消费记录 Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/4/8
 */
@ApiModel(description = "Kafka消息消费记录")
@Data
@TableName(value = "sys_kafka")
public class SysKafka {

    /**
     * id
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @ApiModelProperty(value = "id")
    private String id;

    /**
     * 消费者组ID
     */
    @TableField(value = "group_id")
    @ApiModelProperty(value = "消费者组ID")
    private String groupId;

    /**
     * Topic名称
     */
    @TableField(value = "topic")
    @ApiModelProperty(value = "Topic名称")
    private String topic;

    /**
     * 分区
     */
    @TableField(value = "partition_no")
    @ApiModelProperty(value = "分区")
    private Integer partitionNo;

    /**
     * 偏移量
     */
    @TableField(value = "offset_no")
    @ApiModelProperty(value = "偏移量")
    private Long offsetNo;

    /**
     * 消息Key
     */
    @TableField(value = "message_key")
    @ApiModelProperty(value = "消息Key")
    private String messageKey;

    /**
     * 消息内容
     */
    @TableField(value = "message_value")
    @ApiModelProperty(value = "消息内容")
    private String messageValue;

    /**
     * 处理状态: RECEIVED-已接收, PROCESSING-处理中, SUCCESS-成功, FAILED-失败, DLQ-进入死信队列
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "处理状态")
    private String status;

    /**
     * 重试次数
     */
    @TableField(value = "retry_count")
    @ApiModelProperty(value = "重试次数")
    private Integer retryCount;

    /**
     * 异常类型
     */
    @TableField(value = "exception_class")
    @ApiModelProperty(value = "异常类型")
    private String exceptionClass;

    /**
     * 异常信息
     */
    @TableField(value = "exception_message")
    @ApiModelProperty(value = "异常信息")
    private String exceptionMessage;

    /**
     * 异常堆栈
     */
    @TableField(value = "exception_stack")
    @ApiModelProperty(value = "异常堆栈")
    private String exceptionStack;

    /**
     * 死信队列Topic名称
     */
    @TableField(value = "dlq_topic")
    @ApiModelProperty(value = "死信队列Topic名称")
    private String dlqTopic;

    /**
     * 是否已进入死信队列
     */
    @TableField(value = "dlq_flag")
    @ApiModelProperty(value = "是否已进入死信队列")
    private String dlqFlag;

    /**
     * 消费耗时(毫秒)
     */
    @TableField(value = "cost_time")
    @ApiModelProperty(value = "消费耗时(毫秒)")
    private Long costTime;

    /**
     * 接收时间
     */
    @TableField(value = "received_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "接收时间")
    private Date receivedAt;

    /**
     * 处理完成时间
     */
    @TableField(value = "processed_at")
    @ApiModelProperty(value = "处理完成时间")
    private Date processedAt;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
