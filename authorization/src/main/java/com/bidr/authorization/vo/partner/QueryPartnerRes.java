package com.bidr.authorization.vo.partner;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcUser;
import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.binding.annotation.BindField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: QueryPartnerRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 17:22
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryPartnerRes extends AcPartner {


    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "应用的唯一标识key")
    @Size(max = 50, message = "应用的唯一标识key最大长度要小于 50")
    @NotBlank(message = "应用的唯一标识key不能为空")
    private String appKey;

    
    @ApiModelProperty(value = "应用的密钥")
    @Size(max = 50, message = "应用的密钥最大长度要小于 50")
    @NotBlank(message = "应用的密钥不能为空")
    private String appSecret;

    @BindDict(type = "MDM_PLATFORM_DICT", field = "platform")
    @ApiModelProperty(value = "所属平台")
    private String platform;

    @ApiModelProperty(value = "备注")
    @Size(max = 50, message = "备注最大长度要小于 50")
    private String remark;


    @BindDict(type = "ACTIVE_STATUS_DICT", field = "status")
    @ApiModelProperty(value = "有效性")
    private String statusDisplay;

    @BindField(entity = AcUser.class, field = "name", condition = "this.createBy = customer_number")
    @ApiModelProperty(value = "创建者")
    @Size(max = 50, message = "创建者最大长度要小于 50")
    private String createBy;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @BindField(entity = AcUser.class, field = "name", condition = "this.createBy = customer_number")
    @ApiModelProperty(value = "更新者")
    @Size(max = 50, message = "更新者最大长度要小于 50")
    private String updateBy;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
