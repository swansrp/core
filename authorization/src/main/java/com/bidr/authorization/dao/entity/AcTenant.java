package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: AcTenant
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */

/**
 * 租户目录表（valid 为软删位，code 索引故意非唯一——撤销后再启用同 code 不撞键，同 AcSsoRedirect 定案）
 */
@ApiModel(value = "租户目录表")
@Data
@TableName(value = "ac_tenant")
@AccountContextFill
public class AcTenant {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 租户名称
     */
    @TableField(value = "name")
    @ApiModelProperty(value = "租户名称")
    @Size(max = 100, message = "租户名称最大长度要小于 100")
    @NotBlank(message = "租户名称不能为空")
    private String name;

    /**
     * 业务编码（租户对外可读标识）
     */
    @TableField(value = "code")
    @ApiModelProperty(value = "业务编码")
    @Size(max = 50, message = "业务编码最大长度要小于 50")
    private String code;

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
     * 有效性（软删位：停用＝置0，再启用＝插新行）
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    @Size(max = 1, message = "有效性最大长度要小于 1")
    private String valid;
}
