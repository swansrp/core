package com.bidr.wechat.constant;

import com.bidr.authorization.config.captcha.ICaptchaVerification;
import com.bidr.authorization.config.msg.IMsgVerification;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: MsgVerificationTypeDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/20 10:40
 */
@Getter
@RequiredArgsConstructor
public enum WechatMsgVerificationType implements IMsgVerification, ICaptchaVerification {
    /**
     * 短信验证码类型
     */
    BIND_PHONE_NUMBER_MSG_CODE(60, 300, 6, "", "");


    private final int internal;
    private final int timeout;
    private final int length;
    private final String templateNumber;
    private final String template;
}
