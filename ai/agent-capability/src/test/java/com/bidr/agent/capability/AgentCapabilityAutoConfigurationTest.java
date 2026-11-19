package com.bidr.agent.capability;

import com.bidr.agent.capability.artifact.ArtifactService;
import com.bidr.llm.agent.health.AgentCapabilityHealthProbe;
import com.bidr.llm.agent.health.AgentHealthSseHub;
import com.bidr.llm.agent.health.AgentHealthStreamController;
import com.bidr.agent.capability.sandbox.OpenHandsSandboxProvider;
import com.bidr.agent.runtime.config.AgentRuntimeAutoConfiguration;
import com.bidr.agent.runtime.provider.openhands.OpenHandsClient;
import com.bidr.agent.runtime.service.AgentChatSessionService;
import com.bidr.blob.BlobStore;
import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.TranscribeStep;
import com.bidr.gotenberg.GotenbergAutoConfiguration;
import com.bidr.llm.agent.file.AgentArtifactGenerator;
import com.bidr.llm.agent.file.AgentCapabilityHealthProvider;
import com.bidr.llm.agent.file.AgentFileCapabilityResolver;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import com.bidr.llm.config.AgentHealthAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Title: AgentCapabilityAutoConfigurationTest
 * Description: 能力装配层的条件装配契约钉桩（ApplicationContextRunner，不起完整宿主）：
 * <ul>
 *     <li>gotenberg 腿（{@code tool.gotenberg.base-url} 非空 ⇒ ConvertStep Bean）与 BlobStore
 *     双双在场 ⇒ ArtifactService 装配（P2a 产物腿 on）；</li>
 *     <li>base-url 留空（gotenberg 腿缺席）⇒ 产物腿不装配，附件腿不受影响（attachments/upload on）；
 *     🔴 该腿同时钉住 {@code @AutoConfiguration(afterName=GotenbergAutoConfiguration)} 的排序——
 *     ConvertStep 是 auto-config 件（区别于组件扫描的 BlobStore），排序缺失会让
 *     {@code @ConditionalOnBean(ConvertStep.class)} 恒 false（先把 gotenberg 腿拉进同一 runner
 *     就是按真实排序形态求值）；</li>
 *     <li>宿主自建 {@link AgentArtifactGenerator} Bean ⇒ 装配让位。</li>
 *     <li>reporter 一行志（棒2 ocr 位、棒3a exec.sandbox 位）：fallback 分支（解析器缺席，
 *     runner 形态天然如此）与 off 分支都带五位——TranscribeStep/AgentSandbox Bean 在场即 on
 *     （声明级）；</li>
     *     <li>探活三件套（P3-0 迁 llm com.bidr.llm.agent.health）：llm {@link AgentHealthAutoConfiguration}
     *     无条件装配 {@link AgentCapabilityHealthProbe}/{@link AgentHealthSseHub}，宿主自建同类型
     *     Bean 时让位，探活件拿到同源 hub；{@link AgentHealthStreamController}（llm 首例 controller）
     *     按扫描拾取（@RestController 元注解 @Component，epc 扫描根 com.bidr 同款先例）＋构造注入 hub。</li>
 *     <li>沙箱腿（棒3b）：{@code my.agent.runtime.enabled=true} ⇒ {@code AgentSandbox}=
 *     {@code OpenHandsSandboxProvider} 装配；{@code enabled=false}／{@code provider=agent-system}
 *     ⇒ 不装配（属性条件口径，非 OnBean）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
@ExtendWith(OutputCaptureExtension.class)
class AgentCapabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GotenbergAutoConfiguration.class,
                    AgentCapabilityAutoConfiguration.class))
            // BlobStore 是组件扫描的 @Service 具体类：宿主形态用实例桩模拟（e2e Local OSS 同款）
            .withBean(BlobStore.class, () -> mock(BlobStore.class));

    /**
     * 沙箱腿专用 runner（棒3b）：把 agent-runtime 的 auto-config 一并拉进来按真实形态求值——
     * 沙箱腿装配条件是<b>属性条件</b>（AgentRuntimeOpenHandsCondition，P2a 相位坑的规避），
     * 这两腿同时钉住「属性开⇒AgentSandbox 装配」「属性关/legacy 腿⇒不装配」的完整口径。
     * ChatSessionService 依赖本模块自带 DAO（需数据源）：以 mock 顶替（口径同 agent-runtime 装配测试）。
     */
    private final ApplicationContextRunner sandboxRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentRuntimeAutoConfiguration.class,
                    AgentCapabilityAutoConfiguration.class))
            .withPropertyValues("my.agent.runtime.enabled=true")
            .withBean("agentChatSessionService", AgentChatSessionService.class,
                    () -> mock(AgentChatSessionService.class));

    /**
     * 探活装配专用 runner（P3-0 探活三件套迁 llm）：基础 runner 叠加 llm 的
     * {@link AgentHealthAutoConfiguration}——探活 hub/聚合器自此由 llm 装配提供，
     * 按真实 auto-config 形态求值（本模块自身装配不再含探活件）。
     */
    private final ApplicationContextRunner healthRunner = runner.withConfiguration(
            AutoConfigurations.of(AgentHealthAutoConfiguration.class));

    @Test
    @DisplayName("base-url 非空＋BlobStore 在场：ArtifactService 装配（artifacts.doc 推导前提）")
    void artifactServiceAssemblesWhenConvertLegPresent() {
        runner.withPropertyValues("tool.gotenberg.base-url=http://127.0.0.1:13000")
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentFileStore.class);
                    assertThat(context).hasSingleBean(ConvertStep.class);
                    assertThat(context).hasSingleBean(AgentArtifactGenerator.class);
                    assertThat(context.getBean(AgentArtifactGenerator.class)).isInstanceOf(ArtifactService.class);
                });
    }

    @Test
    @DisplayName("base-url 留空：产物腿缺席、附件腿照常（工具回「产物生成未启用」的前提）")
    void artifactServiceAbsentWhenBaseUrlEmpty() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AgentFileStore.class);
            assertThat(context).doesNotHaveBean(ConvertStep.class);
            assertThat(context).doesNotHaveBean(AgentArtifactGenerator.class);
        });
    }

    @Test
    @DisplayName("宿主自建 AgentArtifactGenerator：装配让位")
    void hostProvidedGeneratorWins() {
        runner.withPropertyValues("tool.gotenberg.base-url=http://127.0.0.1:13000")
                .withBean(AgentArtifactGenerator.class, () -> mock(AgentArtifactGenerator.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentArtifactGenerator.class);
                    assertThat(context.getBean(AgentArtifactGenerator.class)).isNotInstanceOf(ArtifactService.class);
                });
    }

    @Test
    @DisplayName("reporter fallback 分支：TranscribeStep 在场 ⇒ 一行志带 ocr=on（缺席 off）")
    void reporterLineCarriesOcrBitInFallbackBranch(CapturedOutput output) {
        runner.withPropertyValues("tool.gotenberg.base-url=http://127.0.0.1:13000")
                .withBean(TranscribeStep.class, () -> mock(TranscribeStep.class))
                .run(context -> {
                    // runner 形态不注册 AgentFileCapabilityResolver（llm 侧 @Component）⇒ 天然走 fallback 分支
                    context.getBean("agentCapabilityReporter", ApplicationRunner.class).run(null);
                    assertThat(output).contains("Agent 能力清单: files.upload=on, files.read=off, "
                            + "artifacts.doc=on, ocr=on, exec.sandbox=off");
                });
        runner.withPropertyValues("tool.gotenberg.base-url=http://127.0.0.1:13000")
                .run(context -> {
                    context.getBean("agentCapabilityReporter", ApplicationRunner.class).run(null);
                    assertThat(output).contains("ocr=off");
                });
    }

    @Test
    @DisplayName("reporter fallback 分支（棒3a）：AgentSandbox 端口在场 ⇒ 一行志第五位 exec.sandbox=on（缺席 off）")
    void reporterLineCarriesSandboxBitInFallbackBranch(CapturedOutput output) {
        runner.withPropertyValues("tool.gotenberg.base-url=http://127.0.0.1:13000")
                .withBean(AgentSandbox.class, () -> mock(AgentSandbox.class))
                .run(context -> {
                    context.getBean("agentCapabilityReporter", ApplicationRunner.class).run(null);
                    assertThat(output).contains("Agent 能力清单: files.upload=on, files.read=off, "
                            + "artifacts.doc=on, ocr=off, exec.sandbox=on");
                });
        runner.withPropertyValues("tool.gotenberg.base-url=http://127.0.0.1:13000")
                .run(context -> {
                    context.getBean("agentCapabilityReporter", ApplicationRunner.class).run(null);
                    assertThat(output).contains("ocr=off, exec.sandbox=off");
                });
    }

    @Test
    @DisplayName("reporter off 分支：总开关关 ⇒ 一行志五位逐位列 off（含 exec.sandbox=off）")
    void reporterOffBranchListsAllFiveBitsOff(CapturedOutput output) {
        runner.withPropertyValues("llm.agent.file-tools.enabled=false")
                .run(context -> {
                    context.getBean("agentCapabilityReporter", ApplicationRunner.class).run(null);
                    assertThat(output).contains("file-tools=off").contains("ocr=off")
                            .contains("exec.sandbox=off");
                });
    }

    @Test
    @DisplayName("探活件：无条件装配；宿主自建同类型 Bean 时让位")
    void healthProbeAssemblesAndYieldsToHost() {
        healthRunner.run(context -> assertThat(context).hasSingleBean(AgentCapabilityHealthProbe.class));
        AgentCapabilityHealthProbe hostProbe = new AgentCapabilityHealthProbe(java.util.Collections.<com.bidr.document.spi.AgentHealthContributor>emptyList(), 0, null);
        healthRunner.withBean(AgentCapabilityHealthProbe.class, () -> hostProbe)
                .run(context -> assertThat(context.getBean(AgentCapabilityHealthProbe.class))
                        .isSameAs(hostProbe));
    }

    @Test
    @DisplayName("健康度 SSE hub：无条件装配＋宿主自建让位；探活件拿到同源 hub（探活↔SSE 接线）")
    void healthSseHubAssemblesYieldsAndWiresProbe() {
        healthRunner.run(context -> {
            assertThat(context).hasSingleBean(AgentHealthSseHub.class);
            assertThat(context.getBean(AgentCapabilityHealthProbe.class).healthHub())
                    .as("探活件构造注入的 hub 与容器 Bean 同源")
                    .isSameAs(context.getBean(AgentHealthSseHub.class));
        });
        AgentHealthSseHub hostHub = new AgentHealthSseHub();
        healthRunner.withBean(AgentHealthSseHub.class, () -> hostHub)
                .run(context -> {
                    assertThat(context.getBean(AgentHealthSseHub.class)).isSameAs(hostHub);
                    assertThat(context.getBean(AgentCapabilityHealthProbe.class).healthHub()).isSameAs(hostHub);
                });
    }

    @Test
    @DisplayName("健康度流端点（llm 内首例 controller，自 agent-capability 迁入）：@RestController 扫描拾取＋构造注入 hub")
    void healthStreamControllerPickedUpByScanAndWiredToHub() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(AgentHealthSseHub.class);
        context.register(HealthScanConfig.class);
        context.refresh();
        try {
            assertThat(context.getBean(AgentHealthStreamController.class))
                    .as("扫描拾取成功且 hub 构造注入（缺 Bean 会抛 NoSuchBeanDefinitionException）")
                    .isNotNull();
        } finally {
            context.close();
        }
    }

    /** health 包扫描桩（epc 形态的最小还原）：默认过滤器只捞 @RestController 元注解件——
     *  包内其余类（hub/探活件）无 stereotype 注解不入选，hub 以 registerBean 顶上 */
    @Configuration
    @ComponentScan(basePackages = "com.bidr.llm.agent.health")
    static class HealthScanConfig {
    }



    @Test
    @DisplayName("沙箱腿（棒3b）：runtime enabled=true ⇒ AgentSandbox=OpenHandsSandboxProvider 装配（exec.sandbox 位点亮前提）")
    void sandboxAssemblesWhenOpenHandsRuntimeEnabled() {
        sandboxRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenHandsClient.class);
            assertThat(context).hasSingleBean(AgentSandbox.class);
            assertThat(context.getBean(AgentSandbox.class)).isInstanceOf(OpenHandsSandboxProvider.class);
        });
    }

    @Test
    @DisplayName("沙箱腿：enabled=false 或 provider=agent-system ⇒ 不装配（llm 工具回「沙箱未启用」）")
    void sandboxAbsentWhenRuntimeDisabledOrLegacyProvider() {
        sandboxRunner.withPropertyValues("my.agent.runtime.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenHandsClient.class);
                    assertThat(context).doesNotHaveBean(AgentSandbox.class);
                });
        sandboxRunner.withPropertyValues("my.agent.runtime.provider=agent-system")
                .run(context -> {
                    // legacy 腿自身可装配，但沙箱腿只认 openhands
                    assertThat(context).doesNotHaveBean(OpenHandsClient.class);
                    assertThat(context).doesNotHaveBean(AgentSandbox.class);
                });
        // review #9（Condition 去 trim）的 no-trim 语义在 AgentRuntimeOpenHandsConditionTest
        // 直测钉住（withPropertyValues 对值 trim，「true 」形态到不了 Condition，此处测不出）
    }


    @Test
    @DisplayName("探活聚合器收集接线（2026-11-18 架构化）：context 里全部 AgentHealthContributor bean 都进聚合"
            + "——腿 bean 在场才收集（各腿装配条件在各自模块的 AutoConfiguration 测试里钉）")
    void probeAggregatesAllContributorBeans() {
        com.bidr.document.spi.AgentHealthContributor stub =
                new com.bidr.document.spi.AgentHealthContributor() {
            @Override public String capability() { return "ocr"; }
            @Override public String probeName() { return "mineru"; }
            @Override public int order() { return 100; }
            @Override public com.bidr.document.spi.AgentHealthContributor.HealthReport probe() {
                return com.bidr.document.spi.AgentHealthContributor.HealthReport.reachable();
            }
        };
        healthRunner.withBean(com.bidr.document.spi.AgentHealthContributor.class, () -> stub)
                .run(context -> {
                    AgentCapabilityHealthProbe probe = context.getBean(AgentCapabilityHealthProbe.class);
                    probe.run(null);
                    assertThat(probe.snapshot()).containsEntry("ocr", AgentCapabilityHealthProvider.UP);
                });
    }
}
