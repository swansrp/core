package com.bidr.sms.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

 /**
 * Title: SaSmsSend
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/10 12:35
 */

/**
 * 短信发送记录
 */
@ApiModel(value = "短信发送记录")
@Data
@TableName(value = "sa_sms_send")
// @KeySequence("SA_SMS_SEND_SEND_ID_SEQ")
public class SaSmsSend {
    /**
     * 发送流水号
     */
    @TableId(value = "send_id", type = IdType.INPUT)
    @ApiModelProperty(value = "发送流水号")
    private String sendId;

    /**
     * 对接平台id
     */
    @TableField(value = "platform")
    @ApiModelProperty(value = "对接平台id")
    private String platform;

    /**
     * 发送类型
     */
    @TableField(value = "send_type")
    @ApiModelProperty(value = "发送类型")
    private String sendType;

    /**
     * 请求端id
     */
    @TableField(value = "biz_id")
    @ApiModelProperty(value = "请求端id")
    private String bizId;

    /**
     * 手机号码
     */
    @TableField(value = "mobile")
    @ApiModelProperty(value = "手机号码")
    private String mobile;

    /**
     * 发送模板
     */
    @TableField(value = "template_code")
    @ApiModelProperty(value = "发送模板")
    private String templateCode;

    /**
     * 发送签名
     */
    @TableField(value = "send_sign")
    @ApiModelProperty(value = "发送签名")
    private String sendSign;

    /**
     * 发送参数表
     */
    @TableField(value = "send_param")
    @ApiModelProperty(value = "发送参数表")
    private String sendParam;

    /**
     * 发送状态
     */
    @TableField(value = "send_status")
    @ApiModelProperty(value = "发送状态")
    private Integer sendStatus;

    /**
     * 发送结果
     */
    @TableField(value = "send_result")
    @ApiModelProperty(value = "发送结果")
    private String sendResult;

    /**
     * 服务商请求id
     */
    @TableField(value = "request_id")
    @ApiModelProperty(value = "服务商请求id")
    private String requestId;

    /**
     * 发送时间
     */
    @TableField(value = "send_at")
    @ApiModelProperty(value = "发送时间")
    private Date sendAt;

    /**
     * 服务商返回状态码
     */
    @TableField(value = "response_status")
    @ApiModelProperty(value = "服务商返回状态码")
    private Integer responseStatus;

    /**
     * 结果回传时间
     */
    @TableField(value = "response_at")
    @ApiModelProperty(value = "结果回传时间")
    private Date responseAt;

    /**
     * 服务商返回消息
     */
    @TableField(value = "response_msg")
    @ApiModelProperty(value = "服务商返回消息")
    private String responseMsg;

    /**
     * 服务商返回代码
     */
    @TableField(value = "response_code")
    @ApiModelProperty(value = "服务商返回代码")
    private String responseCode;
}
