package com.bidr.gotenberg;

import com.bidr.document.spi.ConvertStep;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Title: GotenbergAutoConfiguration
 * Description: gotenberg convert 腿自动装配（starter 风格）。🔴 装配条件只有一条：
 * {@code tool.gotenberg.base-url} 非空（{@link GotenbergBaseUrlCondition}）——无 provider
 * 选型键（convert 槽单腿，一把地址就够）；留空＝不装配＝convert 槽缺席，office 原件直送、
 * 引用降级无页（RouteTrace 记 CONVERT_FALLBACK 形状）。业务要换别的 convert 腿＝
 * 自注册 {@link ConvertStep} Bean，本装配 {@code @ConditionalOnMissingBean} 让位。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@AutoConfiguration
@Conditional(GotenbergBaseUrlCondition.class)
@EnableConfigurationProperties(GotenbergProperties.class)
public class GotenbergAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ConvertStep.class)
    public ConvertStep gotenbergConvertStep(GotenbergProperties properties) {
        return new GotenbergConvertClient(properties);
    }
}
