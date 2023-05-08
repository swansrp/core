package com.bidr.authorization.bo.permit;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: PermitInfo
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:07
 */
@Data
public class PermitInfo {

    @ApiModelProperty(value = "菜单ID")
    private Long menuId;

    @ApiModelProperty(value = "父菜单ID")
    private Long pid;

    @ApiModelProperty(value = "祖父ID")
    private Long grandId;

    @ApiModelProperty(value = "key与菜单ID一致")
    private Long key;

    @ApiModelProperty(value = "菜单名称")
    private String title;

    @ApiModelProperty(value = "显示顺序")
    private Integer showOrder;

    @ApiModelProperty(value = "路由地址")
    private String path;

    @ApiModelProperty(value = "组件路径")
    private String component;

    @ApiModelProperty(value = "路由参数")
    private String query;

    @ApiModelProperty(value = "是否为外链（0是 1否）")
    private String isFrame;

    @ApiModelProperty(value = "是否缓存（0缓存 1不缓存）")
    private String isCache;

    @ApiModelProperty(value = "菜单类型MENU_TYPE_DICT")
    private Integer menuType;

    @ApiModelProperty(value = "菜单状态（0显示 1隐藏）")
    private String visible;

    @ApiModelProperty(value = "菜单状态（0正常 1停用）")
    private String status;

    @ApiModelProperty(value = "权限标识")
    private String perms;

    @ApiModelProperty(value = "菜单图标")
    private String icon;
}
