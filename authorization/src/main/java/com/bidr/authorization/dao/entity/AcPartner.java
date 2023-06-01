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
 * Title: AcPartner
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/31 08:58
 */

/**
 * 三方应用对接
 */
@ApiModel(description = "三方应用对接")
@Data
@TableName(value = "ac_partner")
public class AcPartner {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id")
    @NotNull(message = "id不能为null")
    private Long id;

    /**
     * 应用的唯一标识key
     */
    @TableField(value = "app_key")
    @ApiModelProperty(value = "应用的唯一标识key")
    @Size(max = 50, message = "应用的唯一标识key最大长度要小于 50")
    @NotBlank(message = "应用的唯一标识key不能为空")
    private String appKey;

    /**
     * 应用的密钥
     */
    @TableField(value = "app_secret")
    @ApiModelProperty(value = "应用的密钥")
    @Size(max = 50, message = "应用的密钥最大长度要小于 50")
    @NotBlank(message = "应用的密钥不能为空")
    private String appSecret;

    /**
     * 所属平台
     */
    @TableField(value = "platform")
    @ApiModelProperty(value = "所属平台")
    @Size(max = 50, message = "所属平台最大长度要小于 50")
    private String platform;

    /**
     * 备注
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    @Size(max = 50, message = "备注最大长度要小于 50")
    private String remark;

    /**
     * 有效性
     */
    @TableField(value = "`status`")
    @ApiModelProperty(value = "有效性")
    @NotNull(message = "有效性不能为null")
    private Integer status;

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

    public static final String COL_ID = "id";

    public static final String COL_APP_KEY = "app_key";

    public static final String COL_APP_SECRET = "app_secret";

    public static final String COL_PLATFORM = "platform";

    public static final String COL_REMARK = "remark";

    public static final String COL_STATUS = "status";

    public static final String COL_CREATE_BY = "create_by";

    public static final String COL_CREATE_AT = "create_at";

    public static final String COL_UPDATE_BY = "update_by";

    public static final String COL_UPDATE_AT = "update_at";
}
