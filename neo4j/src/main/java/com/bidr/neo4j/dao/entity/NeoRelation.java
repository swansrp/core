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
 * Title: NeoRelation
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/07 11:35
 */

/**
 * 关系节点
 */
@ApiModel(description = "关系节点")
@Data
@TableName(value = "neo_relation")
public class NeoRelation {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 关系类型
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value = "关系类型")
    @Size(max = 50, message = "关系类型最大长度要小于 50")
    @NotBlank(message = "关系类型不能为空")
    private String type;
}
