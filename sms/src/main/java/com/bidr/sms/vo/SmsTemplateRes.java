package com.bidr.sms.vo;

import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.diboot.core.binding.annotation.BindDict;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: SmsTemplateRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:47
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmsTemplateRes extends SaSmsTemplate {

    @ApiModelProperty(value = "平台显示名称", allowableValues = "PLATFORM_DICT")
    @BindDict(type = "PLATFORM_DICT", field = "platform")
    private String platformDisplay;


    @ApiModelProperty(value = "审核状态", allowableValues = "ALI_SMS_TEMP_CONFIRM_STATUS_DICT")
    @BindDict(type = "ALI_SMS_TEMP_CONFIRM_STATUS_DICT", field = "confirmStatus")
    private String confirmStatusDisplay;

    @ApiModelProperty(value = "短信模板类型", allowableValues = "ALI_SMS_TEMP_TYPE_DICT")
    @BindDict(type = "ALI_SMS_TEMP_TYPE_DICT", field = "templateType")
    private String templateTypeDisplay;

    @ApiModelProperty(value = "审批时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date confirmAt;
}
