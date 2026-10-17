package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: AcSsoRedirect
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */

/**
 * SSO回调地址白名单（判据＝(partner_id, redirect_uri) 组合全串等值；valid 为软删位，索引故意非唯一）
 */
@ApiModel(description = "SSO回调地址白名单")
@Data
@TableName(value = "ac_sso_redirect")
public class AcSsoRedirect {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 归属partner（ac_partner 口径 id；一 partner 可登记多行，预发/生产各一行）
     */
    @TableField(value = "partner_id")
    @ApiModelProperty(value = "归属partner")
    @NotNull(message = "归属partner不能为null")
    private Long partnerId;

    /**
     * 回调地址（命中判定＝入参全串等值，禁前缀/like 匹配防子域绕过）
     */
    @TableField(value = "redirect_uri")
    @ApiModelProperty(value = "回调地址")
    @Size(max = 500, message = "回调地址最大长度要小于 500")
    @NotBlank(message = "回调地址不能为空")
    private String redirectUri;

    /**
     * 登记备注（用途/环境说明）
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "登记备注")
    @Size(max = 200, message = "登记备注最大长度要小于 200")
    private String remark;

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

    /**
     * 有效性（软删位：撤销登记＝置0，再登记＝插新行）
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    @Size(max = 1, message = "有效性最大长度要小于 1")
    private String valid;
}
