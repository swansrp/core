package com.bidr.document;

import com.bidr.document.spi.ArtifactStep;
import com.bidr.document.spi.ConvertStep;
import com.bidr.document.spi.EnrichStep;
import com.bidr.document.spi.IntakeStep;
import com.bidr.document.spi.TranscribeStep;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.stream.Collectors;

/**
 * Title: DocumentAutoConfiguration
 * Description: 文件底座默认装配（starter 风格，业务可覆盖）：
 * <ul>
 *   <li>腿收集＝List 收口（{@code ObjectProvider.orderedStream}）——classpath 上多条同槽腿
 *       <b>不会</b>炸容器（NoUniqueBeanDefinitionException 的修法面）；</li>
 *   <li>transcribe 选型＝{@link TranscribeLegSelector}（{@code tool.document.transcriber} 一把键，
 *       三态语义：没配→内置；配了包没引→WARN 走内置；配了且在→增强腿）；</li>
 *   <li>{@code DocumentProcessor} Bean 缺席时（业务自建）以 {@code @ConditionalOnMissingBean} 让位。</li>
 * </ul>
 * R-9：只读 Spring Environment，零 sys_config、零 platform 依赖。
 *
 * @author Sharp
 * @since 2026/10/20
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
}
