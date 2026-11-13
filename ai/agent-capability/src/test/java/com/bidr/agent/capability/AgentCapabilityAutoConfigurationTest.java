package com.bidr.agent.capability;

import com.bidr.agent.capability.artifact.ArtifactService;
import com.bidr.blob.BlobStore;
import com.bidr.document.spi.ConvertStep;
import com.bidr.gotenberg.GotenbergAutoConfiguration;
import com.bidr.llm.agent.file.AgentArtifactGenerator;
import com.bidr.llm.agent.file.AgentFileStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/13
 */
class AgentCapabilityAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GotenbergAutoConfiguration.class,
                    AgentCapabilityAutoConfiguration.class))
            // BlobStore 是组件扫描的 @Service 具体类：宿主形态用实例桩模拟（e2e Local OSS 同款）
            .withBean(BlobStore.class, () -> mock(BlobStore.class));

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
}
