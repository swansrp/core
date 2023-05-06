package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: AcRoleDept
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:02
 */
@ApiModel(value = "角色和部门关联表")
@Data
@TableName(value = "ac_role_dept")
public class AcRoleDept {
    public static final String COL_ROLE_ID = "role_id";
    public static final String COL_DEPT_ID = "dept_id";
    /**
     * 角色ID
     */
    @MppMultiId
    @TableField(value = "role_id")
    @ApiModelProperty(value = "角色ID")
    private Long roleId;
    /**
     * 部门ID
     */
    @MppMultiId
    @TableField(value = "dept_id")
    @ApiModelProperty(value = "部门ID")
    private Long deptId;
}
