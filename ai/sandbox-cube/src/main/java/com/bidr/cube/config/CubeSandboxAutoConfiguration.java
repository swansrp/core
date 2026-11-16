package com.bidr.cube.config;

import com.bidr.cube.CubeSandboxProvider;
import com.bidr.cube.admin.AdminSandboxManager;
import com.bidr.cube.client.CubeHttpClient;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Title: CubeSandboxAutoConfiguration
 * Description: Cube 沙箱腿自动配置——{@code my.agent.sandbox.provider=cube} 时把
 * {@link AgentSandbox} 落到 CubeSandbox 底座（{@link CubeSandboxProvider}，客户端/管理面/
 * Provider 已全部填实）。
 * <p>
 * 类级 {@code @ConditionalOnProperty(name="my.agent.sandbox.provider", havingValue="cube")}
 * （无 matchIfMissing：属性缺席不装配）：与既有 OpenHands 沙箱腿
 * {@code AgentRuntimeOpenHandsCondition} 的 provider=cube 退避分支<b>互斥</b>——双侧都是
 * 属性条件，装配面恰好一侧在场，不依赖任何 Bean 在场判定。
 * <p>
 * 🔴 <b>刻意不用 {@code @ConditionalOnBean}</b>（P2a 相位坑）：本模块的自动配置在扫描根含
 * {@code com.bidr} 的宿主里会被组件扫描提前求值，扫描相位的 Bean 在场判定早于 auto-config
 * 注册而恒 false；属性条件与相位无关，规避这一整层。
 * <p>
 * 注册入口双轨（与 {@code AgentCapabilityAutoConfiguration} 同款）：宿主扫描根含
 * {@code com.bidr} 时按扫描件装配；更窄扫描根的宿主走
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 * 宿主自建 {@link AgentSandbox} Bean 时让位（{@code @ConditionalOnMissingBean}）；
 * 配置缺席（api-url/api-key/template 未配齐）打「未装配」行并让位，不阻断启动。
 *
 * @author Sharp
 * @since 2026/11/16
 */
@Slf4j
@AutoConfiguration
@ConditionalOnProperty(name = "my.agent.sandbox.provider", havingValue = "cube")
@EnableConfigurationProperties(CubeSandboxProperties.class)
public class CubeSandboxAutoConfiguration {

    /**
     * Cube 沙箱腿：{@link AgentSandbox} 落到 CubeSandbox（会话级容器沙箱）。配置齐备才装配
     * （缺任一必填项视为未接线，打行让位——照 OpenHands 腿「装配/未装配各一行」口径）；
     * 宿主自建 {@link AgentSandbox} Bean 时让位。
     */
    @Bean
    @ConditionalOnMissingBean(AgentSandbox.class)
    public AgentSandbox cubeSandboxProvider(CubeSandboxProperties properties) {
        if (!StringUtils.hasText(properties.getApiUrl())
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getTemplate())) {
            log.info("Cube 沙箱腿未装配（配置缺席）: my.agent.sandbox.cube.api-url/api-key/template 未配齐");
            return null;
        }
        CubeHttpClient client = new CubeHttpClient(properties);
        AdminSandboxManager admin = new AdminSandboxManager(client, properties);
        log.info("Cube 沙箱腿装配: AgentSandbox=CubeSandboxProvider"
                + "（provider=cube，api-url=" + properties.getApiUrl() + "）");
        return new CubeSandboxProvider(client, admin, properties);
    }
}
