package com.sharp.authorization.config.captcha;

/**
 * Title: ICaptchaVerification
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/24 11:41
 */
public interface ICaptchaVerification {
    /**
     * 获取图形验证码类型名称
     *
     * @return
     */
    String name();

    /**
     * 获取图形验证码过期时间
     *
     * @return
     */
    int getTimeout();
}
