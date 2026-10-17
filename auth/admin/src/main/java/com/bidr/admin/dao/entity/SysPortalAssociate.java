package com.bidr.admin.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
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
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 角色id
     */
    @TableField(value = "role_id")
    @ApiModelProperty(value = "角色id")
    @NotNull(message = "角色id不能为null")
    private Long roleId;

    /**
     * 实体id
     */
    @TableField(value = "portal_id")
    @ApiModelProperty(value = "实体id")
    @NotNull(message = "实体id不能为null")
    private Long portalId;

    /**
     * 显示名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "显示名称")
    @Size(max = 50, message = "显示名称最大长度要小于 50")
    @NotBlank(message = "显示名称不能为空")
    private String title;

    /**
     * 实体关系
     */
    @TableField(value = "bind_type")
    @ApiModelProperty(value = "实体关系")
    @Size(max = 1, message = "实体关系最大长度要小于 1")
    @NotBlank(message = "实体关系不能为空")
    private String bindType;

    /**
     * 目标实体id
     */
    @TableField(value = "bind_portal_id")
    @ApiModelProperty(value = "目标实体id")
    @NotNull(message = "目标实体id不能为null")
    private Long bindPortalId;

    /**
     * 关联字段名
     */
    @TableField(value = "bind_property")
    @ApiModelProperty(value = "关联字段名")
    @Size(max = 50, message = "关联字段名最大长度要小于 50")
    @NotBlank(message = "关联字段名不能为空")
    private String bindProperty;

    /**
     * 默认排序字段
     */
    @TableField(value = "bind_sort_property")
    @ApiModelProperty(value = "默认排序字段")
    @Size(max = 50, message = "默认排序字段最大长度要小于 50")
    @NotBlank(message = "默认排序字段不能为空")
    private String bindSortProperty;

    /**
     * 默认排序方式
     */
    @TableField(value = "bind_sort_type")
    @ApiModelProperty(value = "默认排序方式")
    @Size(max = 1, message = "默认排序方式最大长度要小于 1")
    @NotBlank(message = "默认排序方式不能为空")
    private String bindSortType;

    /**
     * 树形展示
     */
    @TableField(value = "tree_mode")
    @ApiModelProperty(value = "树形展示")
    @Size(max = 1, message = "树形展示最大长度要小于 1")
    @NotBlank(message = "树形展示不能为空")
    private String treeMode;

    /**
     * 树形结构显示是否严格节点显示
     */
    @TableField(value = "tree_check_strict")
    @ApiModelProperty(value = "树形结构显示是否严格节点显示")
    @Size(max = 1, message = "树形结构显示是否严格节点显示最大长度要小于 1")
    @NotBlank(message = "树形结构显示是否严格节点显示不能为空")
    private String treeCheckStrict;

    /**
     * 关联实体查询条件
     */
    @TableField(value = "attach_condition")
    @ApiModelProperty(value = "关联实体查询条件")
    private String attachCondition;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    @NotNull(message = "显示顺序不能为null")
    private Integer displayOrder;
}