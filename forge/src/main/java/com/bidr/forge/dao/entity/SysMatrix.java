package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 矩阵配置
 *
 * @author sharp
 * @since 2025-11-20
 */
@ApiModel(description = "矩阵配置")
@Data
@AccountContextFill
@TableName(value = "mpbe.sys_matrix")
public class SysMatrix {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    @TableField(value = "table_name")
    @ApiModelProperty(value = "表名")
    private String tableName;

    @TableField(value = "table_comment")
    @ApiModelProperty(value = "表注释")
    private String tableComment;

    @TableField(value = "data_source")
    @ApiModelProperty(value = "数据源名称")
    private String dataSource;

    @TableField(value = "primary_key")
    @ApiModelProperty(value = "主键字段")
    private String primaryKey;

    @TableField(value = "index_config")
    @ApiModelProperty(value = "索引配置(JSON)")
    private String indexConfig;

    @TableField(value = "engine")
    @ApiModelProperty(value = "存储引擎")
    private String engine;

    @TableField(value = "charset")
    @ApiModelProperty(value = "字符集")
    private String charset;

    @TableField(value = "status")
    @ApiModelProperty(value = "状态(0:未创建,1:已创建,2:已同步)")
    private String status;

    @TableField(value = "sort")
    @ApiModelProperty(value = "排序")
    private Integer sort;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
