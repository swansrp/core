package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

 /**
 * Title: AcUserGroup
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/07 20:33
 */

/**
 * 用户组群关系
 */
@ApiModel(description = "用户组群关系")
@Data
@TableName(value = "ac_user_group")
public class AcUserGroup {
    @MppMultiId
    @TableField(value = "user_id")
    @ApiModelProperty(value = "")
    private Long userId;

    @MppMultiId
    @TableField(value = "group_id")
    @ApiModelProperty(value = "")
    private Long groupId;

    /**
     * 数据权限范围
     */
    @TableField(value = "data_scope")
    @ApiModelProperty(value = "数据权限范围")
    private Integer dataScope;

    public static final String COL_USER_ID = "user_id";

    public static final String COL_GROUP_ID = "group_id";

    public static final String COL_DATA_SCOPE = "data_scope";
}
