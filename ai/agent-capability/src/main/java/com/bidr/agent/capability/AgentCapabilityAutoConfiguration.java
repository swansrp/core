package com.bidr.agent.capability;

import com.bidr.agent.capability.file.BlobStoreAgentFileStore;
import com.bidr.blob.BlobStore;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentCapabilityAutoConfiguration
 * Description: agent 能力装配层自动配置（P0a）——把 llm 协议端口落到具体底座：
 * <ul>
 *     <li>{@link AgentFileStore}：类路径有 {@link BlobStore} 且容器里有该 Bean 时，
 *     装配 {@link BlobStoreAgentFileStore}（对象存储承载会话附件）；</li>
 *     <li>业务/宿主自建 {@link AgentFileStore} Bean 即让位（{@code @ConditionalOnMissingBean}）。</li>
 * </ul>
 * 缺席语义：无 store-blob 或无 blob 实现 ⇒ 本配置整体不装配，llm 的附件端点/能力位自然降级
 * （{@code /agents} 不出 {@code files.upload}），既有会话行为逐字节不变。
 * 注册入口：{@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}。
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
            @Value("${llm.agent.file-tools.enabled:true}") boolean fileToolsEnabled) {
        return args -> {
            if (!fileToolsEnabled) {
                // I-r2-1 整体回退：开关关时能力位与注入面一起退，启动日志明确显示 off
                log.info("Agent 能力清单: file-tools=off（llm.agent.file-tools.enabled=false，"
                        + "附件能力整体回退）, files.upload=off, files.read=off");
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
            }
            log.info("Agent 能力清单: files.upload={}, files.read={}",
                    capabilities.contains(AgentFileCapabilityResolver.FILES_UPLOAD) ? "on" : "off",
                    capabilities.contains(AgentFileCapabilityResolver.FILES_READ) ? "on" : "off");
        };
    }
}