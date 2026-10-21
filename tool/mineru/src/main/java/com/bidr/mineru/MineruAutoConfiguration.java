package com.bidr.mineru;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Title: MineruAutoConfiguration
 * Description: MinerU 通用客户端自动装配——本模块只提供客户端＋配置件，
 * <b>不注册任何转录腿</b>：MinerU 作为文档加工增强腿挂在
 * {@code tool/document}（{@code com.bidr.document.builtin.MineruTranscribeLeg}＋
 * 该包内的 {@code MineruLegConfiguration}），依赖方向是 document → mineru，
 * 本模块不回依 document/llm（无环）。
 * <p>
 * {@code mineru.enabled=false} 时本装配整体不生效＝{@link MineruProperties} Bean 缺失，
 * 消费方按 {@code ObjectProvider} 缺件语义处理（不抛）；document 侧的腿装配同样吃这个开关。
 *
 * @author Sharp
 * @since 2026/10/17
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mineru.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MineruProperties.class)
public class MineruAutoConfiguration {
}
