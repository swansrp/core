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
 * Title: SaSmsTemplate
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 17:14
 */

/**
 * 短信模板表
 */
@ApiModel(value = "短信模板表")
@Data
@TableName(value = "sa_sms_template")
public class SaSmsTemplate {
    public static final String COL_ID = "id";
    public static final String COL_TEMPLATE_TITLE = "template_title";
    public static final String COL_TEMPLATE_TYPE = "template_type";
    public static final String COL_SMS_TYPE = "sms_type";
    public static final String COL_TEMPLATE_CODE = "template_code";
    public static final String COL_PARAMETER = "parameter";
    public static final String COL_BODY = "body";
    public static final String COL_SIGN = "sign";
    public static final String COL_AUTHOR = "author";
    public static final String COL_PLATFORM = "platform";
    public static final String COL_CONFIRM_AT = "confirm_at";
    public static final String COL_CONFIRM_STATUS = "confirm_status";
    public static final String COL_REASON = "reason";
    public static final String COL_REMARK = "remark";
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Integer id;
    /**
     * 短信模板名称
     */
    @TableField(value = "template_title")
    @ApiModelProperty(value = "短信模板名称")
    private String templateTitle;
    /**
     * 短信模板类型0验证码1通知2推广
     */
    @TableField(value = "template_type")
    @ApiModelProperty(value = "短信模板类型0验证码1通知2推广")
    private Integer templateType;
    /**
     * 发送短信类型
     */
    @TableField(value = "sms_type")
    @ApiModelProperty(value = "发送短信类型")
    private String smsType;
    /**
     * 云平台短信模板
     */
    @TableField(value = "template_code")
    @ApiModelProperty(value = "云平台短信模板")
    private String templateCode;
    /**
     * 短信模板参数个数
     */
    @TableField(value = "`parameter`")
    @ApiModelProperty(value = "短信模板参数个数")
    private String parameter;
    /**
     * 短信模板内容
     */
    @TableField(value = "body")
    @ApiModelProperty(value = "短信模板内容")
    private String body;
    /**
     * 短信签名
     */
    @TableField(value = "sign")
    @ApiModelProperty(value = "短信签名")
    private String sign;
    /**
     * 作者
     */
    @TableField(value = "author")
    @ApiModelProperty(value = "作者")
    private String author;
    /**
     * 平台id
     */
    @TableField(value = "platform")
    @ApiModelProperty(value = "平台id")
    private String platform;
    /**
     * 审批时间
     */
    @TableField(value = "confirm_at")
    @ApiModelProperty(value = "审批时间")
    private Date confirmAt;
    /**
     * 0 未审批 1 同意 2 拒绝
     */
    @TableField(value = "confirm_status")
    @ApiModelProperty(value = "0 未审批 1 同意 2 拒绝")
    private Integer confirmStatus;
    /**
     * 审核理由
     */
    @TableField(value = "reason")
    @ApiModelProperty(value = "审核理由")
    private String reason;
    /**
     * 短信模板附言
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "短信模板附言")
    private String remark;
}
