package com.bidr.authorization.service.captcha;

import com.bidr.authorization.bo.token.TokenInfo;

import java.awt.image.BufferedImage;

/**
 * Title: CaptchaServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/27 09:38
 */
public interface CaptchaService {

    BufferedImage generateCaptcha(String token, String type);

    void validateCaptcha(TokenInfo token, String type, String code);

}
