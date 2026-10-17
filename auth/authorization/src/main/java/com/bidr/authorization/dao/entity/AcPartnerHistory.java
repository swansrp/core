package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import lombok.Data;

/**
 * 对接接口访问记录
 */
@ApiModel(description = "对接接口访问记录")
@Data
@TableName(value = "ac_partner_history")
public class AcPartnerHistory {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * 平台
     */
    @TableField(value = "platform")
    @ApiModelProperty(value = "平台")
    private String platform;

    /**
     * appKey
     */
    @TableField(value = "app_key")
    @ApiModelProperty(value = "appKey")
    private String appKey;

    /**
     * 访问ip
     */
    @TableField(value = "remote_ip")
    @ApiModelProperty(value = "访问ip")
    private String remoteIp;

    /**
     * api路径
     */
    @TableField(value = "url")
    @ApiModelProperty(value = "api路径")
    private String url;

    /**
     * 返回值
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "返回值")
    private Integer status;

    /**
     * 返回消息
     */
    @TableField(value = "message")
    @ApiModelProperty(value = "返回消息")
    private String message;

    /**
     * 访问时间
     */
    @TableField(value = "request_at")
    @ApiModelProperty(value = "访问时间")
    private Date requestAt;

    /**
     * 返回时间
     */
    @TableField(value = "response_at")
    @ApiModelProperty(value = "返回时间")
    private Date responseAt;
}