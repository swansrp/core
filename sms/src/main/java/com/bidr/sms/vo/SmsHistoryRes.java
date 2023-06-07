package com.bidr.sms.vo;

import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.diboot.core.binding.annotation.BindField;
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


    @BindField(entity = SaSmsTemplate.class, field = "body", condition = "this.templateCode = template_code")
    private String details;

    @ApiModelProperty(value = "发送时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date sendAt;
}
