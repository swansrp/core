/**
 * Copyright © 2018 SRC-TJ Service TG. All rights reserved.
 *
 * @Package: com.srct.service.config.swagger
 * @author: xxfore
 * @since: 2018-04-30 15:07
 */
package com.bidr.kernel.config.swagger;

import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.ParameterBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.schema.ModelRef;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Parameter;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.spring.web.plugins.WebMvcRequestHandlerProvider;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sharp
 */

@Profile(value = {"local", "dev", "stg"})
public class SwaggerConfigBak {

    @Value("${my.swagger.config.root-package-name}")
    private String rootPackageName;

    @Bean
    public static BeanPostProcessor springfoxHandlerProviderBeanPostProcessor() {
        return new BeanPostProcessor() {

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof WebMvcRequestHandlerProvider) {
                    customizeSpringfoxHandlerMappings(getHandlerMappings(bean));
                }
                return bean;
            }

            private <T extends RequestMappingInfoHandlerMapping> void customizeSpringfoxHandlerMappings(List<T> mappings) {
                List<T> copy = mappings.stream()
                        .filter(mapping -> mapping.getPatternParser() == null)
                        .collect(Collectors.toList());
                mappings.clear();
                mappings.addAll(copy);
            }

            @SuppressWarnings("unchecked")
            private List<RequestMappingInfoHandlerMapping> getHandlerMappings(Object bean) {
                try {
                    Field field = ReflectionUtil.getField(bean.getClass(), "handlerMappings");
                    field.setAccessible(true);
                    return (List<RequestMappingInfoHandlerMapping>) field.get(bean);
                } catch (IllegalArgumentException | IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    @Bean
    public Docket createRestApi() {

        // 添加head参数start
        ParameterBuilder par = new ParameterBuilder();
        List<Parameter> pars = new ArrayList<Parameter>();
        par.name("x-access-token").description("令牌").modelRef(new ModelRef("string")).parameterType("header")
                .required(false).defaultValue("").build();
        pars.add(par.build());
        par.name("api-version").description("api版本").modelRef(new ModelRef("string")).parameterType("header")
                .required(true).defaultValue("V1.0").build();
        pars.add(par.build());
        par.name("client-type").description("终端类型(WEB/APP/WECHAT)").modelRef(new ModelRef("string"))
                .parameterType("header").required(true).defaultValue("WEB").build();
        pars.add(par.build());
        par.name("user-id").description("登录用户名").modelRef(new ModelRef("string")).parameterType("header")
                .required(false).defaultValue("").build();
        pars.add(par.build());
        par.name("authorization").description("OpenAPI 登录鉴权").modelRef(new ModelRef("string")).parameterType("header")
                .required(false).defaultValue("basic Base64(appKey:appSecret)").build();
        pars.add(par.build());
        // 添加head参数end

        return new Docket(DocumentationType.SWAGGER_2).apiInfo(apiInfo()).select()
                .apis(RequestHandlerSelectors.basePackage(rootPackageName)).paths(PathSelectors.any()).build()
                .globalOperationParameters(pars);
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                // Customer Information
                .title("BHFAE").description("Just for development").termsOfServiceUrl("http://sharp.org.cn")
                .contact(new Contact("Sharp", "", "56093273@qq.com")).version("1.0").build();
    }
}
