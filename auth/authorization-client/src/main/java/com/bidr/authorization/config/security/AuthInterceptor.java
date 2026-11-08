package com.bidr.authorization.config.security;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.annotation.auth.AuthRole;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.annotation.msg.MsgCodeVerify;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.captcha.CaptchaVerifier;
import com.bidr.authorization.service.msg.MsgCodeVerifier;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.tenant.CrossTenant;
import com.bidr.authorization.tenant.TenantExemptIdentityFill;
import com.bidr.authorization.vo.captcha.CaptchaVerificationReq;
import com.bidr.authorization.vo.msg.MsgVerificationReq;
import com.bidr.kernel.config.anno.IgnoreAuth;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Title: AuthInterceptor
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/17 15:52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String CAPTCHA_SUFFIX = "_CAPTCHA";

    private final ApplicationContext applicationContext;
    private final TokenService tokenService;
    /**
     * 图形/短信验证码校验 SPI（T2.3c 上提改 ObjectProvider 可选注入——宿主装配
     * server 侧 CaptchaService/MsgVerificationService（已 implements api 校验面）即生效；
     * 端点带 @CaptchaVerify/@MsgCodeVerify 而宿主无实现时 fail-closed 报错，
     * 不带注解的端点零影响）
     */
    private final ObjectProvider<CaptchaVerifier> captchaVerifier;
    private final ObjectProvider<MsgCodeVerifier> msgCodeVerifier;
    /** 豁免入口身份接线 SPI（可选——宿主无实现时静默跳过；ERP 侧装实现，LoginFillTokenInf 同族先例） */
    private final ObjectProvider<TenantExemptIdentityFill> tenantExemptIdentityFill;
    @Value("${my.cors.enable:false}")
    private boolean corsEnable;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        enableCrossDomain(request, response);
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (HttpUtil.systemRequest(request)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        Method method = handlerMethod.getMethod();
        Class<?> clazz = handlerMethod.getBeanType();
        if (!BasicErrorController.class.isAssignableFrom(
                clazz)) {
            validateClientType(request);
            validateAuth(request, method, clazz);
            validateCaptcha(request, method);
            validateMsgCode(request, method);
            applyCrossTenantExemption(request, handlerMethod);
        }
        return true;
    }

    /**
     * @CrossTenant 入口级跨租户读豁免消费（0.4b erp-common 同款逻辑，2026-10-24 豁免治理迁 core）。
     *
     * <p>方法/类注解命中（方法优先、类兜底）＋已认证（AccountContext 在场，与原实现同口径——
     * 登录/健康检查等未认证路径不触发）→ 三件事：
     * ① WARN 留痕（照原样：uri/handler/operator，不打印租户值）；② 置 core 平台档
     * （行级隔离 ignoreTable 短路＝读侧豁免；不清租户槽，与原实现一致）；
     * ③ 经 {@link TenantExemptIdentityFill} SPI 回调业务侧升格身份面（身份键名是业务
     * 单一正源，core 不感知——见 SPI javadoc）。</p>
     *
     * <p><b>序</b>：跑在 validateAuth 之后＝鉴权后生效（鉴权失败抛错时绝不会置位豁免，
     * fail-closed）；读写边界＝只豁免读侧，端点内写隔离表仍需显式 runWithoutIsolation
     * （见 {@link CrossTenant} javadoc）。平台档清理复用 {@link #afterCompletion} 既有
     * {@code TenantContext.remove()}（AuthAdmin 腿同款收尾，零新增清理点）。</p>
     */
    private void applyCrossTenantExemption(HttpServletRequest request, HandlerMethod handlerMethod) {
        if (handlerMethod.getMethodAnnotation(CrossTenant.class) == null
                && handlerMethod.getBeanType().getAnnotation(CrossTenant.class) == null) {
            return;
        }
        AccountInfo account = AccountContext.get();
        if (account == null) {
            return;
        }
        if (log.isWarnEnabled()) {
            log.warn("@CrossTenant 忽略通道生效：注入 PLATFORM（原租户/店键已清除） uri={} handler={} "
                            + "operatorUserId={} operatorUserName={}",
                    request.getRequestURI(),
                    handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName(),
                    account.getUserId(), account.getUserName());
        }
        TenantContext.markPlatform();
        TenantExemptIdentityFill identityFill = tenantExemptIdentityFill.getIfAvailable();
        if (identityFill != null) {
            identityFill.fill();
        }
    }

    private void enableCrossDomain(HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> headersInfoMap = HttpUtil.getHeadersInfoMap(request);
        if (FuncUtil.equals(BeanUtil.getActiveProfile(), "dev") ||
                FuncUtil.equals(BeanUtil.getActiveProfile(), "local") || corsEnable) {
            response.setHeader("Access-Control-Allow-Origin", headersInfoMap.get("origin"));
            // 🔴 必配对：ACAO 回显 origin 时补 ACAC=true，前端 withCredentials:true 的跨源 XHR
            // 才允许落 Set-Cookie（2026-11-08 实锤：dev 跨源形态登录 cookie 永远不落盘 ⇒
            // iframe 内 authorize 的 cookie 回退恒「未登录」）。生产同源形态不走本分支，零变化。
            response.setHeader("Access-Control-Allow-Credentials", "true");
        }
        response.setHeader("Access-Control-Allow-Headers", RequestConst.getAllHeader());
        response.setHeader("Access-Control-Allow-Methods", RequestConst.getAllMethod());
    }

    private void validateClientType(HttpServletRequest request) {
        String clientType = request.getHeader(RequestConst.CLIENT_TYPE);
        if (FuncUtil.isEmpty(clientType)) {
            clientType = ClientType.WEB.getValue();
        }
        Validator.assertNotBlank(clientType, ErrCodeSys.PA_PARAM_NULL, "客户端类型");
        ClientTypeHolder.set(clientType);
    }

    private void validateAuth(HttpServletRequest request, Method method, Class<?> clazz) {
        Auth auth = null;
        if (method.isAnnotationPresent(Auth.class)) {
            auth = method.getAnnotation(Auth.class);
        } else if (clazz.isAnnotationPresent(Auth.class)) {
            auth = clazz.getDeclaredAnnotation(Auth.class);
        }
        if (!method.isAnnotationPresent(IgnoreAuth.class)) {
            request.setAttribute(Auth.class.getName(), auth);
            if (auth != null) {
                for (Class<? extends AuthRole> authRoleClazz : effectiveRoles(auth)) {
                    ((AuthRole) applicationContext.getBean(
                            StringUtils.uncapitalize(authRoleClazz.getSimpleName()))).validate(request, auth.perms(),
                            auth.extraData());
                }
            } else {
                ((AuthRole) applicationContext.getBean(AuthLogin.class)).validate(request);
            }
        }
    }

    /**
     * @Auth 角色集归一（T2.3c 上提适配）：api 的 Auth.value() 默认改空数组（api 禁依赖 client，
     * 原默认 AuthLogin.class 编译不过），空＝「登录默认」，此处归一回 AuthLogin——
     * 与上提前裸 @Auth 的对外行为逐位等价。
     */
    @SuppressWarnings("unchecked")
    private Class<? extends AuthRole>[] effectiveRoles(Auth auth) {
        Class<? extends AuthRole>[] roles = auth.value();
        if (roles != null && roles.length > 0) {
            return roles;
        }
        return new Class[]{AuthLogin.class};
    }

    private void validateCaptcha(HttpServletRequest request, Method method) {
        if (method.isAnnotationPresent(CaptchaVerify.class)) {
            CaptchaVerify verify = method.getAnnotation(CaptchaVerify.class);
            if (FuncUtil.isEmpty(verify.field())) {
                captchaVerify(request, verify.value());
            } else {
                String captchaVerifyType = request.getParameter(verify.field());
                if (!verify.field().endsWith(CAPTCHA_SUFFIX)) {
                    captchaVerifyType += CAPTCHA_SUFFIX;
                }
                captchaVerify(request, captchaVerifyType);
            }
        }
    }

    private void validateMsgCode(HttpServletRequest request, Method method) {
        if (method.isAnnotationPresent(MsgCodeVerify.class)) {
            MsgCodeVerify verify = method.getAnnotation(MsgCodeVerify.class);
            msgCodeVerify(request, verify.value());
        }
    }

    private void captchaVerify(HttpServletRequest request, String captchaVerifyType) {
        CaptchaVerificationReq req = HttpUtil.getParamMap(request, CaptchaVerificationReq.class);
        TokenInfo token = tokenService.getToken();
        CaptchaVerifier verifier = captchaVerifier.getIfAvailable();
        if (verifier == null) {
            log.error("端点声明 @CaptchaVerify 但宿主未装配 CaptchaVerifier 实现（fail-closed，验证码校验不得静默跳过）");
            throw Validator.<RuntimeException>assertException(ErrCodeSys.SYS_ERR_MSG,
                    "[captcha] 验证码校验服务未装配，带 @CaptchaVerify 的端点已拒绝访问");
        }
        verifier.validateCaptcha(token, captchaVerifyType, req.getGraphCode());
    }

    private void msgCodeVerify(HttpServletRequest request, String msgCodeVerifyType) {
        MsgVerificationReq req = HttpUtil.getParamMap(request, MsgVerificationReq.class);
        TokenInfo token = tokenService.getToken();
        MsgCodeVerifier verifier = msgCodeVerifier.getIfAvailable();
        if (verifier == null) {
            log.error("端点声明 @MsgCodeVerify 但宿主未装配 MsgCodeVerifier 实现（fail-closed，短信码校验不得静默跳过）");
            throw Validator.<RuntimeException>assertException(ErrCodeSys.SYS_ERR_MSG,
                    "[msg] 短信验证码校验服务未装配，带 @MsgCodeVerify 的端点已拒绝访问");
        }
        verifier.validateMsgCode(token, req, msgCodeVerifyType);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        authCompletion(request, response);
        TokenHolder.remove();
        AccountContext.remove();
        // 租户档与账号档同生命周期收尾（棒2）：Tomcat 线程池复用，不并列清就会串档
        TenantContext.remove();
        // 审计上下文的兜底清理由 base-audit 的 AuditContextCleanupFilter 负责（doFilter finally，
        // 覆盖面比拦截器只多不少）——authorization 反向引用审计包会构成 Maven 循环
    }

    private void authCompletion(HttpServletRequest request, HttpServletResponse response) {
        Auth auth = (Auth) request.getAttribute(Auth.class.getName());
        if (auth != null) {
            for (Class<? extends AuthRole> authRoleClazz : effectiveRoles(auth)) {
                ((AuthRole) applicationContext.getBean(
                        StringUtils.uncapitalize(authRoleClazz.getSimpleName()))).completion(request, response);
            }
        } else {
            applicationContext.getBean(AuthLogin.class).completion(request, response);
        }
    }
}
