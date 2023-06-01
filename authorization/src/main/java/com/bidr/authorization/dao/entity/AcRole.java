package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

 /**
 * Title: AcRole
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 10:15
 */

/**
 * 角色信息表
 */
@ApiModel(description = "角色信息表")
@Data
@TableName(value = "ac_role")
public class AcRole {
    /**
     * 角色ID
     */
    @TableId(value = "role_id", type = IdType.AUTO)
    @ApiModelProperty(value = "角色ID")
    @NotNull(message = "角色ID不能为null")
    private Long roleId;

    /**
     * 角色名称
     */
    @TableField(value = "role_name")
    @ApiModelProperty(value = "角色名称")
    @Size(max = 30, message = "角色名称最大长度要小于 30")
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    /**
     * 角色权限字符串
     */
    @TableField(value = "role_key")
    @ApiModelProperty(value = "角色权限字符串")
    @Size(max = 100, message = "角色权限字符串最大长度要小于 100")
    private String roleKey;

    /**
     * 角色状态（1正常 0停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "角色状态（1正常 0停用）")
    @NotNull(message = "角色状态（1正常 0停用）不能为null")
    private Integer status;

    /**
     * 显示顺序
     */
    @TableField(value = "display_order")
    @ApiModelProperty(value = "显示顺序")
    private Integer displayOrder;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private Long createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private Long updateBy;

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

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    @Size(max = 1, message = "有效性最大长度要小于 1")
    private String valid;

    public static final String COL_ROLE_ID = "role_id";

    public static final String COL_ROLE_NAME = "role_name";

    public static final String COL_ROLE_KEY = "role_key";

    public static final String COL_STATUS = "status";

    public static final String COL_DISPLAY_ORDER = "display_order";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_CREATE_AT = "create_at";

    public static final String COL_UPDATE_BY = "update_by";

    public static final String COL_UPDATE_AT = "update_at";

    public static final String COL_REMARK = "remark";

    public static final String COL_VALID = "valid";
}
