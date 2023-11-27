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
 * Title: NeoNodeSync
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/07 15:58
 */

/**
 * 节点同步原则
 */
@ApiModel(description = "节点同步原则")
@Data
@TableName(value = "neo_node_sync")
public class NeoNodeSync {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 节点id
     */
    @TableField(value = "node_id")
    @ApiModelProperty(value = "节点id")
    @NotNull(message = "节点id不能为null")
    private Long nodeId;

    /**
     * 源数据库字段名
     */
    @TableField(value = "property")
    @ApiModelProperty(value = "源数据库字段名")
    @Size(max = 50, message = "源数据库字段名最大长度要小于 50")
    @NotBlank(message = "源数据库字段名不能为空")
    private String property;

    /**
     * 关系(PORTAL_CONDITION_DICT)
     */
    @TableField(value = "relation")
    @ApiModelProperty(value = "关系(PORTAL_CONDITION_DICT)")
    @NotNull(message = "关系(PORTAL_CONDITION_DICT)不能为null")
    private Integer relation;

    /**
     * 值逗号隔开
     */
    @TableField(value = "`value`")
    @ApiModelProperty(value = "值逗号隔开")
    @Size(max = 500, message = "值逗号隔开最大长度要小于 500")
    private String value;

    /**
     * 时间格式
     */
    @TableField(value = "date_format")
    @ApiModelProperty(value = "时间格式")
    @Size(max = 20, message = "时间格式最大长度要小于 20")
    private String dateFormat;
}
