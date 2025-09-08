package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel(description="erp.sys_portal_indicator_group")
@Data
@TableName(value = "erp.sys_portal_indicator_group")
public class SysPortalIndicatorGroup {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value="id")
    private Long id;

    /**
     * 父级指标id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value="父级指标id")
    private Long pid;

    /**
     * 实体名称
     */
    @TableField(value = "portal_name")
    @ApiModelProperty(value="实体名称")
    private String portalName;

    /**
     * 指标名称
     */
    @TableField(value = "`name`")
    @ApiModelProperty(value="指标名称")
    private String name;

    /**
     * 排序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value="排序")
    private Integer displayOrder;
}