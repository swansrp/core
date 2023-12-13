package com.bidr.authorization.config.security;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.annotation.auth.AuthRole;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.annotation.msg.MsgCodeVerify;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.captcha.CaptchaService;
import com.bidr.authorization.service.sms.MsgVerificationService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.captcha.CaptchaVerificationReq;
import com.bidr.authorization.vo.msg.MsgVerificationReq;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    private final ApplicationContext applicationContext;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final MsgVerificationService msgVerificationService;

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
        validateClientType(request);
        validateAuth(request, method, clazz);
        validateCaptcha(request, method);
        validateMsgCode(request, method);
        return true;
    }

    private void enableCrossDomain(HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> headersInfoMap = HttpUtil.getHeadersInfoMap(request);
        if (FuncUtil.equals(BeanUtil.getActiveProfile(), "dev") ||
                FuncUtil.equals(BeanUtil.getActiveProfile(), "local")) {
            response.setHeader("Access-Control-Allow-Origin", headersInfoMap.get("origin"));
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
        if (auth != null) {
            for (Class<? extends AuthRole> authRoleClazz : auth.value()) {
                ((AuthRole) applicationContext.getBean(
                        StringUtils.uncapitalize(authRoleClazz.getSimpleName()))).validate(request, auth.perms(),
                        auth.extraData());
            }
        } else {
            ((AuthRole) applicationContext.getBean(AuthLogin.class)).validate(request);
        }
    }

    private void validateCaptcha(HttpServletRequest request, Method method) {
        if (method.isAnnotationPresent(CaptchaVerify.class)) {
            CaptchaVerify verify = method.getAnnotation(CaptchaVerify.class);
            if (FuncUtil.isEmpty(verify.field())) {
                captchaVerify(request, verify.value());
            } else {
                captchaVerify(request, request.getParameter(verify.field()));
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
        captchaService.validateCaptcha(token, captchaVerifyType, req.getGraphCode());
    }

    private void msgCodeVerify(HttpServletRequest request, String msgCodeVerifyType) {
        MsgVerificationReq req = HttpUtil.getParamMap(request, MsgVerificationReq.class);
        TokenInfo token = tokenService.getToken();
        msgVerificationService.validateMsgCode(token, req, msgCodeVerifyType);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) {
        TokenHolder.remove();
        AccountContext.remove();
    }
}
