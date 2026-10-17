package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: AcUserDept
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/04 10:27
 */

/**
 * 用户组织结构表
 */
@ApiModel(value = "用户组织结构表")
@Data
@TableName(value = "ac_user_dept")
public class AcUserDept {
    @MppMultiId
    @TableField(value = "user_id")
    @ApiModelProperty(value = "")
    private Long userId;
    @MppMultiId
    @TableField(value = "dept_id")
    @ApiModelProperty(value = "")
    private String deptId;
    /**
     * 数据权限范围
     */
    @TableField(value = "data_scope")
    @ApiModelProperty(value = "数据权限范围")
    private Integer dataScope;
}
