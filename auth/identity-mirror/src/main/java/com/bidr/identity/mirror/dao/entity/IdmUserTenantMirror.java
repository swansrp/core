package com.bidr.identity.mirror.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: IdmUserTenantMirror
 * Description: 用户-租户关系镜像表（T2.3d）——上游用户租户归属关系的全量投影。
 * <p>与上游关系表不同，本表带 surrogate 自增 {@code id}＋UK(user_id,tenant_id)（承诺面裁定）：
 * 镜像侧按 (userId,tenantId) 全量比对增删（同步幂等判据），surrogate 主键让
 * BaseSqlRepo/updateById 走通用单列主键通道，不引入复合主键形态。</p>
 * <p>🔴 G9 边界：零角色/菜单/功能码字段——上游 {@code is_admin} 一类授权语义<b>不镜像</b>
 * （IdentityMirrorConventionTest 钉）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "用户租户关系镜像表")
@Data
@TableName(value = "idm_user_tenant_mirror")
public class IdmUserTenantMirror {

    /**
     * 自增主键（镜像侧 surrogate，与上游无对应）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "自增id")
    private Long id;

    /**
     * 用户 id（＝镜像用户表 id）
     */
    @TableField(value = "user_id")
    @ApiModelProperty(value = "用户id")
    private Long userId;

    /**
     * 租户 id（＝镜像租户表 id）
     */
    @TableField(value = "tenant_id")
    @ApiModelProperty(value = "租户id")
    private Long tenantId;

    /**
     * 创建时间（关系首次落库时刻）
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新时间（快照有差异时的落库时刻）
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性（惯例列；关系行按上游全量快照增删，恒 '1'）
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
