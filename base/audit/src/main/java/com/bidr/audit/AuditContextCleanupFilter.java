package com.bidr.audit;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 审计上下文请求级兜底清理：{@code doFilter} 的 finally 里 {@link AuditContext#clear()}。
 *
 * <p><b>为什么由本模块自己清而不是 {@code AuthInterceptor}</b>：审计上下文的正源在
 * base-audit；authorization 反向引用它会构成 Maven 循环。清理职责跟着正源走。</p>
 *
 * <p><b>覆盖面</b>：主清理在 {@link AuditTrailAspect}／{@link AuditTrailAutoAdvisor} 的
 * 方法级 finally；本过滤器只兜一个盲区——"注解方法被内部调用（自调用绕过代理）、切面
 * 不生效"时方法体内 put 的残留。过滤器在 DispatcherServlet 之外层，收尾晚于
 * HandlerInterceptor，覆盖面只多不少。Tomcat 线程池复用，不清即串档。</p>
 *
 * @author sharp (audit 抽取 base/audit：清理责任随正源 2026-10-25)
 */
@Component
public class AuditContextCleanupFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuditContext.clear();
        }
    }
}
