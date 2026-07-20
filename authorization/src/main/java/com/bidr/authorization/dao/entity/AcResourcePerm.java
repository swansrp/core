package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通用资源权限表
 *
 * @author Sharp
 * @since 2026/07/20
 */
@ApiModel(value = "通用资源权限表")
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "ac_resource_perm")
public class AcResourcePerm {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    /**
     * 资源类型（表名）
     */
    @TableField(value = "resource_type")
    @ApiModelProperty(value = "资源类型（表名）")
    private String resourceType;

    /**
     * 资源ID（表主键）
     */
    @TableField(value = "resource_id")
    @ApiModelProperty(value = "资源ID（表主键）")
    private String resourceId;

    /**
     * 授权主体类型（0=角色 1=用户 2=用户组 3=部门）
     */
    @TableField(value = "subject_type")
    @ApiModelProperty(value = "授权主体类型（0=角色 1=用户 2=用户组 3=部门）")
    private Integer subjectType;

    /**
     * 主体标识（role_id / customer_number / group_id / dept_id）
     */
    @TableField(value = "subject_id")
    @ApiModelProperty(value = "主体标识")
    private String subjectId;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createAt;
}
