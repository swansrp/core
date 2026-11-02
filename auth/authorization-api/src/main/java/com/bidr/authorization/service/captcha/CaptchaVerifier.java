package com.bidr.authorization.service.captcha;

import com.bidr.authorization.bo.token.TokenInfo;

/**
 * Title: CaptchaVerifier
 * Description: 图形验证码校验最小接口（T2.3c 上提）——方法面按 client 侧
 * {@code AuthInterceptor.validateCaptcha} 的实际调用点定（@CaptchaVerify 端点前置校验）。
 *
 * <p>server 既有 {@code CaptchaService}（含生成面 {@code generateCaptcha}）加
 * {@code extends CaptchaVerifier}（校验面同签名，行为零变化）；宿主未装配实现时
 * client 拦截器对带 {@code @CaptchaVerify} 的端点 fail-closed 报错、不带注解的端点零影响
 * （ObjectProvider 注入，见 AuthInterceptor javadoc）。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
public interface CaptchaVerifier {

    /**
     * 校验图形验证码（不通过即抛业务异常——server 实现的既有契约）。
     *
     * @param token            当前线程 token（验证码与 token 绑定存储）
     * @param captchaVerifyType 验证类型（@CaptchaVerify value 或 field 参数派生）
     * @param code             用户提交的图形验证码
     */
    void validateCaptcha(TokenInfo token, String captchaVerifyType, String code);
}
