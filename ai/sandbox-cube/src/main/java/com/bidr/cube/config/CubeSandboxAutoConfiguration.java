package com.bidr.cube.config;

import com.bidr.cube.CubeSandboxProvider;
import com.bidr.cube.SandboxJanitor;
import com.bidr.cube.admin.AdminSandboxManager;
import com.bidr.cube.client.CubeHttpClient;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import com.bidr.llm.agent.session.AgentSessionStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
 * Provider 已全部填实），并按需装配 {@link SandboxJanitor}（启动孤儿对账＋idle/lifetime
 * 清扫轮，{@link org.springframework.context.SmartLifecycle} 由容器自动驱动）。
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
 * 配置缺席（api-url/api-key/template 未配齐）打「未装配」行并让位，不阻断启动
 * （janitor 同口径：Provider 腿让位/缺席或 {@code orphan-cleanup-enabled=false} 时
 * 一并让位）。
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
     * Cube HTTP 客户端（命令面/数据面传输底座，无状态）：{@link CubeSandboxProvider} 与
     * {@link SandboxJanitor} 共用同一实例（对账 delete 与清扫 killLease 同一面）。
     * 配置缺席只影响 Provider 装配让位，不影响本 Bean 构造（纯对象，零 I/O）。
     */
    @Bean
    public CubeHttpClient cubeHttpClient(CubeSandboxProperties properties) {
        return new CubeHttpClient(properties);
    }

    /**
     * Cube 沙箱腿：{@link AgentSandbox} 落到 CubeSandbox（会话级容器沙箱）。配置齐备才装配
     * （缺任一必填项视为未接线，打行让位——照 OpenHands 腿「装配/未装配各一行」口径）；
     * 宿主自建 {@link AgentSandbox} Bean 时让位。
     */
    @Bean
    @ConditionalOnMissingBean(AgentSandbox.class)
    public AgentSandbox cubeSandboxProvider(CubeSandboxProperties properties, CubeHttpClient client) {
        if (!StringUtils.hasText(properties.getApiUrl())
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getTemplate())) {
            log.info("Cube 沙箱腿未装配（配置缺席）: my.agent.sandbox.cube.api-url/api-key/template 未配齐");
            return null;
        }
        AdminSandboxManager admin = new AdminSandboxManager(client, properties);
        log.info("Cube 沙箱腿装配: AgentSandbox=CubeSandboxProvider"
                + "（provider=cube，api-url=" + properties.getApiUrl() + "）");
        return new CubeSandboxProvider(client, admin, properties);
    }

    /**
     * Cube 沙箱清扫器：启动孤儿对账＋idle/lifetime 清扫轮。装配条件＝
     * {@code orphan-cleanup-enabled} 为 true（properties 缺省 true）且 Provider 腿已装配
     * （在场 Bean 是本类的 {@link CubeSandboxProvider} 实例）——宿主自建 AgentSandbox
     * 让位/配置缺席让位时一并让位，返回 null＝NullBean（同「装配/未装配各一行」口径）。
     * <p>
     * 用 {@link ObjectProvider} 相位安全求值而非 {@code @ConditionalOnBean}
     * （P2a 同款规避：Bean 相位判定不可靠）；判定放方法体内而非注解条件，
     * 单一事实源＝properties 对象。
     */
    @Bean
    @ConditionalOnMissingBean(SandboxJanitor.class)
    public SandboxJanitor sandboxJanitor(ObjectProvider<AgentSandbox> agentSandbox,
                                         ObjectProvider<AgentSessionStore> sessionStores,
                                         CubeHttpClient client,
                                         CubeSandboxProperties properties) {
        if (!properties.isOrphanCleanupEnabled()) {
            log.info("Cube 沙箱清扫器未装配（my.agent.sandbox.cube.orphan-cleanup-enabled=false）");
            return null;
        }
        if (!(agentSandbox.getIfAvailable() instanceof CubeSandboxProvider provider)) {
            log.info("Cube 沙箱清扫器未装配（Provider 腿缺席/让位，无活跃租约可清扫）");
            return null;
        }
        log.info("Cube 沙箱清扫器装配: interval=" + properties.getCleanupInterval() + "s"
                + " idle-timeout=" + properties.getIdleTimeout() + "s"
                + " max-lifetime=" + properties.getMaxLifetime() + "s"
                + " owner=" + properties.getOwnerId()
                + " 会话活跃豁免idle=on");
        return new SandboxJanitor(provider, client, properties, sessionStores);
    }
}
