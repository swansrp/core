package com.bidr.kernel.config.db;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;

/**
 * Title: MybatisPlusInterceptorCustomizer
 * Description: MybatisPlusInterceptor 拦截器链的自定义扩展点（照 Spring Boot
 * XxxCustomizer 惯例形态）。实现 bean 在框架内置拦截器（分页/动态表名）<b>之前</b>
 * 被依次应用——即经本扩展点 addInnerInterceptor 的拦截器物理位于链头部，
 * 供"必须先于分页执行"的行级改写类拦截器（如多租户 TenantLine）使用。
 * <p>向后兼容承诺：容器中无任何实现 bean 时，拦截器链与旧版逐字节相同。</p>
 * <p>纪律：本接口属共享框架面，只加不改；实现方自行保证幂等与顺序依赖的自声明
 * （多个 customizer 按 {@code @Order} 升序应用）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-19
 */
@FunctionalInterface
public interface MybatisPlusInterceptorCustomizer {

    /**
     * 向拦截器链添加自定义 InnerInterceptor（在本 bean 方法内先于分页/动态表名执行）。
     */
    void customize(MybatisPlusInterceptor interceptor);
}
