package com.bidr.authorization.config.msg;

import com.bidr.authorization.config.captcha.ICaptchaVerification;

/**
 * Title: IMsgVerification
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/24 10:02
 */
public interface IMsgVerification extends ICaptchaVerification {
    /**
     * 获取短信验证码获取间隔时间
     *
     * @return
     */
    int getInternal();

    /**
     * 短信验证码长度
     *
     * @return
     */
    int getLength();
}
