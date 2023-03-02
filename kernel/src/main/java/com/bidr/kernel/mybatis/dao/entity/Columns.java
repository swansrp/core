package com.bidr.kernel.mybatis.dao.entity;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * Title: Columns
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/09/26 23:28
 */
@ApiModel(value = "`COLUMNS`")
@Data
@TableName("INFORMATION_SCHEMA.`COLUMNS`")
public class Columns implements Serializable {
    @TableField("TABLE_CATALOG")
    @ApiModelProperty(value = "")
    private String tableCatalog;

    @TableField("TABLE_SCHEMA")
    @ApiModelProperty(value = "")
    private String tableSchema;

    @TableField("`TABLE_NAME`")
    @ApiModelProperty(value = "")
    private String tableName;

    @TableField("`COLUMN_NAME`")
    @ApiModelProperty(value = "")
    private String columnName;

    @TableField("ORDINAL_POSITION")
    @ApiModelProperty(value = "")
    private Long ordinalPosition;

    @TableField("COLUMN_DEFAULT")
    @ApiModelProperty(value = "")
    private String columnDefault;

    @TableField("IS_NULLABLE")
    @ApiModelProperty(value = "")
    private String isNullable;

    @TableField("DATA_TYPE")
    @ApiModelProperty(value = "")
    private String dataType;

    @TableField("CHARACTER_MAXIMUM_LENGTH")
    @ApiModelProperty(value = "")
    private Long characterMaximumLength;

    @TableField("CHARACTER_OCTET_LENGTH")
    @ApiModelProperty(value = "")
    private Long characterOctetLength;

    @TableField("NUMERIC_PRECISION")
    @ApiModelProperty(value = "")
    private Long numericPrecision;

    @TableField("NUMERIC_SCALE")
    @ApiModelProperty(value = "")
    private Long numericScale;

    @TableField("DATETIME_PRECISION")
    @ApiModelProperty(value = "")
    private Long datetimePrecision;

    @TableField("`CHARACTER_SET_NAME`")
    @ApiModelProperty(value = "")
    private String characterSetName;

    @TableField("`COLLATION_NAME`")
    @ApiModelProperty(value = "")
    private String collationName;

    @TableField("COLUMN_TYPE")
    @ApiModelProperty(value = "")
    private String columnType;

    @TableField("COLUMN_KEY")
    @ApiModelProperty(value = "")
    private String columnKey;

    @TableField("EXTRA")
    @ApiModelProperty(value = "")
    private String extra;

    @TableField("`PRIVILEGES`")
    @ApiModelProperty(value = "")
    private String privileges;

    @TableField("COLUMN_COMMENT")
    @ApiModelProperty(value = "")
    private String columnComment;

    @TableField("GENERATION_EXPRESSION")
    @ApiModelProperty(value = "")
    private String generationExpression;
}
