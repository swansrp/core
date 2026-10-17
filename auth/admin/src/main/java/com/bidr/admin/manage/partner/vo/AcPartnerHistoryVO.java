package com.bidr.admin.manage.partner.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.config.PortalTextAreaField;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: AcPartnerHistoryVO
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/2 15:34
 */
@Data
public class AcPartnerHistoryVO {
    @PortalIdField
    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "平台")
    private String platform;

    @ApiModelProperty(value = "appKey")
    private String appKey;

    @ApiModelProperty(value = "访问ip")
    private String remoteIp;

    @ApiModelProperty(value = "api路径")
    private String url;

    @ApiModelProperty(value = "返回值")
    private Integer status;

    @PortalTextAreaField
    @ApiModelProperty(value = "返回消息")
    private String message;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "访问时间")
    private Date requestAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "返回时间")
    private Date responseAt;
}
