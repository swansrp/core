/**
 * Bhfae.com Inc. Copyright (c) 2004-2020 All Rights Reserved.
 */
package com.bidr.kernel.config.swagger;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * description 请求日志
 * 1. 导出接口api必须含有export
 *
 * @author sharuopeng
 * @version V1.0.0
 */
@Slf4j
@Component
@Profile(value = {"dev", "test", "preview"})
public class SwaggerFilter extends OncePerRequestFilter {

    @Value("${swagger.prefix:}")
    private String swaggerPrefix;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (isPrefixSet()) {
            if (uri.startsWith(swaggerPrefix + "/v3/api-docs") || uri.startsWith(swaggerPrefix + "/swagger-")) {
                request.getRequestDispatcher(uri.replace(swaggerPrefix, "")).forward(request, response);
                return;
            }
        }
        filterChain.doFilter(request, response);

    }

    private boolean isPrefixSet() {
        return swaggerPrefix != null && !"".equals(swaggerPrefix) && !"/".equals(swaggerPrefix);
    }

}
