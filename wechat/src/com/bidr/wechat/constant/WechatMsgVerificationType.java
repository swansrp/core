package com.bidr.wechat.constant;

import com.bidr.authorization.config.captcha.ICaptchaVerification;
import com.bidr.authorization.config.msg.IMsgVerification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: MsgVerificationTypeDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/20 10:40
 */
@AllArgsConstructor
public enum WechatMsgVerificationType implements IMsgVerification, ICaptchaVerification {
    /**
     * 短信验证码类型
     */
    BIND_PHONE_NUMBER_MSG_CODE(60, 300, 6, "", "");

    @Getter
    @Setter
    private int internal;
    @Getter
    @Setter
    private int timeout;
    @Getter
    @Setter
    private int length;
    @Getter
    @Setter
    private String templateNumber;
    @Getter
    @Setter
    private String template;
}
