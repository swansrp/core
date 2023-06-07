package com.bidr.sms.service.message;

import com.bidr.sms.vo.SendSmsReq;
import com.bidr.sms.vo.SendSmsRes;

/**
 * Title: SmsService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 10:12
 */
public interface SmsService {
    /**
     * 发送短信
     *
     * @param sendSmsReq
     * @return 发送结果
     */
    SendSmsRes sendSms(SendSmsReq sendSmsReq);

    /**
     * 异步发送短信
     *
     * @param sendSmsReq
     */
    SendSmsRes sendAsyncSms(SendSmsReq sendSmsReq);

    /**
     * 获取发送短信结果
     *
     * @param requestId
     * @return 发送结果
     */
    SendSmsRes getSendSmsRes(String requestId);
}
