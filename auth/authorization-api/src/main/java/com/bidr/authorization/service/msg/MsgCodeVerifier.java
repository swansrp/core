package com.bidr.authorization.service.msg;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.vo.msg.MsgVerificationReq;

/**
 * Title: MsgCodeVerifier
 * Description: 短信验证码校验最小接口（T2.3c 上提）——方法面按 client 侧
 * {@code AuthInterceptor.validateMsgCode} 的实际调用点定（@MsgCodeVerify 端点前置校验，
 * 手机号＋验证码一起交验）。
 *
 * <p>server 既有 {@code MsgVerificationService}（含生成/类型解析面）加
 * {@code extends MsgCodeVerifier}（校验面同签名，行为零变化）；宿主未装配实现时
 * client 拦截器对带 {@code @MsgCodeVerify} 的端点 fail-closed 报错、不带注解的端点零影响
 * （ObjectProvider 注入，见 AuthInterceptor javadoc）。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
public interface MsgCodeVerifier {

    /**
     * 校验短信验证码（不通过即抛业务异常——server 实现的既有契约）。
     *
     * @param token     当前线程 token
     * @param req       验证码请求（手机号＋用户提交的短信码）
     * @param verifyType 验证类型（@MsgCodeVerify value）
     */
    void validateMsgCode(TokenInfo token, MsgVerificationReq req, String verifyType);
}
