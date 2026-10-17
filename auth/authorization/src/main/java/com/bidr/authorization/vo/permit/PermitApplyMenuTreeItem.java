package com.bidr.authorization.vo.permit;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Title: PermitApplyMenuTreeItem
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/6 22:51
 */
@Data
public class PermitApplyMenuTreeItem {

    @ApiModelProperty(value = "待审批数量")
    private Long waitApproveCount;

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
     * key与菜单ID一致
     */
    @TableField(value = "`key`")
    @ApiModelProperty(value = "key与菜单ID一致")
    private Long key;

    /**
     * 祖父ID
     */
    @TableField(value = "grand_id")
    @ApiModelProperty(value = "祖父ID")
    private Long grandId;

    /**
     * 菜单名称
     */
    @TableField(value = "title")
    @ApiModelProperty(value = "菜单名称")
    @Size(max = 50, message = "菜单名称最大长度要小于 50")
    @NotBlank(message = "菜单名称不能为空")
    private String title;
}