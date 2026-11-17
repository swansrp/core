package com.bidr.authorization.client.session;

import com.bidr.authorization.client.AuthzCheckApi;
import com.bidr.authorization.client.login.SubsystemLoginService;
import com.bidr.authorization.client.login.SubsystemLoginSyncHook;
import com.bidr.authorization.client.login.SubsystemPasswordLoginController;
import com.bidr.authorization.client.oauth2.IdpTokenVerifier;
import com.bidr.authorization.client.oauth2.OAuth2Client;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.identity.mirror.dao.repository.IdmTenantMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserMirrorService;
import com.bidr.identity.mirror.dao.repository.IdmUserTenantMirrorService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Title: SubsystemSessionAutoConfiguration
 * Description: 子系统自签会话的自动装配——仅当宿主带 {@code identity-mirror}（镜像 repo bean 在位）时生效。
 *
 * <p>🔴 主系统退位腿：墨枢经 {@code authorization → authorization-client} 拿到本 jar，
 * 但 {@code authorization-client} 对 {@code identity-mirror} 是 <b>optional</b> 依赖（不传递），
 * 主系统 classpath 上没有镜像 ⇒ 本装配不注册（{@code @ConditionalOnClass} 用字符串名判定，
 * 宿主组件扫描覆盖本包时也不会触发类加载）。子系统的两条腿：显式依赖
 * {@code identity-mirror}（epc-ai-common 已声明）＋镜像 repo 被扫描为 bean，装配才生效。</p>
 *
 * <p>bean 由本类 {@code @Bean} 方法注册、{@link SubsystemSessionService} 本身无组件注解——
 * 镜像缺席时它不会被任何扫描面加载。</p>
 *
 * <p>🔴 登录 service/controller 与本会话 service <b>同配置类</b>注册（U18 教训：不跨配置类赌注册序）：
 * 方法参数直接注入 {@link SubsystemSessionService}，条件全部在扫描期可判，不依赖任何
 * {@code @AutoConfigureAfter} 或 {@code @Bean} 登记先后。</p>
 *
 * @author Sharp
 * @since 2026-11-04
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.bidr.identity.mirror.dao.repository.IdmUserMirrorService")
@ConditionalOnBean(value = TokenService.class,
        name = {"idmUserMirrorService", "idmUserTenantMirrorService", "idmTenantMirrorService"})
@ConditionalOnMissingBean(SubsystemSessionService.class)
public class SubsystemSessionAutoConfiguration {

    @Bean
    public SubsystemSessionService subsystemSessionService(IdmUserMirrorService userMirrorService,
                                                           IdmUserTenantMirrorService userTenantMirrorService,
                                                           IdmTenantMirrorService tenantMirrorService,
                                                           TokenService tokenService,
                                                           ObjectProvider<AuthzCheckApi> authzCheckApi) {
        return new SubsystemSessionService(userMirrorService, userTenantMirrorService,
                tenantMirrorService, tokenService, authzCheckApi);
    }

    @Bean
    @ConditionalOnMissingClass("com.bidr.authorization.controller.login.LoginController")
    @ConditionalOnMissingBean(SubsystemLoginService.class)
    public SubsystemLoginService subsystemLoginService(ObjectProvider<OAuth2Client> oauth2Client,
                                                       ObjectProvider<IdpTokenVerifier> idpTokenVerifier,
                                                       SubsystemSessionService sessionService,
                                                       ObjectProvider<SubsystemLoginSyncHook> syncHook) {
        return new SubsystemLoginService(oauth2Client, idpTokenVerifier, sessionService, syncHook);
    }

    @Bean
    @ConditionalOnMissingClass("com.bidr.authorization.controller.login.LoginController")
    @ConditionalOnMissingBean(SubsystemPasswordLoginController.class)
    public SubsystemPasswordLoginController subsystemPasswordLoginController(SubsystemLoginService loginService) {
        return new SubsystemPasswordLoginController(loginService);
    }
}
