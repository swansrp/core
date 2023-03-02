package com.bidr.kernel.mybatis.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Title: Tables
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/09/26 23:28
 */
@ApiModel(value = "`TABLES`")
@Data
@TableName("INFORMATION_SCHEMA.`TABLES`")
public class Tables implements Serializable {
    @TableField("TABLE_CATALOG")
    @ApiModelProperty(value = "")
    private String tableCatalog;

    @TableField("TABLE_SCHEMA")
    @ApiModelProperty(value = "")
    private String tableSchema;

    @TableField("`TABLE_NAME`")
    @ApiModelProperty(value = "")
    private String tableName;

    @TableField("TABLE_TYPE")
    @ApiModelProperty(value = "")
    private String tableType;

    @TableField("ENGINE")
    @ApiModelProperty(value = "")
    private String engine;

    @TableField("VERSION")
    @ApiModelProperty(value = "")
    private Long version;

    @TableField("ROW_FORMAT")
    @ApiModelProperty(value = "")
    private String rowFormat;

    @TableField("TABLE_ROWS")
    @ApiModelProperty(value = "")
    private Long tableRows;

    @TableField("`AVG_ROW_LENGTH`")
    @ApiModelProperty(value = "")
    private Long avgRowLength;

    @TableField("DATA_LENGTH")
    @ApiModelProperty(value = "")
    private Long dataLength;

    @TableField("MAX_DATA_LENGTH")
    @ApiModelProperty(value = "")
    private Long maxDataLength;

    @TableField("INDEX_LENGTH")
    @ApiModelProperty(value = "")
    private Long indexLength;

    @TableField("DATA_FREE")
    @ApiModelProperty(value = "")
    private Long dataFree;

    @TableField("`AUTO_INCREMENT`")
    @ApiModelProperty(value = "")
    private Long autoIncrement;

    @TableField("CREATE_TIME")
    @ApiModelProperty(value = "")
    private Date createTime;

    @TableField("UPDATE_TIME")
    @ApiModelProperty(value = "")
    private Date updateTime;

    @TableField("CHECK_TIME")
    @ApiModelProperty(value = "")
    private Date checkTime;

    @TableField("TABLE_COLLATION")
    @ApiModelProperty(value = "")
    private String tableCollation;

    @TableField("`CHECKSUM`")
    @ApiModelProperty(value = "")
    private Long checksum;

    @TableField("CREATE_OPTIONS")
    @ApiModelProperty(value = "")
    private String createOptions;

    @TableField("TABLE_COMMENT")
    @ApiModelProperty(value = "")
    private String tableComment;
}
