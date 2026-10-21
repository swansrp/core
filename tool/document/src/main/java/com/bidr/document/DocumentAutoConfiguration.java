package com.bidr.document;

import com.bidr.document.builtin.MineruAsyncTranscribeLeg;
import com.bidr.document.builtin.MineruTranscribeLeg;
import com.bidr.document.spi.ArtifactStep;
import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.EnrichStep;
import com.bidr.document.spi.IntakeStep;
import com.bidr.document.spi.TranscribeStep;
import com.bidr.llm.config.LlmDefaultAutoConfiguration;
import com.bidr.llm.provider.ModelConfigProvider;
import com.bidr.mineru.MineruCloudClient;
import com.bidr.mineru.MineruLocalClient;
import com.bidr.mineru.MineruProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * Title: DocumentAutoConfiguration
 * Description: 文件底座默认装配（starter 风格，业务可覆盖）：
 * <ul>
 *   <li>腿收集＝List 收口（{@code ObjectProvider.orderedStream}）——classpath 上多条同槽腿
 *       <b>不会</b>炸容器（NoUniqueBeanDefinitionException 的修法面）；</li>
 *   <li>transcribe 选型＝{@link TranscribeLegSelector}（{@code tool.document.transcriber} 一把键，
 *       三态语义：没配→内置；配了包没引→WARN 走内置；配了且在→增强腿）；
 *       <b>MinerU 腿本模块自带</b>（{@link MineruTranscribeLeg}，键点名 mineru 才装），
 *       业务引 document 即可用，无需另装配；</li>
 *   <li>视觉配置默认适配器＝{@link LlmVisionConfigProvider}（接 core llm 的
 *       {@link ModelConfigProvider}，purpose=VISION：数据库系统参数优先、{@code llm.vision.*} 回落），
 *       业务注册自己的 {@link VisionConfigProvider} 即覆盖；</li>
 *   <li>入口三件（{@link DocumentProcessor}／{@link FileMarkdownService}／
 *       {@code agent.FileMarkdownTool}）缺席时才装，业务自建以 {@code @ConditionalOnMissingBean} 让位。</li>
 * </ul>
 * 配置面只读 Spring Environment（{@code tool.document.*}／{@code mineru.*}），零 sys_config；
 * 视觉模型配置经上面的适配器才触达 sys_config，本装配类自身不读库。
 *
 * @author Sharp
 * @since 2026-10-20
 */
@AutoConfiguration
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DocumentProcessor.class)
    public DocumentProcessor documentProcessor(DocumentProperties properties,
            ObjectProvider<ConvertStep> convertSteps,
            ObjectProvider<TranscribeStep> transcribeSteps,
            ObjectProvider<VisionConfigProvider> visionConfigProvider,
            ObjectProvider<EnrichStep> enrichSteps,
            ObjectProvider<ArtifactStep> artifactStep,
            ObjectProvider<IntakeStep> intakeStep) {
        return new DocumentProcessor(properties,
                convertSteps.orderedStream().collect(Collectors.toList()),
                TranscribeLegSelector.select(
                        transcribeSteps.orderedStream().collect(Collectors.toList()),
                        properties.getTranscriber()),
                visionConfigProvider.getIfUnique(),
                enrichSteps.orderedStream().collect(Collectors.toList()),
                artifactStep.getIfUnique(),
                intakeStep.getIfUnique());
    }

    /**
     * 视觉配置默认适配器：{@code llm.proxy.*} 口径与文本模型同源（代理构造复用
     * {@link LlmDefaultAutoConfiguration#buildProxy}），容器无 {@link ModelConfigProvider} 时
     * 适配器解析返回 null＝多模态只能调用时显式传入，不抛
     */
    @Bean
    @ConditionalOnMissingBean(VisionConfigProvider.class)
    public VisionConfigProvider documentVisionConfigProvider(ObjectProvider<ModelConfigProvider> configProvider,
            @org.springframework.beans.factory.annotation.Value("${llm.proxy.enable:false}") boolean proxyEnable,
            @org.springframework.beans.factory.annotation.Value("${llm.proxy.host:}") String proxyHost,
            @org.springframework.beans.factory.annotation.Value("${llm.proxy.port:0}") int proxyPort) {
        return new LlmVisionConfigProvider(configProvider.getIfAvailable(),
                LlmDefaultAutoConfiguration.buildProxy(proxyEnable, proxyHost, proxyPort));
    }

    /**
     * 文件 → Markdown 入口：加工本体走上面的 {@link DocumentProcessor}（业务自建门面时本 Bean 仍可用）
     */
    @Bean
    @ConditionalOnMissingBean(FileMarkdownService.class)
    public FileMarkdownService fileMarkdownService(ObjectProvider<ModelConfigProvider> configProvider,
            ObjectProvider<DocumentProcessor> processor,
            @org.springframework.beans.factory.annotation.Value("${llm.proxy.enable:false}") boolean proxyEnable,
            @org.springframework.beans.factory.annotation.Value("${llm.proxy.host:}") String proxyHost,
            @org.springframework.beans.factory.annotation.Value("${llm.proxy.port:0}") int proxyPort) {
        return new FileMarkdownService(configProvider.getIfAvailable(),
                LlmDefaultAutoConfiguration.buildProxy(proxyEnable, proxyHost, proxyPort),
                processor.getIfAvailable());
    }

    /**
     * 通用「读文件」agent 工具（read_file）：包 {@link FileMarkdownService}，挂进 agent 工具清单即用；
     * 默认 2 万字截断防大文档灌爆上下文，失败回文本不抛（模型可自纠）
     */
    @Bean
    @ConditionalOnMissingBean(com.bidr.document.agent.FileMarkdownTool.class)
    public com.bidr.document.agent.FileMarkdownTool fileMarkdownTool(FileMarkdownService fileMarkdownService) {
        return new com.bidr.document.agent.FileMarkdownTool(fileMarkdownService);
    }

    /**
     * MinerU 内置增强转写腿（本门面处理路径自带的一条腿）：两道条件——
     * {@code mineru.enabled}（缺省开，关掉＝{@link MineruProperties} 与本腿都不装，
     * 与业务侧「配置缺席＝无腿」口径一致）＋ {@code tool.document.transcriber=mineru} 点名（R-3：
     * 同一时刻至多一条增强腿在装）。就绪度不入装配条件，留给 {@code isAvailable} 运行期判定
     */
    @Configuration
    @ConditionalOnProperty(name = "mineru.enabled", havingValue = "true", matchIfMissing = true)
    @EnableConfigurationProperties(MineruProperties.class)
    static class MineruLegConfiguration {

        @Bean
        @ConditionalOnProperty(name = "tool.document.transcriber", havingValue = MineruTranscribeLeg.LEG_NAME)
        @ConditionalOnMissingBean(TranscribeStep.class)
        public TranscribeStep mineruTranscribeLeg(MineruProperties properties) {
            MineruLocalClient local = StringUtils.hasText(properties.getLocal().getBaseUrl())
                    ? new MineruLocalClient(properties) : null;
            MineruCloudClient cloud = StringUtils.hasText(properties.getCloud().getToken())
                    ? new MineruCloudClient(properties) : null;
            return new MineruTranscribeLeg(properties, local, cloud);
        }

        /**
         * MinerU 异步转录腿（M17 棒 1，供 {@code engine/file-parse} 编排层消费）：
         * 同一道 {@code mineru.enabled} 闸（关掉＝两客户端腿与异步腿都不装）；不参与
         * {@code tool.document.transcriber} 同步选型——编排层按 {@code engine.parse.transcriber}
         * 自行选型，腿在不在由 classpath 决定，配置就绪度留给 {@code hasLeg()} 运行期判定
         */
        @Bean
        @ConditionalOnMissingBean(com.bidr.document.spi.AsyncTranscribeLeg.class)
        public com.bidr.document.spi.AsyncTranscribeLeg mineruAsyncTranscribeLeg(MineruProperties properties) {
            MineruLocalClient local = StringUtils.hasText(properties.getLocal().getBaseUrl())
                    ? new MineruLocalClient(properties) : null;
            MineruCloudClient cloud = StringUtils.hasText(properties.getCloud().getToken())
                    ? new MineruCloudClient(properties) : null;
            return new MineruAsyncTranscribeLeg(local, cloud);
        }
    }
}
