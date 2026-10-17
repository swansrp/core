package com.bidr.kernel.config.swagger;

import io.swagger.models.auth.In;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.CorsEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.boot.actuate.endpoint.ExposableEndpoint;
import org.springframework.boot.actuate.endpoint.web.*;
import org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.annotation.ServletEndpointsSupplier;
import org.springframework.boot.actuate.endpoint.web.servlet.WebMvcEndpointHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.RequestHandler;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.oas.annotations.EnableOpenApi;
import springfox.documentation.service.*;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Swagger2的接口配置
 *
 * @author ruoyi
 */
@Configuration
@EnableOpenApi
@EnableSwagger2
public class SwaggerConfig {

    private static final Set<String> BASE_CLASS_NAMES = new HashSet<>(Arrays.asList(
            "BaseAdminController",
            "BaseAdminOrderController",
            "BaseAdminTreeController",
            "BaseBindController"
    ));

    /**
     * 是否开启swagger
     */
    @Value("${swagger.enabled}")
    private boolean enabled;

    /**
     * 设置请求的统一前缀
     */
    @Value("${swagger.pathMapping}")
    private String pathMapping;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    /**
     * 创建API
     */
    @Bean
    public Docket createRestApi() {
        return new Docket(DocumentationType.OAS_30)
                // 是否启用Swagger
                .enable(enabled)
                // 用来创建该API的基本信息，展示在文档的页面中（自定义展示的信息）
                .apiInfo(apiInfo())
                // 设置哪些接口暴露给Swagger展示
                .select()
                // 扫描所有有注解的api，用这种方式更灵活
                // .apis(RequestHandlerSelectors.withMethodAnnotation(ApiOperation.class))
                // 动态过滤
                .apis(this::filterApis)
                .apis(RequestHandlerSelectors.withClassAnnotation(RestController.class))
                // 扫描指定包中的swagger注解
                // .apis(RequestHandlerSelectors.basePackage("com.bidr"))
                // 扫描所有 .apis(RequestHandlerSelectors.any())
                .paths(PathSelectors.any()).build()
                /* 设置安全模式，swagger可以设置访问token */.securitySchemes(securitySchemes())
                .securityContexts(securityContexts()).pathMapping(pathMapping);
    }

    /**
     * 过滤逻辑：
     * - dev环境：显示所有接口
     * - 其他环境：隐藏 BASE_CLASS_NAMES 声明的方法，但保留子类定义的
     */
    private boolean filterApis(RequestHandler handler) {
        if ("dev".equalsIgnoreCase(activeProfile) || "local".equalsIgnoreCase(activeProfile)) {
            return true;
        }

        Class<?> declaringClass = handler.declaringClass();

        // 如果当前接口直接定义在 BaseXXXController 里 -> 隐藏
        if (isBaseController(declaringClass)) {
            return false;
        }
        // 如果当前接口来自子类，但方法定义在父类中 -> 隐藏
        try {
            Method method = handler.getHandlerMethod().getMethod();
            Class<?> methodDeclaringClass = method.getDeclaringClass();

            // 隐藏方法定义在 BaseXXXController 中的接口
            if (BASE_CLASS_NAMES.contains(methodDeclaringClass.getSimpleName())) {
                return false;
            }
        } catch (Exception e) {
            // 某些非标准 handler 类型，忽略
        }

        return true;
    }

    /**
     * 判断是否属于需要隐藏的基础 Controller 类
     */
    private boolean isBaseController(Class<?> clazz) {
        if (clazz == null) {
            return false;
        }
        return BASE_CLASS_NAMES.contains(clazz.getSimpleName());
    }

    /**
     * 添加摘要信息
     */
    private ApiInfo apiInfo() {
        // 用ApiInfoBuilder进行定制
        return new ApiInfoBuilder()
                // 设置标题
                .title("标题：接口文档")
                // 描述
                .description("描述：用于管理集团旗下公司的人员信息,具体包括XXX,XXX模块...")
                // 作者信息
                .contact(new Contact("Ruopeng Sha", null, null))
                // 版本
                .version("版本号:" + "2025").build();
    }

    /**
     * 安全模式，这里指定token通过Authorization头请求头传递
     */
    private List<SecurityScheme> securitySchemes() {
        List<SecurityScheme> apiKeyList = new ArrayList<SecurityScheme>();
        apiKeyList.add(new ApiKey("Authorization", "authorization", In.HEADER.toValue()));
        apiKeyList.add(new ApiKey("Open-api", "x-api-key", In.HEADER.toValue()));
        return apiKeyList;
    }

    /**
     * 安全上下文
     */
    private List<SecurityContext> securityContexts() {
        List<SecurityContext> securityContexts = new ArrayList<>();
        securityContexts.add(SecurityContext.builder().securityReferences(defaultAuth())
                .operationSelector(o -> o.requestMappingPattern().matches("/.*")).build());
        return securityContexts;
    }

    /**
     * 默认的安全上引用
     */
    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        List<SecurityReference> securityReferences = new ArrayList<>();
        securityReferences.add(new SecurityReference("Authorization", authorizationScopes));
        return securityReferences;
    }

    @Bean
    public WebMvcEndpointHandlerMapping webEndpointServletHandlerMapping(WebEndpointsSupplier webEndpointsSupplier,
                                                                         ServletEndpointsSupplier servletEndpointsSupplier,
                                                                         ControllerEndpointsSupplier controllerEndpointsSupplier,
                                                                         EndpointMediaTypes endpointMediaTypes,
                                                                         CorsEndpointProperties corsProperties,
                                                                         WebEndpointProperties webEndpointProperties,
                                                                         Environment environment) {
        List<ExposableEndpoint<?>> allEndpoints = new ArrayList();
        Collection<ExposableWebEndpoint> webEndpoints = webEndpointsSupplier.getEndpoints();
        allEndpoints.addAll(webEndpoints);
        allEndpoints.addAll(servletEndpointsSupplier.getEndpoints());
        allEndpoints.addAll(controllerEndpointsSupplier.getEndpoints());
        String basePath = webEndpointProperties.getBasePath();
        EndpointMapping endpointMapping = new EndpointMapping(basePath);
        boolean shouldRegisterLinksMapping = this.shouldRegisterLinksMapping(webEndpointProperties, environment,
                basePath);
        return new WebMvcEndpointHandlerMapping(endpointMapping, webEndpoints, endpointMediaTypes,
                corsProperties.toCorsConfiguration(), new EndpointLinksResolver(allEndpoints, basePath),
                shouldRegisterLinksMapping, null);
    }

    private boolean shouldRegisterLinksMapping(WebEndpointProperties webEndpointProperties, Environment environment,
                                               String basePath) {
        return webEndpointProperties.getDiscovery().isEnabled() && (
                StringUtils.hasText(basePath) ||
                        ManagementPortType.get(environment).equals(ManagementPortType.DIFFERENT));
    }
}
