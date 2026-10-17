package com.bidr.sms.vo;

import com.bidr.kernel.config.response.BindDict;
import com.bidr.kernel.config.response.BindRepo;
import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: SmsHistoryRes
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 10:38
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmsHistoryRes extends SaSmsSend {


    @BindRepo(entity = SaSmsTemplate.class, matchField = "templateCode", extractField = "body", sourceField = "templateCode")
    private String details;

    @ApiModelProperty(value = "发送时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sendAt;

    @BindDict(type = "MDM_PLATFORM_DICT", field = "platform")
    @ApiModelProperty(value = "对接平台")
    private String platformDisplay;
}
