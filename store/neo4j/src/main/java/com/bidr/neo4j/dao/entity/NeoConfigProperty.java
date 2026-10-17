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
 * Title: NeoConfigProperty
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/07/07 15:05
 */

/**
 * 关系配置属性
 */
@ApiModel(description = "关系配置属性")
@Data
@TableName(value = "neo_config_property")
public class NeoConfigProperty {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 关系配置id
     */
    @TableField(value = "config_id")
    @ApiModelProperty(value = "关系配置id")
    @NotNull(message = "关系配置id不能为null")
    private Long configId;

    /**
     * 对应关系属性id
     */
    @TableField(value = "relation_property_id")
    @ApiModelProperty(value = "对应关系属性id")
    @NotNull(message = "对应关系属性id不能为null")
    private Long relationPropertyId;

    /**
     * 对应属性值
     */
    @TableField(value = "`value`")
    @ApiModelProperty(value = "对应属性值")
    @Size(max = 100, message = "对应属性值最大长度要小于 100")
    @NotBlank(message = "对应属性值不能为空")
    private String value;
}
