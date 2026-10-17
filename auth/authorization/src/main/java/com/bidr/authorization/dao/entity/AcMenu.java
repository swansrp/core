package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

 /**
 * Title: AcMenu
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/29 16:52
 */

/**
 * 菜单权限表
 */
@ApiModel(description = "菜单权限表")
@Data
@TableName(value = "ac_menu")
@AccountContextFill
public class AcMenu {
    /**
     * 菜单ID
     */
    @TableId(value = "menu_id", type = IdType.AUTO)
    @ApiModelProperty(value = "菜单ID")
    @NotNull(message = "菜单ID不能为null")
    private Long menuId;

    /**
     * 父菜单ID
     */
    @TableField(value = "pid")
    @ApiModelProperty(value = "父菜单ID")
    private Long pid;

    /**
     * 祖父ID
     */
    @TableField(value = "grand_id")
    @ApiModelProperty(value = "祖父ID")
    private Long grandId;

    /**
     * key与菜单ID一致
     */
    @TableField(value = "`key`")
    @ApiModelProperty(value = "key与菜单ID一致")
    private Long key;

    /**
     * 祖级列表
     */
    @TableField(value = "ancestors")
    @ApiModelProperty(value = "祖级列表")
    @Size(max = 50, message = "祖级列表最大长度要小于 50")
    @NotBlank(message = "祖级列表不能为空")
    private String ancestors;

    /**
     * 客户端类型
     */
    @TableField(value = "client_type")
    @ApiModelProperty(value = "客户端类型")
    @Size(max = 2, message = "客户端类型最大长度要小于 2")
    @NotBlank(message = "客户端类型不能为空")
    private String clientType;

    /**
     * 菜单名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "菜单名称")
    @Size(max = 50, message = "菜单名称最大长度要小于 50")
    @NotBlank(message = "菜单名称不能为空")
    private String title;

    /**
     * 显示顺序
     */
    @TableField(value = "show_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer showOrder;

    /**
     * 路由地址
     */
    @TableField(value = "`path`")
    @ApiModelProperty(value = "路由地址")
    @Size(max = 200, message = "路由地址最大长度要小于 200")
    private String path;

    /**
     * 组件路径
     */
    @TableField(value = "component")
    @ApiModelProperty(value = "组件路径")
    @Size(max = 255, message = "组件路径最大长度要小于 255")
    private String component;

    /**
     * 路由参数
     */
    @TableField(value = "query")
    @ApiModelProperty(value = "路由参数")
    @Size(max = 255, message = "路由参数最大长度要小于 255")
    private String query;

    /**
     * 是否为外链（0是 1否）
     */
    @TableField(value = "is_frame")
    @ApiModelProperty(value = "是否为外链（0是 1否）")
    @Size(max = 50, message = "是否为外链（0是 1否）最大长度要小于 50")
    private String isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    @TableField(value = "is_cache")
    @ApiModelProperty(value = "是否缓存（0缓存 1不缓存）")
    @Size(max = 50, message = "是否缓存（0缓存 1不缓存）最大长度要小于 50")
    private String isCache;

    /**
     * 菜单类型MENU_TYPE_DICT
     */
    @TableField(value = "menu_type")
    @ApiModelProperty(value = "菜单类型MENU_TYPE_DICT")
    private Integer menuType;

    /**
     * 菜单状态（0显示 1隐藏）
     */
    @TableField(value = "visible")
    @ApiModelProperty(value = "菜单状态（0显示 1隐藏）")
    @Size(max = 1, message = "菜单状态（0显示 1隐藏）最大长度要小于 1")
    private String visible;

    /**
     * 菜单状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "菜单状态（0正常 1停用）")
    @Size(max = 1, message = "菜单状态（0正常 1停用）最大长度要小于 1")
    private String status;

    /**
     * 权限标识
     */
    @TableField(value = "perms")
    @ApiModelProperty(value = "权限标识")
    @Size(max = 100, message = "权限标识最大长度要小于 100")
    private String perms;

    /**
     * 菜单图标
     */
    @TableField(value = "icon")
    @ApiModelProperty(value = "菜单图标")
    @Size(max = 100, message = "菜单图标最大长度要小于 100")
    private String icon;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    @Size(max = 50, message = "创建者最大长度要小于 50")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    @Size(max = 50, message = "更新者最大长度要小于 50")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;
}
