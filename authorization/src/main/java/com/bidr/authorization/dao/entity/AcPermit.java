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
 * Title: AcPermit
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:03
 */
@ApiModel(value="ac_permit")
@Data
@TableName(value = "ac_permit")
public class AcPermit {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value="id")
    private String id;

    /**
     * 父节点id
     */
    @TableField(value = "pid")
    @ApiModelProperty(value="父节点id")
    private String pid;

    /**
     * 标题
     */
    @TableField(value = "title")
    @ApiModelProperty(value="标题")
    private String title;

    /**
     * 图标
     */
    @TableField(value = "icon")
    @ApiModelProperty(value="图标")
    private String icon;

    /**
     * 0-菜单|1-按钮
     */
    @TableField(value = "`type`")
    @ApiModelProperty(value="0-菜单|1-按钮")
    private String type;

    /**
     * 目标地址
     */
    @TableField(value = "url")
    @ApiModelProperty(value="目标地址")
    private String url;

    /**
     * 顺序
     */
    @TableField(value = "show_order")
    @ApiModelProperty(value="顺序")
    private Integer showOrder;

    /**
     * 是否显示
     */
    @TableField(value = "`show`")
    @ApiModelProperty(value="是否显示")
    private String show;

    /**
     * 添加时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value="添加时间")
    private Date createAt;

    /**
     * 修改时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value="修改时间")
    private Date updateAt;

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value="有效性")
    private String valid;

    public static final String COL_ID = "id";

    public static final String COL_PID = "pid";

    public static final String COL_TITLE = "title";

    public static final String COL_ICON = "icon";

    public static final String COL_TYPE = "type";

    public static final String COL_URL = "url";

    public static final String COL_SHOW_ORDER = "show_order";

    public static final String COL_SHOW = "show";

    public static final String COL_CREATE_AT = "create_at";

    public static final String COL_UPDATE_AT = "update_at";

    public static final String COL_VALID = "valid";
}