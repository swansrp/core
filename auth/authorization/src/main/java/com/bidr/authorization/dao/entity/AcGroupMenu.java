package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户组和菜单关联表
 *
 * @author sharp
 */
@ApiModel(description = "用户组和菜单关联表")
@Data
@TableName(value = "ac_group_menu")
public class AcGroupMenu {
    /**
     * 用户组ID
     */
    @MppMultiId
    @TableField(value = "group_id")
    @ApiModelProperty(value = "用户组ID")
    private Long groupId;

    /**
     * 菜单ID
     */
    @MppMultiId
    @TableField(value = "menu_id")
    @ApiModelProperty(value = "菜单ID")
    private Long menuId;
}
