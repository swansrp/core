package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: AcUserTenant
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */

/**
 * 用户-租户成员关系表（复合主键，物理删——无 valid 软删位，形态照 AcUserDept）
 */
@ApiModel(value = "用户租户关系表")
@Data
@TableName(value = "ac_user_tenant")
public class AcUserTenant {
    /**
     * 用户id
     */
    @MppMultiId
    @TableField(value = "user_id")
    @ApiModelProperty(value = "用户id")
    private Long userId;

    /**
     * 租户id
     */
    @MppMultiId
    @TableField(value = "tenant_id")
    @ApiModelProperty(value = "租户id")
    private Long tenantId;

    /**
     * 租户内管理员（关系属性，非全局角色）
     */
    @TableField(value = "is_admin")
    @ApiModelProperty(value = "租户内管理员")
    private Boolean isAdmin;
}
