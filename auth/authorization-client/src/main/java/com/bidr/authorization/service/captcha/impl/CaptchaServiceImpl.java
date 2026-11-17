package com.bidr.authorization.service.captcha.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.config.captcha.ICaptchaVerification;
import com.bidr.authorization.config.msg.IMsgVerification;
import com.bidr.authorization.constants.captcha.CaptchaType;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.service.captcha.CaptchaService;
import com.bidr.authorization.service.token.impl.TokenServiceImpl;
import com.bidr.authorization.utils.captcha.CaptchaUtil;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.PackageScanUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: CaptchaServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 17:39
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final Map<String, ICaptchaVerification> CAPTCHA_CODE_TYPE_MAP = new ConcurrentHashMap<>(16);
    private static final String EXPIRED_TIMESTAMP = "_CAPTCHA_EXPIRED_TIMESTAMP";
    /** 测试模式提示只打一次（P-1 可观测性开关；不参与任何判定） */
    private static final AtomicBoolean TEST_MODE_WARNED = new AtomicBoolean(false);
    @Resource
    private TokenServiceImpl tokenServiceImpl;

    @Resource
    private SysConfigCacheService sysConfigCacheService;
    @Value("${my.base-package:com.bidr}")
    private String basePackage;

    @Override
    public BufferedImage generateCaptcha(String token, String type) {
        Validator.assertNotBlank(token, ErrCodeSys.PA_DATA_NOT_EXIST, "token");
        ICaptchaVerification captchaVerification = CAPTCHA_CODE_TYPE_MAP.get(type);
        Validator.assertNotNull(captchaVerification, ErrCodeSys.PA_DATA_NOT_EXIST, "图形验证码类型");
        StringBuffer code = new StringBuffer();
        BufferedImage image = CaptchaUtil.genRandomCodeImage(code);
        TokenInfo tokenInfo = AuthTokenUtil.decode(token);
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
        boolean strictMode;
        try {
            strictMode = sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH);
        } catch (Exception e) {
            // sys_config 行缺失时 getSysConfigValue 会抛 PARAM_IS_NOT_EXISTED：视为严格模式（安全默认）
            log.warn("读取验证码严格模式开关失败，按严格模式处理: {}", e.getMessage());
            strictMode = true;
        }
        String captcha;
        if (strictMode) {
            captcha = tokenServiceImpl.getItem(token, type, String.class);
            Long timeStamp = tokenServiceImpl.getItem(token, getExpiredTimestampTokenItem(type), Long.class);
            Validator.assertNotNull(timeStamp, ErrCodeSys.SYS_ERR_MSG, "验证码错误");
            Validator.assertTrue(timeStamp > System.currentTimeMillis(), ErrCodeSys.SYS_ERR_MSG, "图形验证码已过期");
            // 字面量比较：用户输入绝不能当正则用（".*" 会把任意存量码都匹配通过）
            Validator.assertEquals(captcha, code.toLowerCase(), ErrCodeSys.SYS_ERR_MSG, "验证码错误");
        } else {
            // P-1（用户裁定保留测试模式语义）：只做可观测性——首次命中提示一次，判定逻辑与默认值均不变
            String testModeDefaultCode = getTestModeDefaultCode();
            if (TEST_MODE_WARNED.compareAndSet(false, true)) {
                log.warn("验证码处于测试模式（TEST_MODE_VALIDATE_SWITCH=0），默认码「{}」可放行；生产环境建议置 1",
                        testModeDefaultCode);
            }
            try {
                captcha = tokenServiceImpl.getItem(token, type, String.class);
                Validator.assertEquals(captcha, code.toLowerCase(), ErrCodeSys.SYS_ERR_MSG, "验证码错误");
            } catch (Exception e) {
                Validator.assertEquals(testModeDefaultCode, code.toLowerCase(), ErrCodeSys.SYS_ERR_MSG, "验证码错误");
            }
        }
        cleanCaptchaToken(token, type);
    }

    /**
     * 非严格模式默认码：配置行缺失/为空时取空串，配合字面量比较必然拒绝（安全默认）
     */
    private String getTestModeDefaultCode() {
        try {
            return sysConfigCacheService.getParamValueAvail(AccountParam.TEST_MODE_VALIDATE_DEFAULT_CODE);
        } catch (Exception e) {
            log.warn("读取测试模式默认验证码失败，按空串处理: {}", e.getMessage());
            return "";
        }
    }

    private void cleanCaptchaToken(TokenInfo token, String type) {
        tokenServiceImpl.removeItemByToken(token, type, getExpiredTimestampTokenItem(type));
    }

    @PostConstruct
    public void init() {
        // 内置 8 类型先注册：子系统 base-package 内无验证码枚举时也能恒有内置类型
        for (CaptchaType captchaType : CaptchaType.values()) {
            CAPTCHA_CODE_TYPE_MAP.put(captchaType.name(), captchaType);
        }
        // 宿主扩展枚举 best-effort 扫描（主系统 WechatMsgVerificationType 等），异常/无命中不阻断
        try {
            Reflections reflections = PackageScanUtil.reflections(basePackage);
            Set<Class<? extends ICaptchaVerification>> msgVerificationClass = reflections.getSubTypesOf(
                    ICaptchaVerification.class);
            if (msgVerificationClass.isEmpty()) {
                log.warn("宿主扩展验证码类型扫描无命中（base-package={}），仅使用内置类型", basePackage);
                return;
            }
            for (Class<? extends ICaptchaVerification> clazz : msgVerificationClass) {
                if (Enum.class.isAssignableFrom(clazz)) {
                    boolean isMsgVerification = IMsgVerification.class.isAssignableFrom(clazz);
                    for (ICaptchaVerification enumItem : clazz.getEnumConstants()) {
                        if (isMsgVerification) {
                            CAPTCHA_CODE_TYPE_MAP.put(enumItem.name() + "_CAPTCHA", enumItem);
                        } else {
                            CAPTCHA_CODE_TYPE_MAP.put(enumItem.name(), enumItem);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("宿主扩展验证码类型扫描失败（base-package={}），仅使用内置类型: {}", basePackage, e.getMessage());
        }
    }
}
