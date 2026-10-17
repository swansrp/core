package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 用户和菜单关联表（权限直授）
 *
 * @author sharp
 */
@ApiModel(description = "用户和菜单关联表")
@Data
@TableName(value = "ac_user_menu")
public class AcUserMenu {
    /**
     * 用户ID
     */
    @MppMultiId
    @TableField(value = "customer_number")
    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    /**
     * 菜单ID
     */
    @MppMultiId
    @TableField(value = "menu_id")
    @ApiModelProperty(value = "菜单ID")
    private Long menuId;
}
