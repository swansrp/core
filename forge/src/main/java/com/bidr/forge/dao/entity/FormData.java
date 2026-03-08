package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单填写数据表
 *
 * @author sharp
 */
@ApiModel(description = "表单填写数据表")
@Data
@AccountContextFill
@TableName(value = "form_data")
public class FormData {
    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "主键 ID")
    private String id;

    /**
     * 上传历史 ID
     */
    @TableField(value = "history_id")
    @ApiModelProperty(value = "上传历史 ID")
    private String historyId;

    /**
     * 区块实例 ID
     */
    @TableField(value = "section_instance_id")
    @ApiModelProperty(value = "区块实例 ID")
    private String sectionInstanceId;

    /**
     * 组实例 ID
     */
    @TableField(value = "group_instance_id")
    @ApiModelProperty(value = "组实例 ID")
    private String groupInstanceId;

    /**
     * 字段 ID
     */
    @TableField(value = "attribute_id")
    @ApiModelProperty(value = "字段 ID")
    private Long attributeId;

    /**
     * 企业填写的值
     */
    @TableField(value = "value")
    @ApiModelProperty(value = "企业填写的值")
    private String value;

    /**
     * 表单版本号
     */
    @TableField(value = "version")
    @ApiModelProperty(value = "表单版本号")
    private Integer version;

    /**
     * 状态：1=有效，0=无效
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态：1=有效，0=无效")
    private Integer status;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
