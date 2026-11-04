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
 * Title: IdmTenantMirror
 * Description: 租户身份镜像表（T2.3d）——上游租户目录表的最小身份投影
 * （id＋tenant_code＋tenant_name）。
 * <p>🔴 G9 边界：零角色/菜单/功能码字段（IdentityMirrorConventionTest 钉）。
 * {@code id} 非自增＝上游租户 id；{@code tenant_code} 唯一（镜像侧业务键）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "租户身份镜像表")
@Data
@TableName(value = "idm_tenant_mirror")
public class IdmTenantMirror {

    /**
     * 租户 id（＝上游租户 id，非自增）
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "租户id（上游id）")
    private Long id;

    /**
     * 租户业务编码（镜像侧唯一键）
     */
    @TableField(value = "tenant_code")
    @ApiModelProperty(value = "租户业务编码")
    private String tenantCode;

    /**
     * 租户名称
     */
    @TableField(value = "tenant_name")
    @ApiModelProperty(value = "租户名称")
    private String tenantName;

    /**
     * 创建时间（快照首次落库时刻）
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
     * 有效性（惯例软删位，镜像行随上游全量快照增删，恒 '1'）
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
