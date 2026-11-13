package com.bidr.agent.capability;

import com.bidr.agent.capability.artifact.ArtifactService;
import com.bidr.agent.capability.file.BlobStoreAgentFileStore;
import com.bidr.blob.BlobStore;
import com.bidr.document.spi.ConvertStep;
import com.bidr.gotenberg.GotenbergBaseUrlCondition;
import com.bidr.llm.agent.file.AgentArtifactGenerator;
import com.bidr.llm.agent.file.AgentFileCapabilityResolver;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.document.FileMarkdownTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentCapabilityAutoConfiguration
 * Description: agent 能力装配层自动配置（P0a，P2a 增产物腿）——把 llm 协议端口落到具体底座：
 * <ul>
 *     <li>{@link AgentFileStore}：容器里有 {@link BlobStore} Bean 时装配
 *     {@link BlobStoreAgentFileStore}（对象存储承载会话附件）；</li>
 *     <li>{@link AgentArtifactGenerator}（P2a）：gotenberg 腿配置键 {@code tool.gotenberg.base-url}
 *     非空（{@link GotenbergBaseUrlCondition}，与 gotenberg 腿的装配条件同键同 Environment）
 *     且 BlobStore 在场时装配 {@link ArtifactService}（Markdown→docx/pdf 产物落对象存储）。
 *     🔴 <b>条件故意不用 {@code @ConditionalOnBean(ConvertStep.class)}</b>：本类在
 *     {@code @AutoConfiguration} 元注解 {@code @Configuration} 的作用下会被扫描根
 *     {@code com.bidr} 当组件扫进来（imports 侧成重复件被跳过），扫描相位的 @Bean 条件求值
 *     早于 auto-config 排序口径——跨 auto-config 的 Bean 在场判定会先于 ConvertStep 注册而恒 false
 *     （2026-11-14 e2e 实证，debug 条件报告留档）；属性条件与相位无关，规避这一整层。</li>
 *     <li>业务/宿主自建同类型端口 Bean 即让位（{@code @ConditionalOnMissingBean}）。</li>
 * </ul>
 * 缺席语义：无 BlobStore Bean ⇒ 存储腿/产物腿都不装配；gotenberg 未配置（base-url 空）⇒ 产物腿
 * 不装配——llm 的能力位/工具自然降级（{@code /agents} 不出对应能力位，{@code generate_artifact}
 * 工具回「产物生成未启用」），既有会话行为逐字节不变。
 * 注册入口双轨：宿主扫描根含 {@code com.bidr} 时按扫描件装配（epc 形态）；更窄扫描根的宿主走
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
 * base-url 非空时 ConvertStep Bean 由 {@code GotenbergAutoConfiguration} 保证在场（同一键同一判定），
 * 而且本模块对 gotenberg 是 compile 直依（类必在 classpath，方法签名无加载风险）。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({BlobStore.class, AgentFileStore.class})
public class AgentCapabilityAutoConfiguration {

    /**
     * 会话附件存储（对象存储实现）：宿主自建同类型 Bean 时让位
     */
    @Bean
    @ConditionalOnBean(BlobStore.class)
    @ConditionalOnMissingBean(AgentFileStore.class)
    public AgentFileStore blobStoreAgentFileStore(BlobStore blobStore) {
        return new BlobStoreAgentFileStore(blobStore);
    }

    /**
     * 会话产物生成（P2a）：gotenberg 腿配置键非空＋BlobStore 在场才装配——
     * 宿主自建 {@link AgentArtifactGenerator} Bean 时让位
     */
    @Bean
    @Conditional(GotenbergBaseUrlCondition.class)
    @ConditionalOnBean(BlobStore.class)
    @ConditionalOnMissingBean(AgentArtifactGenerator.class)
    public AgentArtifactGenerator artifactService(ConvertStep convertStep, BlobStore blobStore) {
        return new ArtifactService(convertStep, blobStore);
    }

    /**
     * 装配完成后的能力清单一行志（口径与 {@link AgentFileCapabilityResolver#current()} 同源）：
     * 解析器在场用它推导，缺席则按端口/工具 Bean 直接判——{@code files.upload=off} 也打出来，
     * 现场一眼分清是"没装对象存储"还是"没装文档工具"。
     * <p>多 Bean 安全（m-4）：全部用 {@code getIfUnique()}，多候选取值不唯一时按"能力不可用"降级，
     * 不让启动期一行志把 {@code NoUniqueBeanDefinitionException} 抛成启动失败。
     */
    @Bean
    public ApplicationRunner agentCapabilityReporter(
            ObjectProvider<AgentFileCapabilityResolver> resolverProvider,
            ObjectProvider<AgentFileStore> fileStoreProvider,
            ObjectProvider<FileMarkdownTool> fileMarkdownToolProvider,
            ObjectProvider<AgentArtifactGenerator> artifactGeneratorProvider,
            @Value("${llm.agent.file-tools.enabled:true}") boolean fileToolsEnabled) {
        return args -> {
            if (!fileToolsEnabled) {
                // I-r2-1 整体回退：开关关时能力位与注入面一起退，启动日志明确显示 off
                log.info("Agent 能力清单: file-tools=off（llm.agent.file-tools.enabled=false，"
                        + "附件能力整体回退）, files.upload=off, files.read=off, artifacts.doc=off");
                return;
            }
            AgentFileCapabilityResolver resolver = resolverProvider.getIfUnique();
            List<String> capabilities;
            if (resolver != null) {
                capabilities = resolver.current();
            } else {
                capabilities = new ArrayList<>();
                if (fileStoreProvider.getIfUnique() != null) {
                    capabilities.add(AgentFileCapabilityResolver.FILES_UPLOAD);
                }
                if (fileMarkdownToolProvider.getIfUnique() != null) {
                    capabilities.add(AgentFileCapabilityResolver.FILES_READ);
                }
                if (artifactGeneratorProvider.getIfUnique() != null) {
                    capabilities.add(AgentFileCapabilityResolver.ARTIFACTS_DOC);
                }
            }
            log.info("Agent 能力清单: files.upload={}, files.read={}, artifacts.doc={}",
                    capabilities.contains(AgentFileCapabilityResolver.FILES_UPLOAD) ? "on" : "off",
                    capabilities.contains(AgentFileCapabilityResolver.FILES_READ) ? "on" : "off",
                    capabilities.contains(AgentFileCapabilityResolver.ARTIFACTS_DOC) ? "on" : "off");
        };
    }
}