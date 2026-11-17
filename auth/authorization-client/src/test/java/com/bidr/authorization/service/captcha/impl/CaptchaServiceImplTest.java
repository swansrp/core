package com.bidr.authorization.service.captcha.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.captcha.CaptchaType;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.service.token.impl.TokenServiceImpl;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.platform.constant.err.ConfigErrorCode;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.image.BufferedImage;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证码生成/校验上提 authorization-client 后的守卫（T2 系列，2026-11-17）：
 *
 * <p>钉三件事：① {@code @PostConstruct init()} 先注册 8 个内置 {@link CaptchaType}（子系统
 * base-package 内无验证码枚举也恒有），宿主扩展扫描异常/无命中不阻断；② 严格模式下匹配通过、
 * 不匹配抛「验证码错误」、过期抛「图形验证码已过期」、通过后一次性清理 token 项；③
 * {@code TEST_MODE_VALIDATE_SWITCH} 读取失败（sys_config 行缺失抛 PARAM_IS_NOT_EXISTED）
 * 时按严格模式走——安全默认，绝不因配置缺失而放行。</p>
 *
 * @author coder (棒1, 验证码上提 2026-11-17)
 * @since 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class CaptchaServiceImplTest {

    private static final String TYPE = "LOGIN_CAPTCHA";
    private static final String TIMESTAMP_KEY = TYPE + "_CAPTCHA_EXPIRED_TIMESTAMP";
    private static final String CODE = "ab3d";

    @Mock
    private TokenServiceImpl tokenServiceImpl;

    @Mock
    private SysConfigCacheService sysConfigCacheService;

    @InjectMocks
    private CaptchaServiceImpl captchaService;

    private final TokenInfo token = new TokenInfo("token-1", TokenType.WEB_ACCESS_TOKEN, "cust-1", 0);

    @BeforeEach
    void initBuiltinTypes() {
        // 只扫本包（无验证码枚举）：覆盖「无命中不阻断」；内置类型在扫描之前注册
        ReflectionTestUtils.setField(captchaService, "basePackage",
                "com.bidr.authorization.service.captcha.impl");
        captchaService.init();
    }

    @Test
    void initRegistersEightBuiltinCaptchaTypes() {
        Map<?, ?> typeMap = (Map<?, ?>) ReflectionTestUtils.getField(CaptchaServiceImpl.class,
                "CAPTCHA_CODE_TYPE_MAP");
        assertNotNull(typeMap);
        // 不锁 map 总 size（宿主扩展类型/并行用例可追加）；只断言 8 个内置类型名逐个在位
        for (CaptchaType captchaType : CaptchaType.values()) {
            assertTrue(typeMap.containsKey(captchaType.name()), "缺少内置验证码类型: " + captchaType.name());
        }
    }

    @Test
    void generateCaptchaUsesBuiltinTypeAndStoresCodeAndTimestamp() {
        // 公开方法间接口径：内置类型生成不抛「图形验证码类型」，并产出 140×50 PNG 源图
        BufferedImage image = captchaService.generateCaptcha(AuthTokenUtil.getToken(token), TYPE);

        assertNotNull(image);
        assertEquals(140, image.getWidth());
        assertEquals(50, image.getHeight());
        verify(tokenServiceImpl).updateTokenValue(any(TokenInfo.class), eq(TYPE), anyString());
        verify(tokenServiceImpl).updateTokenValue(any(TokenInfo.class), eq(TIMESTAMP_KEY), anyLong());
        verifyNoMoreInteractions(tokenServiceImpl);
    }

    @Test
    void strictModeAcceptsMatchingCodeAndConsumesTokenOnce() {
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(true);
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() + 60_000L);

        assertDoesNotThrow(() -> captchaService.validateCaptcha(token, TYPE, CODE.toUpperCase()));

        verify(tokenServiceImpl).getItem(token, TYPE, String.class);
        verify(tokenServiceImpl).getItem(token, TIMESTAMP_KEY, Long.class);
        verify(tokenServiceImpl).removeItemByToken(token, TYPE, TIMESTAMP_KEY);
        verifyNoMoreInteractions(tokenServiceImpl);
    }

    @Test
    void strictModeRejectsMismatchedCode() {
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(true);
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() + 60_000L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> captchaService.validateCaptcha(token, TYPE, "zzzz"));

        assertTrue(ex.getMessage().contains("验证码错误"), "实际消息: " + ex.getMessage());
        verify(tokenServiceImpl, never()).removeItemByToken(any(TokenInfo.class), any(String[].class));
    }

    @Test
    void strictModeRejectsExpiredTimestamp() {
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(true);
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() - 1_000L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> captchaService.validateCaptcha(token, TYPE, CODE));

        assertTrue(ex.getMessage().contains("图形验证码已过期"), "实际消息: " + ex.getMessage());
        verify(tokenServiceImpl, never()).removeItemByToken(any(TokenInfo.class), any(String[].class));
    }

    @Test
    void strictModeRejectsWildcardRegexInput() {
        // C-1（blocker）：".*" 当正则会匹配任意存量码——必须按字面量拒绝
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(true);
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() + 60_000L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> captchaService.validateCaptcha(token, TYPE, ".*"));

        assertTrue(ex.getMessage().contains("验证码错误"), "实际消息: " + ex.getMessage());
        verify(tokenServiceImpl, never()).removeItemByToken(any(TokenInfo.class), any(String[].class));
    }

    @Test
    void strictModeRejectsDotQuantifierRegexInput() {
        // C-1：任意四字符正则（"...."）同样不得放行
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(true);
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() + 60_000L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> captchaService.validateCaptcha(token, TYPE, "...."));

        assertTrue(ex.getMessage().contains("验证码错误"), "实际消息: " + ex.getMessage());
        verify(tokenServiceImpl, never()).removeItemByToken(any(TokenInfo.class), any(String[].class));
    }

    @Test
    void strictModeStillAcceptsCorrectCodeAfterLiteralComparison() {
        // 防误伤：改字面量比较后，正确码原样通过并消费 token 项
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(true);
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() + 60_000L);

        assertDoesNotThrow(() -> captchaService.validateCaptcha(token, TYPE, CODE));

        verify(tokenServiceImpl).removeItemByToken(token, TYPE, TIMESTAMP_KEY);
    }

    @Test
    void switchReadFailureFallsBackToStrictMode() {
        // sys_config 行缺失：getSysConfigValue → PARAM_IS_NOT_EXISTED
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH))
                .thenThrow(new ServiceException(ConfigErrorCode.PARAM_IS_NOT_EXISTED,
                        AccountParam.TEST_MODE_VALIDATE_SWITCH.name()));
        when(tokenServiceImpl.getItem(token, TYPE, String.class)).thenReturn(CODE);
        when(tokenServiceImpl.getItem(token, TIMESTAMP_KEY, Long.class))
                .thenReturn(System.currentTimeMillis() + 60_000L);

        assertDoesNotThrow(() -> captchaService.validateCaptcha(token, TYPE, CODE));

        // 只有严格分支会读过期时间戳；非严格分支走默认码、不读时间戳
        verify(sysConfigCacheService).getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH);
        verify(tokenServiceImpl).getItem(token, TIMESTAMP_KEY, Long.class);
        verify(tokenServiceImpl).removeItemByToken(token, TYPE, TIMESTAMP_KEY);
        verify(tokenServiceImpl).getItem(token, TYPE, String.class);
        verifyNoMoreInteractions(tokenServiceImpl);
        verifyNoMoreInteractions(sysConfigCacheService);
    }

    @Test
    void nonStrictModeWithMissingDefaultCodeRejectsAnyInputSafely() {
        when(sysConfigCacheService.getParamSwitch(AccountParam.TEST_MODE_VALIDATE_SWITCH)).thenReturn(false);
        when(sysConfigCacheService.getParamValueAvail(AccountParam.TEST_MODE_VALIDATE_DEFAULT_CODE))
                .thenThrow(new ServiceException(ConfigErrorCode.PARAM_IS_NOT_EXISTED,
                        AccountParam.TEST_MODE_VALIDATE_DEFAULT_CODE.name()));

        // token 内无验证码 → NPE/校验失败进 catch → 默认码读取也失败 → "" → 必然拒绝
        ServiceException ex = assertThrows(ServiceException.class,
                () -> captchaService.validateCaptcha(token, TYPE, "0000"));

        assertTrue(ex.getMessage().contains("验证码错误"), "实际消息: " + ex.getMessage());
        verify(sysConfigCacheService).getParamValueAvail(AccountParam.TEST_MODE_VALIDATE_DEFAULT_CODE);
        verify(tokenServiceImpl, never()).getItem(token, TIMESTAMP_KEY, Long.class);
        verify(tokenServiceImpl, never()).removeItemByToken(any(TokenInfo.class), any(String[].class));
    }
}