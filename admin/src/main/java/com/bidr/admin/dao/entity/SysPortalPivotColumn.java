package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 透视报表父表头列配置
 *
 * @author Sharp
 */
@ApiModel(description = "透视报表父表头列配置")
@Data
@TableName(value = "sys_portal_pivot_column")
public class SysPortalPivotColumn {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * table_id
     */
    @TableField(value = "table_id")
    @ApiModelProperty(value = "table_id")
    private Long tableId;

    /**
     * 列标识
     */
    @TableField(value = "item_value")
    @ApiModelProperty(value = "列标识")
    private String itemValue;

    /**
     * 表头名称
     */
    @TableField(value = "item_name")
    @ApiModelProperty(value = "表头名称")
    private String itemName;

    /**
     * 列条件json
     */
    @TableField(value = "`condition`")
    @ApiModelProperty(value = "列条件json")
    private String condition;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;

    /**
     * 状态
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "状态")
    private String status;
}
