package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: AcTenantApp
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-07
 */

/**
 * 租户级应用开通表——字段平移自业务仓旧表 tenant_app（旧表不读不迁不 DROP，业务侧删除在后续棒），
 * 口径变化仅一处：表下沉 core 改名 {@code ac_tenant_app}（照 {@link AcSubsystem} 同族模式）。
 * <p>逻辑主键 = UK(tenant_id, app_code)；id 仍是自增物理主键。
 * 开通判定单一真相＝{@code AcTenantAppService.activatedFor}（status/valid/start_at/expire_at 四条件）。</p>
 */
@ApiModel(description = "租户级应用开通表")
@Data
@AccountContextFill
@TableName(value = "ac_tenant_app")
public class AcTenantApp {

    /**
     * id（自增物理主键，逻辑主键是 UK(tenant_id, app_code)）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    private Long id;

    /**
     * 租户id（弱引用 ac_tenant.id，无 FK）
     */
    @TableField(value = "tenant_id")
    @ApiModelProperty(value = "租户id（ac_tenant.id）")
    private Long tenantId;

    /**
     * 应用编码（弱引用 ac_subsystem.app_code，无 FK）
     */
    @TableField(value = "app_code")
    @ApiModelProperty(value = "应用编码（ac_subsystem.app_code）")
    private String appCode;

    /**
     * 状态：ENABLED 已开通/DISABLED 已停用
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态：ENABLED 已开通/DISABLED 已停用")
    private String status;

    /**
     * 套餐编码（售卖档位，空=未分级）
     */
    @TableField(value = "plan_code")
    @ApiModelProperty(value = "套餐编码（售卖档位，空=未分级）")
    private String planCode;

    /**
     * 生效时间（空=立即生效）
     */
    @TableField(value = "start_at")
    @ApiModelProperty(value = "生效时间（空=立即生效）")
    private Date startAt;

    /**
     * 到期时间（空=长期有效）
     */
    @TableField(value = "expire_at")
    @ApiModelProperty(value = "到期时间（空=长期有效）")
    private Date expireAt;

    /**
     * 应用级配置JSON（按 app_code 语义消费）
     */
    @TableField(value = "config_json")
    @ApiModelProperty(value = "应用级配置JSON（按 app_code 语义消费）")
    private String configJson;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性（软删位：unbind 置 '0'，bind 撞 UK 复活置 '1'）
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;

}
