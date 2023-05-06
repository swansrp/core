package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: AcUserRole
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/28 13:38
 */

/**
 * 用户和角色关联表
 */
@ApiModel(value = "用户和角色关联表")
@Data
@TableName(value = "ac_user_role")
public class AcUserRole {
    public static final String COL_USER_ID = "user_id";
    public static final String COL_ROLE_ID = "role_id";
    /**
     * 用户ID
     */
    @MppMultiId
    @TableField(value = "user_id")
    @ApiModelProperty(value = "用户ID")
    private Long userId;
    /**
     * 角色ID
     */
    @MppMultiId
    @TableField(value = "role_id")
    @ApiModelProperty(value = "角色ID")
    private Long roleId;
}
