/**
 * Title: SendSmsRes.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @description Project Name: Grote
 * @Package: com.srct.service.account.po.sms
 * @since 2019-8-7 11:20
 */
package com.bidr.sms.vo;

import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.data.copy.Accept;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SendSmsRes {
    @Accept(name = "sendResult")
    @JsonProperty(value = "Message")
    private String message;
    @Accept(name = "sendId")
    @JsonProperty(value = "RequestId")
    private String requestId;
    @JsonProperty(value = "BizId")
    private String bizId;
    @BindDict(type = "")
    @Accept(name = "sendStatus")
    @JsonProperty(value = "Code")
    private Integer code;
}
