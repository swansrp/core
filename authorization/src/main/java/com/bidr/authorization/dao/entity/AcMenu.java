package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import lombok.Data;

 /**
 * Title: AcMenu
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/27 13:29
 */

/**
 * 菜单权限表
 */
@ApiModel(value = "菜单权限表")
@Data
@TableName(value = "ac_menu")
public class AcMenu {
    public static final String COL_MENU_NAME = "menu_name";
    /**
     * 菜单ID
     */
    @TableId(value = "menu_id", type = IdType.AUTO)
    @ApiModelProperty(value = "菜单ID")
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
     * 菜单名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "菜单名称")
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
    private String path;

    /**
     * 组件路径
     */
    @TableField(value = "component")
    @ApiModelProperty(value = "组件路径")
    private String component;

    /**
     * 路由参数
     */
    @TableField(value = "query")
    @ApiModelProperty(value = "路由参数")
    private String query;

    /**
     * 是否为外链（0是 1否）
     */
    @TableField(value = "is_frame")
    @ApiModelProperty(value = "是否为外链（0是 1否）")
    private String isFrame;

    /**
     * 是否缓存（0缓存 1不缓存）
     */
    @TableField(value = "is_cache")
    @ApiModelProperty(value = "是否缓存（0缓存 1不缓存）")
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
    private String visible;

    /**
     * 菜单状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "菜单状态（0正常 1停用）")
    private String status;

    /**
     * 权限标识
     */
    @TableField(value = "perms")
    @ApiModelProperty(value = "权限标识")
    private String perms;

    /**
     * 菜单图标
     */
    @TableField(value = "icon")
    @ApiModelProperty(value = "菜单图标")
    private String icon;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public static final String COL_MENU_ID = "menu_id";

    public static final String COL_PID = "pid";

    public static final String COL_GRAND_ID = "grand_id";

    public static final String COL_KEY = "key";

    public static final String COL_TITLE = "title";

    public static final String COL_SHOW_ORDER = "show_order";

    public static final String COL_PATH = "path";

    public static final String COL_COMPONENT = "component";

    public static final String COL_QUERY = "query";

    public static final String COL_IS_FRAME = "is_frame";

    public static final String COL_IS_CACHE = "is_cache";

    public static final String COL_MENU_TYPE = "menu_type";

    public static final String COL_VISIBLE = "visible";

    public static final String COL_STATUS = "status";

    public static final String COL_PERMS = "perms";

    public static final String COL_ICON = "icon";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_CREATE_TIME = "create_time";

    public static final String COL_UPDATE_BY = "update_by";

    public static final String COL_UPDATE_TIME = "update_time";

    public static final String COL_REMARK = "remark";
}