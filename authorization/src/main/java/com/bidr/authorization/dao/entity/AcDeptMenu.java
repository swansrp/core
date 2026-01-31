package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 部门和菜单关联表
 *
 * @author sharp
 */
@ApiModel(description = "部门和菜单关联表")
@Data
@TableName(value = "ac_dept_menu")
public class AcDeptMenu {
    /**
     * 部门ID
     */
    @MppMultiId
    @TableField(value = "dept_id")
    @ApiModelProperty(value = "部门ID")
    private Long deptId;

    /**
     * 菜单ID
     */
    @MppMultiId
    @TableField(value = "menu_id")
    @ApiModelProperty(value = "菜单ID")
    private Long menuId;
}
