package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: AcRole
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:02
 */
@ApiModel(value = "角色信息表")
@Data
@TableName(value = "ac_role")
public class AcRole {
    public static final String COL_ROLE_ID = "role_id";
    public static final String COL_ROLE_NAME = "role_name";
    public static final String COL_ROLE_KEY = "role_key";
    public static final String COL_ROLE_SORT = "role_sort";
    public static final String COL_DATA_SCOPE = "data_scope";
    public static final String COL_MENU_CHECK_STRICTLY = "menu_check_strictly";
    public static final String COL_DEPT_CHECK_STRICTLY = "dept_check_strictly";
    public static final String COL_STATUS = "status";
    public static final String COL_CREATE_BY = "create_by";
    public static final String COL_CREATE_TIME = "create_time";
    public static final String COL_UPDATE_BY = "update_by";
    public static final String COL_UPDATE_TIME = "update_time";
    public static final String COL_REMARK = "remark";
    public static final String COL_VALID = "valid";
    /**
     * 角色ID
     */
    @TableId(value = "role_id", type = IdType.INPUT)
    @ApiModelProperty(value = "角色ID")
    private Long roleId;
    /**
     * 角色名称
     */
    @TableField(value = "role_name")
    @ApiModelProperty(value = "角色名称")
    private String roleName;
    /**
     * 角色权限字符串
     */
    @TableField(value = "role_key")
    @ApiModelProperty(value = "角色权限字符串")
    private String roleKey;
    /**
     * 显示顺序
     */
    @TableField(value = "role_sort")
    @ApiModelProperty(value = "显示顺序")
    private Integer roleSort;
    /**
     * 数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）
     */
    @TableField(value = "data_scope")
    @ApiModelProperty(value = "数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）")
    private String dataScope;
    /**
     * 菜单树选择项是否关联显示
     */
    @TableField(value = "menu_check_strictly")
    @ApiModelProperty(value = "菜单树选择项是否关联显示")
    private Boolean menuCheckStrictly;
    /**
     * 部门树选择项是否关联显示
     */
    @TableField(value = "dept_check_strictly")
    @ApiModelProperty(value = "部门树选择项是否关联显示")
    private Boolean deptCheckStrictly;
    /**
     * 角色状态（0正常 1停用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "角色状态（0正常 1停用）")
    private String status;
    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;
    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;
    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private String updateBy;
    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;
    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    private String remark;
    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
