package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

 /**
 * Title: SysConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/27 23:06
 */

/**
 * 参数配置表
 */
@ApiModel(value = "参数配置表")
@Data
@TableName(value = "sys_config")
public class SysConfig {
    public static final String COL_TITLE = "title";
    /**
     * 参数主键
     */
    @TableId(value = "config_id", type = IdType.AUTO)
    @ApiModelProperty(value = "参数主键")
    private Integer configId;

    /**
     * 参数名称
     */
    @TableField(value = "config_name")
    @ApiModelProperty(value = "参数名称")
    private String configName;

    /**
     * 参数键名
     */
    @TableField(value = "config_key")
    @ApiModelProperty(value = "参数键名")
    private String configKey;

    /**
     * 参数键值
     */
    @TableField(value = "config_value")
    @ApiModelProperty(value = "参数键值")
    private String configValue;

    /**
     * 系统内置
     */
    @TableField(value = "config_type")
    @ApiModelProperty(value = "系统内置")
    private String configType;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public static final String COL_CONFIG_ID = "config_id";

    public static final String COL_CONFIG_NAME = "config_name";

    public static final String COL_CONFIG_KEY = "config_key";

    public static final String COL_CONFIG_VALUE = "config_value";

    public static final String COL_CONFIG_TYPE = "config_type";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_CREATE_TIME = "create_time";

    public static final String COL_UPDATE_BY = "update_by";

    public static final String COL_UPDATE_TIME = "update_time";

    public static final String COL_REMARK = "remark";
}
