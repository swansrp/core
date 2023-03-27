package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: AcUserRole
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:02
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
    @MppMultiId(value = "user_id")
    @ApiModelProperty(value = "用户ID")
    private Long userId;
    /**
     * 角色ID
     */
    @MppMultiId(value = "role_id")
    @ApiModelProperty(value = "角色ID")
    private Long roleId;
}
