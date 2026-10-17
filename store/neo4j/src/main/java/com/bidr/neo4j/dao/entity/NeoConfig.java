package com.bidr.neo4j.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

 /**
 * Title: NeoConfig
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/11 15:32
 */

/**
 * 图数据库关系配置
 */
@ApiModel(description = "图数据库关系配置")
@Data
@TableName(value = "neo_config")
public class NeoConfig {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 起始节点id
     */
    @TableField(value = "start_id")
    @ApiModelProperty(value = "起始节点id")
    @NotNull(message = "起始节点id不能为null")
    private Long startId;

    /**
     * 结束节点id
     */
    @TableField(value = "end_id")
    @ApiModelProperty(value = "结束节点id")
    @NotNull(message = "结束节点id不能为null")
    private Long endId;

    /**
     * 其他参与节点(逗号隔开)
     */
    @TableField(value = "extra_node_list")
    @ApiModelProperty(value = "其他参与节点(逗号隔开)")
    @Size(max = 200, message = "其他参与节点(逗号隔开)最大长度要小于 200")
    private String extraNodeList;

    /**
     * 关系条件
     */
    @TableField(value = "`condition`")
    @ApiModelProperty(value = "关系条件")
    private String condition;

    /**
     * 关系类型id
     */
    @TableField(value = "relation_id")
    @ApiModelProperty(value = "关系类型id")
    @NotNull(message = "关系类型id不能为null")
    private Long relationId;
}
