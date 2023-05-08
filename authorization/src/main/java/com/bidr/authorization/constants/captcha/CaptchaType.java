package com.bidr.authorization.constants.captcha;

import com.bidr.authorization.config.captcha.ICaptchaVerification;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: CaptchaType
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/20 10:39
 */
@AllArgsConstructor
public enum CaptchaType implements ICaptchaVerification {
    /**
     *
     */
    LOGIN_CAPTCHA(300),
    LOGIN_MSG_CODE_CAPTCHA(300),
    FIND_PASSWORD_CAPTCHA(300);

    @Getter
    @Setter
    private int timeout;
}
