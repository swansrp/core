package com.bidr.wechat.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 用户标签角色关系
 */
@ApiModel(description = "用户标签角色关系")
@Data
@TableName(value = "mm_role_tag_map")
public class MmRoleTagMap {
    /**
     * 角色id
     */
    @MppMultiId
    @ApiModelProperty(value = "角色id")
    @NotNull(message = "角色id不能为null")
    private Long roleId;

    /**
     * 标签id
     */
    @MppMultiId
    @ApiModelProperty(value = "标签id")
    @NotNull(message = "标签id不能为null")
    private Integer tagId;

    /**
     * 标签名
     */
    @TableField(value = "tag_name")
    @ApiModelProperty(value = "标签名")
    @Size(max = 50, message = "标签名最大长度要小于 50")
    private String tagName;

    /**
     * 个性菜单id
     */
    @TableField(value = "menu_id")
    @ApiModelProperty(value = "个性菜单id")
    @Size(max = 20, message = "个性菜单id最大长度要小于 20")
    private String menuId;
}