package com.bidr.kernel.config.swagger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Title: SwaggerPrefixConfiguration
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/19 14:00
 */
@Configuration
public class SwaggerPrefixConfiguration implements WebMvcConfigurer {

    @Value("${swagger.prefix:}")
    private String swaggerPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (isPrefixSet()) {
            registry.addResourceHandler(swaggerPrefix + "/swagger-ui/**").addResourceLocations("classpath:/META-INF/resources/webjars/springfox-swagger-ui/");
            registry.addResourceHandler(swaggerPrefix + "/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        }
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        if (isPrefixSet()) {
            registry.addRedirectViewController(swaggerPrefix + "/v3/api-docs", "/v3/api-docs").setKeepQueryParams(true);
            registry.addRedirectViewController(swaggerPrefix + "/swagger-resources", "/swagger-resources");
            registry.addRedirectViewController(swaggerPrefix + "/swagger-resources/configuration/ui", "/swagger-resources/configuration/ui");
            registry.addRedirectViewController(swaggerPrefix + "/swagger-resources/configuration/security", "/swagger-resources/configuration/security");
            registry.addRedirectViewController("/swagger-ui/index.html", "/404");
        }
    }

    private boolean isPrefixSet() {
        return swaggerPrefix != null && !"".equals(swaggerPrefix) && !"/".equals(swaggerPrefix);
    }

}
