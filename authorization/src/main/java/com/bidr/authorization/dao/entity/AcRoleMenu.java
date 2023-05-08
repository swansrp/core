package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: AcRoleMenu
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@ApiModel(value = "角色和菜单关联表")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "ac_role_menu")
public class AcRoleMenu {
    public static final String COL_ROLE_ID = "role_id";
    public static final String COL_MENU_ID = "menu_id";
    /**
     * 角色ID
     */
    @MppMultiId
    @TableField(value = "role_id")
    @ApiModelProperty(value = "角色ID")
    private Long roleId;
    /**
     * 菜单ID
     */
    @MppMultiId
    @TableField(value = "menu_id")
    @ApiModelProperty(value = "菜单ID")
    private Long menuId;
}
