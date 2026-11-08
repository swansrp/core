package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: AcSubsystem
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-06
 */

/**
 * 子系统注册表（门户入口清单）——字段平移自业务仓旧表（棒7 将删旧表），
 * 仅两处口径变化（§8.1 裁定）：①表下沉 core 改名 {@code ac_subsystem}；
 * ②{@code sso_app_id}（存 ac_partner.app_key 字符串）改为 {@code partner_id}
 * （存 {@code ac_partner.id}，弱引用无 FK/实体关联——照抄 {@link AcSsoRedirect} 模式）。
 * 不迁移老数据：旧表不读不迁不 DROP（业务侧删除在棒7）。
 */
@ApiModel(description = "子系统注册表")
@Data
@TableName(value = "ac_subsystem")
public class AcSubsystem {
    /**
     * 应用编码（业务主键，门户入口/功能码前缀都按它）
     */
    @TableId(value = "app_code", type = IdType.INPUT)
    @ApiModelProperty(value = "应用编码（业务主键）")
    @Size(max = 64, message = "应用编码最大长度要小于 64")
    @NotBlank(message = "应用编码不能为空")
    private String appCode;

    /**
     * 子系统显示名
     */
    @TableField(value = "name")
    @ApiModelProperty(value = "子系统显示名")
    @Size(max = 100, message = "子系统显示名最大长度要小于 100")
    @NotBlank(message = "子系统显示名不能为空")
    private String name;

    /**
     * 图标
     */
    @TableField(value = "icon")
    @ApiModelProperty(value = "图标")
    @Size(max = 200, message = "图标最大长度要小于 200")
    private String icon;

    /**
     * 门户排序
     */
    @TableField(value = "order_no")
    @ApiModelProperty(value = "门户排序")
    private Integer orderNo;

    /**
     * 入口形态（iframe/新窗口/同页）
     */
    @TableField(value = "entry_type")
    @ApiModelProperty(value = "入口形态（iframe/新窗口/同页）")
    @Size(max = 32, message = "入口形态最大长度要小于 32")
    private String entryType;

    /**
     * 入口地址
     */
    @TableField(value = "entry_url")
    @ApiModelProperty(value = "入口地址")
    @Size(max = 500, message = "入口地址最大长度要小于 500")
    private String entryUrl;

    /**
     * 归属partner（ac_partner 口径 id；弱引用无 FK，uk_partner_id＝一 partner 至多挂一子系统）
     */
    @TableField(value = "partner_id")
    @ApiModelProperty(value = "归属partner（ac_partner.id）")
    private Long partnerId;

    /**
     * 是否对所有租户可见：1=公开目录（只读展示，不扩写权）/0=按开通与授权判定
     */
    @TableField(value = "visible_to_all_tenant")
    @ApiModelProperty(value = "是否对所有租户可见（1=公开目录/0=按开通与授权判定）")
    @Size(max = 1, message = "是否对所有租户可见最大长度要小于 1")
    private String visibleToAllTenant;

    /**
     * 子系统后台地址（管理入口）
     */
    @TableField(value = "admin_url")
    @ApiModelProperty(value = "子系统后台地址（管理入口）")
    @Size(max = 500, message = "子系统后台地址最大长度要小于 500")
    private String adminUrl;

    /**
     * 状态（1=启用）
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "状态（1=启用）")
    @Size(max = 20, message = "状态最大长度要小于 20")
    private String status;

    /**
     * 说明
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "说明")
    @Size(max = 500, message = "说明最大长度要小于 500")
    private String description;

    /**
     * 创建者
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    @Size(max = 50, message = "创建者最大长度要小于 50")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    @Size(max = 50, message = "更新者最大长度要小于 50")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
