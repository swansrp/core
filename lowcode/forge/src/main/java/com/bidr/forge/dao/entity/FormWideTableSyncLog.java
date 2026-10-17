package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 宽表同步日志
 *
 * @author sharp
 */
@ApiModel(description = "宽表同步日志")
@Data
@TableName(value = "form_wide_table_sync_log")
public class FormWideTableSyncLog {
    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 关联宽表配置 ID
     */
    @TableField(value = "config_id")
    @ApiModelProperty(value = "关联宽表配置 ID")
    private Long configId;

    /**
     * 填报历史 ID
     */
    @TableField(value = "history_id")
    @ApiModelProperty(value = "填报历史 ID")
    private String historyId;

    /**
     * 同步状态: success/fail
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "同步状态: success/fail")
    private String status;

    /**
     * 错误信息
     */
    @TableField(value = "error_msg")
    @ApiModelProperty(value = "错误信息")
    private String errorMsg;

    /**
     * 同步时间
     */
    @TableField(value = "synced_at")
    @ApiModelProperty(value = "同步时间")
    private Date syncedAt;
}
