package com.bidr.authorization.service.captcha.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.captcha.ICaptchaVerification;
import com.bidr.authorization.config.msg.IMsgVerification;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.service.captcha.CaptchaService;
import com.bidr.authorization.service.token.impl.TokenServiceImpl;
import com.bidr.authorization.utils.captcha.CaptchaUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.apache.commons.lang3.StringUtils;
import org.reflections.Reflections;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: CaptchaServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/26 17:39
 */
@Service
public class CaptchaServiceImpl implements CaptchaService, CommandLineRunner {

    private static final Map<String, ICaptchaVerification> CAPTCHA_CODE_TYPE_MAP = new ConcurrentHashMap<>(16);
    private static final String EXPIRED_TIMESTAMP = "_CAPTCHA_EXPIRED_TIMESTAMP";
    @Resource
    private TokenServiceImpl tokenServiceImpl;

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    @Override
    public BufferedImage generateCaptcha(String token, String type) {
        Validator.assertNotBlank(token, ErrCodeSys.PA_DATA_NOT_EXIST, "token");
        ICaptchaVerification captchaVerification = CAPTCHA_CODE_TYPE_MAP.get(type);
        Validator.assertNotNull(captchaVerification, ErrCodeSys.PA_DATA_NOT_EXIST, "图形验证码类型");
        StringBuffer code = new StringBuffer();
        BufferedImage image = CaptchaUtil.genRandomCodeImage(code);
        TokenInfo tokenInfo = AuthTokenUtil.resolveToken(token);
        tokenServiceImpl.updateTokenValue(tokenInfo, type, code.toString().toLowerCase());
        long timeout = System.currentTimeMillis();
        if (captchaVerification instanceof IMsgVerification) {
            timeout += (long) captchaVerification.getTimeout() * 10 * 1000;
        } else {
            timeout += captchaVerification.getTimeout() * 1000L;
        }
        tokenServiceImpl.updateTokenValue(tokenInfo, getExpiredTimestampTokenItem(type), timeout);
        return image;
    }

    private String getExpiredTimestampTokenItem(String type) {
        return type + EXPIRED_TIMESTAMP;
    }

    @Override
    public void validateCaptcha(TokenInfo token, String type, String code) {
        Validator.assertNotNull(token, ErrCodeSys.PA_DATA_NOT_EXIST, "token");
        Validator.assertNotBlank(code, ErrCodeSys.PA_DATA_NOT_EXIST, "图形验证码");
        String captcha;
        if (sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)) {
            captcha = tokenServiceImpl.getItem(token, type, String.class);
            Long timeStamp = tokenServiceImpl.getItem(token, getExpiredTimestampTokenItem(type), Long.class);
            Validator.assertTrue(timeStamp > System.currentTimeMillis(), ErrCodeSys.SYS_ERR_MSG, "图形验证码已过期");
            Validator.assertMatch(captcha, code.toLowerCase(), ErrCodeSys.SYS_ERR_MSG, "验证码错误");
        } else {
            try {
                captcha = tokenServiceImpl.getItem(token, type, String.class);
                Validator.assertMatch(captcha, code.toLowerCase(), ErrCodeSys.SYS_ERR_MSG, "验证码错误");
            } catch (Exception e) {
                captcha = sysConfigCacheService.getParamValueAvail(AccountParam.TEST_MODE_VALIDATE_DEFAULT_CODE);
                Validator.assertMatch(captcha, code.toLowerCase(), ErrCodeSys.SYS_ERR_MSG, "验证码错误");
            }
        }
        cleanCaptchaToken(token, type);
    }

    private void cleanCaptchaToken(TokenInfo token, String type) {
        tokenServiceImpl.removeItemByToken(token, type, getExpiredTimestampTokenItem(type));
    }

    @Override
    public void run(String... args) throws Exception {
        init();
    }

    public void init() {
        Reflections reflections = new Reflections("com.bidr");
        Set<Class<? extends ICaptchaVerification>> msgVerificationClass = reflections.getSubTypesOf(
                ICaptchaVerification.class);
        for (Class<? extends ICaptchaVerification> clazz : msgVerificationClass) {
            if (Enum.class.isAssignableFrom(clazz)) {
                for (ICaptchaVerification enumItem : clazz.getEnumConstants()) {
                    if (StringUtils.isNotBlank(enumItem.name())) {
                        CAPTCHA_CODE_TYPE_MAP.put(enumItem.name(), enumItem);
                    }
                }
            }
        }
    }
}
