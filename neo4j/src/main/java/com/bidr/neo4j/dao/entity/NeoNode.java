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
 * Title: NeoNode
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/11 09:17
 */

/**
 * 节点
 */
@ApiModel(description = "节点")
@Data
@TableName(value = "neo_node")
public class NeoNode {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 名称
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value = "名称")
    @Size(max = 50, message = "名称最大长度要小于 50")
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 节点名称
     */
    @TableField(value = "`label`")
    @ApiModelProperty(value = "节点名称")
    @Size(max = 50, message = "节点名称最大长度要小于 50")
    @NotBlank(message = "节点名称不能为空")
    private String label;

    /**
     * 对应表名
     */
    @TableField(value = "`table_name`")
    @ApiModelProperty(value = "对应表名")
    @Size(max = 100, message = "对应表名最大长度要小于 100")
    @NotBlank(message = "对应表名不能为空")
    private String tableName;
}
