package com.bidr.parse;

import com.bidr.blob.BlobStore;
import com.bidr.document.DocumentProcessor;
import com.bidr.document.spi.AsyncTranscribeLeg;
import com.bidr.document.spi.ConvertStep;
import com.bidr.task.AsyncTaskObserver;
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
 * Title: ParseEngineAutoConfiguration
 * Description: 解析编排引擎默认装配（starter 风格，业务可覆盖）：
 * <ul>
 *   <li>{@link ParseEngineFlow} 缺席时才装；带 {@code engine.parse.enabled=false} 总闸
 *       （业务自管受理面时整体关闭，零装配）；</li>
 *   <li>产物落盘槽 {@link ParseArtifactWriter}：业务注册了自己的 Bean 即整体接管（R-5 自证腿）；
 *       不注册＝内置默认（图片入库＋占位改写＋偏移重定位）；</li>
 *   <li>增强腿 {@link ParseEnrichLeg}：List 收口，多腿并存，业务注册即挂链，不注册＝空转；</li>
 *   <li>异步转录腿：经 tool/document 的 {@link AsyncTranscribeLeg} 口按 classpath 收口，
 *       选型键 {@code engine.parse.transcriber}（三态语义沿 {@code TranscribeLegSelector}）。</li>
 * </ul>
 * 配置面只读 Spring Environment（{@code engine.parse.*}），零 sys_config（R-9）；
 * 引擎不直接依赖 tool/mineru / tool/gotenberg——腿装不装由 classpath 决定，引擎零感知。
 *
 * @author Sharp
 * @since 2026-10-21
 */
@AutoConfiguration
@EnableConfigurationProperties(ParseEngineProperties.class)
public class ParseEngineAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "engine.parse.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ParseEngineFlow.class)
    public ParseEngineFlow parseEngineFlow(BlobStore storage,
            ObjectProvider<DocumentProcessor> processor,
            ObjectProvider<ConvertStep> convertStep,
            ObjectProvider<AsyncTranscribeLeg> transcribeLegs,
            ParseEngineProperties properties,
            ObjectProvider<ParseArtifactWriter> artifactWriter,
            TextPassthroughService passthrough,
            PageImageRenderer pageImageRenderer,
            org.springframework.beans.factory.ObjectProvider<ParseEnrichLeg> enrichLegs) {
        return new ParseEngineFlow(storage, processor, convertStep,
                transcribeLegs.orderedStream().collect(Collectors.toList()),
                properties.getTranscriber(), properties.getMineruMaxWaitMs(),
                artifactWriter.getIfUnique(), passthrough, pageImageRenderer,
                enrichLegs.orderedStream().collect(Collectors.toList()));
    }

    /** 产物落盘内置默认（业务注册自己的 ParseArtifactWriter 即让位） */
    @Bean
    @ConditionalOnMissingBean(ParseArtifactWriter.class)
    public ParseArtifactWriter defaultParseArtifactWriter(BlobStore storage, ParseEngineProperties properties) {
        return new DefaultParseArtifactWriter(storage, requireScheme(properties));
    }

    /** 直通腿（D-5 内置化；业务自建以 @ConditionalOnMissingBean 让位——本件引擎内装、非 Bean 冲突面） */
    @Bean
    @ConditionalOnMissingBean(TextPassthroughService.class)
    public TextPassthroughService textPassthroughService(BlobStore storage, ParseEngineProperties properties) {
        return new TextPassthroughService(storage, properties.getPassthroughRemoteImageHosts(),
                requireScheme(properties));
    }

    /**
     * 占位 scheme 必填闸：协议值业务所有（core 不内置任何项目的字面量），两内置腿写占位
     * 离了它产不出合法正文——缺配＝装配期快败，指路两条给法（配键 / 自注册 Bean 注入常量）。
     */
    private static String requireScheme(ParseEngineProperties properties) {
        if (!StringUtils.hasText(properties.getImagePlaceholderScheme())) {
            throw new IllegalStateException(
                    "engine.parse.image-placeholder-scheme 未配置——正文占位协议值属业务所有，core 不内置 scheme；"
                            + "配该键，或自注册 TextPassthroughService/ParseArtifactWriter Bean 在构造时注入自家常量");
        }
        return properties.getImagePlaceholderScheme();
    }

    /** 页图渲染器（D-7 内置；D-11 裁剪请求时流程跳过调用，Bean 常在） */
    @Bean
    @ConditionalOnMissingBean(PageImageRenderer.class)
    public PageImageRenderer pageImageRenderer(BlobStore storage) {
        return new PageImageRenderer(storage);
    }

    /**
     * 排空观察者（业务可写自己的 {@link AsyncTaskObserver} 接终态——引擎只保证 SUCCESS/DEAD
     * 必发，观察者异常不改终态）：本 Bean 是个空实现占位，保证观察者链不为空也业务零成本
     */
    @Configuration
    @ConditionalOnProperty(name = "engine.parse.enabled", havingValue = "true", matchIfMissing = true)
    static class ObserverConfiguration {
        // 终态观察者不设默认实现：SUCCESS/DEAD 两档必发是底盘契约（AsyncTaskService 逐件 try-catch），
        // 业务按需注册自己的 AsyncTaskObserver（如回写版本行/失败态），引擎不自作主张写业务行
    }
}
