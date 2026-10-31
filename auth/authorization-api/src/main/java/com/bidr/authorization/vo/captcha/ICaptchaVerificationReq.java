package com.bidr.authorization.vo.captcha;

/**
 * Title: ICaptchaVerificationReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/24 11:45
 */
public interface ICaptchaVerificationReq {
    /**
     * 获取图形验证码
     *
     * @return
     */
    String getGraphCode();
}
