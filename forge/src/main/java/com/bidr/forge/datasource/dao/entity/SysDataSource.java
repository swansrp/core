package com.bidr.forge.datasource.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: SysDataSource
 * Description: 数据源配置表（数据源管理）。配置项经 DataSourceCacheService
 * 加载进内存并按名称维护连接池，前端可随时触发刷新；未静态定义在 yml
 * （spring.datasource.dynamic.datasource）的名称会在切换时动态注册进
 * dynamic-datasource 路由，供 dataset/matrix/问数等全链路共用。
 * 目前仅支持 MySQL 语法系（MySQL/Doris/StarRocks 等 MySQL 协议库）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@ApiModel(description = "数据源配置表")
@Data
@TableName(value = "sys_data_source")
public class SysDataSource {

    @TableId(value = "ds_id", type = IdType.AUTO)
    @ApiModelProperty(value = "数据源主键")
    private Integer dsId;

    @TableField(value = "ds_name")
    @ApiModelProperty(value = "数据源名称（唯一，作为内存缓存键）")
    @Size(max = 100, message = "数据源名称最大长度要小于 100")
    private String dsName;

    @TableField(value = "ds_type")
    @ApiModelProperty(value = "数据库类型（目前仅支持 mysql 语法系）")
    @Size(max = 20, message = "数据库类型最大长度要小于 20")
    private String dsType;

    @TableField(value = "jdbc_url")
    @ApiModelProperty(value = "JDBC 连接地址（jdbc:mysql://...）")
    @Size(max = 500, message = "JDBC 连接地址最大长度要小于 500")
    private String jdbcUrl;

    @TableField(value = "username")
    @ApiModelProperty(value = "用户名")
    @Size(max = 100, message = "用户名最大长度要小于 100")
    private String username;

    @TableField(value = "password")
    @ApiModelProperty(value = "密码")
    @Size(max = 200, message = "密码最大长度要小于 200")
    private String password;

    @TableField(value = "is_default")
    @ApiModelProperty(value = "是否默认数据源（1=是 0=否），问数执行取默认数据源")
    @Size(max = 1, message = "是否默认数据源最大长度要小于 1")
    private String isDefault;

    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private Long createBy;

    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private Long updateBy;

    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;
}
