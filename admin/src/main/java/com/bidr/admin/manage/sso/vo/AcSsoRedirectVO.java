package com.bidr.admin.manage.sso.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalNameField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: AcSsoRedirectVO
 * Description: Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 * <p>🔴 实体＝DDL 镜像：不挂派生列／exist=false 列；宿主列字段名必须＝partnerId
 * （前端 advance-condition/bind-default-value 以它作 property）。</p>
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Data
public class AcSsoRedirectVO {
    @PortalIdField
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "归属partner（ac_partner 口径 id）")
    private Long partnerId;

    @PortalNameField
    @ApiModelProperty(value = "回调地址（全串等值命中，禁前缀/like）")
    private String redirectUri;

    @ApiModelProperty(value = "登记备注（用途/环境说明）")
    private String remark;

    @ApiModelProperty(value = "创建者")
    private String createBy;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @ApiModelProperty(value = "有效性（软删位：撤销登记＝置0，再登记＝插新行）")
    private String valid;
}
