package com.bidr.audit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 审计框架配置（前缀 {@code bidr.audit-trail}；显式注解模式零配置，本类只服务
 * <b>默认拦截模式</b>）。注册形态照 {@code ResponseResultProperty} 先例：
 * {@code @Configuration} 落 {@code com.bidr.audit}，由宿主
 * {@code BaseApplication} 的 {@code @ComponentScan("com.bidr")} 扫到。
 *
 * <p><b>默认拦截模式</b>（{@code auto.enabled=true} 时 {@code AuditTrailAutoAdvisor} 装配）：
 * "默认拦截＋显式豁免"——拦截面内的全部写端点默认产审计行（无注解也组装，bizType 为 null
 * 的行由宿主 sink 定夺丢弃），{@link AuditTrailIgnore} 逐个摘除，类级 {@code @AuditTrail}
 * 作为共享映射面（bizType/visibility）并兼作框架继承写面的放行闸。方法级 {@code @AuditTrail}
 * 的端点跳过默认拦截（防双记，语义由显式注解切面全权负责）。</p>
 *
 * <p>拦截面＝两腿并集（沿 inkhub 实测定案，勿改 {@code @target}/{@code @within} 单条——
 * Spring 代理层 classFilter 全宽松会给全应用建代理）：{@code within(controllerPackages..*)}
 * 腿拦业务包内自身声明的写方法；{@code execution(* frameworkBaseClasses+.*(..))} 腿拦继承自
 * 框架基类（如 {@code BaseAdminController}）的 Portal 写尾段——{@code within()} 按方法声明类
 * 求值，单用会整体漏掉继承写面。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
@Data
@Configuration
@ConfigurationProperties("bidr.audit-trail")
public class AuditTrailProperties {

    private Auto auto = new Auto();

    /**
     * 默认拦截模式配置。
     */
    @Data
    public static class Auto {

        /** 总开关（false＝advisor 不装配，宿主零行为变化——显式注解模式不受影响） */
        private boolean enabled = false;

        /**
         * 业务控制器包前缀列表（{@code within(pkg..*)} 腿）。方法声明类以任一前缀开头
         * 即视为业务声明方法，进"应记"评估。
         */
        private List<String> controllerPackages = new ArrayList<>();

        /**
         * 框架控制器基类全限定名列表（{@code execution(* fqn+.*(..))} 腿，如
         * {@code com.bidr.kernel.controller.BaseAdminController}）。该腿按目标类求值不分包：
         * 声明在框架基类、但目标类不在 {@link #controllerPackages} 内且无类级 {@code @AuditTrail}
         * 的端点视为框架会话/平台面，不记（防 execution 腿把非业务域的框架继承控制器卷进来）。
         */
        private List<String> frameworkBaseClasses = new ArrayList<>();

        /**
         * 框架 Portal 读路径尾段白名单（请求路径包含即视为读语义，放行不记）。
         * 缺省值＝kernel {@code BaseAdminController} 的 POST 读尾段（检索/统计/透视/导入进度），
         * 框架自身路径而非业务常量；宿主可整体覆写。
         */
        private List<String> readPathTails = new ArrayList<>(Arrays.asList(
                "/general/", "/advanced/", "/pivot", "/import/add/progress", "/import/update/progress"));

        /** 写动词集（HTTP method 大写；集合外的一律放行不记） */
        private Set<String> writeMethods = new LinkedHashSet<>(Arrays.asList("POST", "PUT", "DELETE", "PATCH"));

        /**
         * 分组键兜底参数名：默认拦截模式下注解/resolver 都没给出 group 时，从绑定入参/
         * 路径模板变量按此名取（缺省 {@code spaceId}——多空间应用的最常见命名；宿主可改）。
         */
        private String groupParam = "spaceId";
    }
}
