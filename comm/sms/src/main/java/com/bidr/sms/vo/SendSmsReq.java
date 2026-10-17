/**
 * Title: SendSmsReq.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @description Project Name: Grote
 * @Package: com.srct.service.account.po.sms
 * @since 2019-8-7 11:13
 */
package com.bidr.sms.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

@Data
public class SendSmsReq {
    @NotNull(message = "业务id不能为空")
    private String bizId;
    @NotNull(message = "目标手机号不能为空")
    private String phoneNumbers;
    private String templateCode;
    private String sendSmsType;
    private String mock;
    private Map<String, String> paramMap;
}
