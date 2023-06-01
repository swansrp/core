package com.bidr.platform.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

 /**
 * Title: SysConfig
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 13:32
 */

/**
 * 参数配置表
 */
@ApiModel(description = "参数配置表")
@Data
@TableName(value = "sys_config")
public class SysConfig {
    /**
     * 参数主键
     */
    @TableId(value = "config_id", type = IdType.AUTO)
    @ApiModelProperty(value = "参数主键")
    @NotNull(message = "参数主键不能为null")
    private Integer configId;

    /**
     * 参数名称
     */
    @TableField(value = "config_name")
    @ApiModelProperty(value = "参数名称")
    @Size(max = 100, message = "参数名称最大长度要小于 100")
    private String configName;

    /**
     * 参数键名
     */
    @TableField(value = "config_key")
    @ApiModelProperty(value = "参数键名")
    @Size(max = 100, message = "参数键名最大长度要小于 100")
    private String configKey;

    /**
     * 参数键值
     */
    @TableField(value = "config_value")
    @ApiModelProperty(value = "参数键值")
    @Size(max = 500, message = "参数键值最大长度要小于 500")
    private String configValue;

    /**
     * 系统内置
     */
    @TableField(value = "config_type")
    @ApiModelProperty(value = "系统内置")
    @Size(max = 1, message = "系统内置最大长度要小于 1")
    private String configType;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private Long createBy;

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
    private Long updateBy;

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
    @Size(max = 500, message = "备注最大长度要小于 500")
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
