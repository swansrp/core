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

import com.diboot.core.data.copy.Accept;
import lombok.Data;

@Data
public class SendSmsRes {
    @Accept(name = "sendResult")
    private String message;
    @Accept(name = "sendId")
    private String requestId;
    private String bizId;
    @Accept(name = "sendStatus")
    private Integer status;
}
