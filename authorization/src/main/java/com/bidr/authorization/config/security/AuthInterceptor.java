package com.sharp.authorization.config.security;

import com.sharp.authorization.constants.common.RequestConst;
import com.sharp.kernel.utils.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;

/**
 * Title: AuthInterceptor
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/17 15:52
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        enableCrossDomain(request, response);

        return true;
    }

    private void enableCrossDomain(HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> headersInfoMap = HttpUtil.getHeadersInfoMap(request);
        response.setHeader("Access-Control-Allow-Origin", headersInfoMap.get("origin"));
        response.setHeader("Access-Control-Allow-Headers", RequestConst.getAllHeader());
        response.setHeader("Access-Control-Allow-Methods", RequestConst.getAllMethod());
    }
}
