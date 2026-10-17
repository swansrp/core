package com.bidr.neo4j.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

 /**
 * Title: NeoRelationProperty
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/07 15:03
 */

/**
 * 关系属性
 */
@ApiModel(description = "关系属性")
@Data
@TableName(value = "neo_relation_property")
public class NeoRelationProperty {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 关系id
     */
    @TableField(value = "relation_id")
    @ApiModelProperty(value = "关系id")
    @NotNull(message = "关系id不能为null")
    private Long relationId;

    /**
     * 字段名
     */
    @TableField(value = "property")
    @ApiModelProperty(value = "字段名")
    @Size(max = 100, message = "字段名最大长度要小于 100")
    @NotBlank(message = "字段名不能为空")
    private String property;

    /**
     * 中文名
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "中文名")
    @Size(max = 100, message = "中文名最大长度要小于 100")
    @NotBlank(message = "中文名不能为空")
    private String name;

    /**
     * 类型(PORTAL_FIELD_DICT)
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value = "类型(PORTAL_FIELD_DICT)")
    @NotNull(message = "类型(PORTAL_FIELD_DICT)不能为null")
    private Integer type;

    /**
     * 涉及字典名称
     */
    @TableField(value = "reference")
    @ApiModelProperty(value = "涉及字典名称")
    @Size(max = 50, message = "涉及字典名称最大长度要小于 50")
    private String reference;

    /**
     * 必选
     */
    @TableField(value = "`require`")
    @ApiModelProperty(value = "必选")
    @Size(max = 1, message = "必选最大长度要小于 1")
    @NotBlank(message = "必选不能为空")
    private String require;

    /**
     * 只读
     */
    @TableField(value = "read_only")
    @ApiModelProperty(value = "只读")
    @Size(max = 1, message = "只读最大长度要小于 1")
    @NotBlank(message = "只读不能为空")
    private String readOnly;

    /**
     * 只写
     */
    @TableField(value = "write_only")
    @ApiModelProperty(value = "只写")
    @Size(max = 1, message = "只写最大长度要小于 1")
    @NotBlank(message = "只写不能为空")
    private String writeOnly;

    /**
     * 是否显示
     */
    @TableField(value = "`show`")
    @ApiModelProperty(value = "是否显示")
    @Size(max = 1, message = "是否显示最大长度要小于 1")
    @NotBlank(message = "是否显示不能为空")
    private String show;
}
