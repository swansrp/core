package com.bidr.sms.bo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Title: SmsReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/28 14:53
 */
@Data
public class SmsReq {
    @NotBlank(message = "手机号码不能为空")
    private String phoneNumbers;
    @NotBlank(message = "短信类型不能为空")
    private String sendSmsType;
    private Map<String, String> paramMap;
}
