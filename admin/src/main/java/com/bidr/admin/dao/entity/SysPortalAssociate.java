package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 关联表格配置
 */
@ApiModel(description = "关联表格配置")
@Data
@TableName(value = "sys_portal_associate")
public class SysPortalAssociate {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * 角色id
     */
    @TableField(value = "role_id")
    @ApiModelProperty(value = "角色id")
    private Long roleId;

    /**
     * 实体id
     */
    @TableField(value = "portal_id")
    @ApiModelProperty(value = "实体id")
    private Long portalId;

    /**
     * 显示名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "显示名称")
    private String title;

    /**
     * 实体关系
     */
    @TableField(value = "bind_type")
    @ApiModelProperty(value = "实体关系")
    private String bindType;

    /**
     * 目标实体id
     */
    @TableField(value = "bind_portal_id")
    @ApiModelProperty(value = "目标实体id")
    private Long bindPortalId;

    /**
     * 关联字段名
     */
    @TableField(value = "bind_property")
    @ApiModelProperty(value = "关联字段名")
    private String bindProperty;

    /**
     * 默认排序字段
     */
    @TableField(value = "bind_sort_property")
    @ApiModelProperty(value = "默认排序字段")
    private String bindSortProperty;

    /**
     * 默认排序方式
     */
    @TableField(value = "bind_sort_type")
    @ApiModelProperty(value = "默认排序方式")
    private String bindSortType;

    /**
     * 树形展示
     */
    @TableField(value = "tree_mode")
    @ApiModelProperty(value = "树形展示")
    private String treeMode;

    /**
     * 树形结构显示是否严格节点显示
     */
    @TableField(value = "tree_check_strict")
    @ApiModelProperty(value = "树形结构显示是否严格节点显示")
    private String treeCheckStrict;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;
}