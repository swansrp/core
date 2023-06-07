package com.bidr.sms.constant.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: SmsTypeEnum
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 10:42
 */
@AllArgsConstructor
@Getter
public enum SmsTypeEnum {
    /**
     * 短信模板
     */
    TEST_MSG(0, "SMS_234151418", "固定文本无参数的模板"),
    IDAAS_MSG_CODE(3, "SMS_234156462", "尊敬的用户，您本次的验证码是：${code}。如非本人操作，请忽略本短信");

    private final Integer smsCode;
    private final String templateId;
    private final String remark;
}
