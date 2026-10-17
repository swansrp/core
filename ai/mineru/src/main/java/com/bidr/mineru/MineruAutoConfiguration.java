package com.bidr.mineru;

import com.bidr.llm.parse.DocumentTranscriber;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Title: MineruAutoConfiguration
 * Description: MinerU 通用客户端自动装配。本模块引在 classpath 上且 {@code mineru.enabled}
 * ≠false 即注册 {@link DocumentTranscriber} 门面腿——{@code FileMarkdownService} 的
 * ObjectProvider 咨询到它，pdf/图片就先走 MinerU（云优先本地兜底），失败回落既有本地路径。
 * <p>
 * 腿就绪度不入装配条件、留给 {@link MineruTranscriber#isAvailable(String)} 运行期判定
 * （两腿全空配＝不认领＝门面零变化），避免 NullBean 与"Bean 在但不生效"的歧义。
 * 业务要自定义转录腿（更宽的类型面/别的引擎）＝自己注册 {@link DocumentTranscriber} Bean，
 * 本装配 {@code @ConditionalOnMissingBean} 让位。
 *
 * @author Sharp
 * @since 2026/10/17
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mineru.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(MineruProperties.class)
public class MineruAutoConfiguration {

    /**
     * MinerU 门面腿：Bean 常驻（本模块在 classpath 且未显式关闭即注册），
     * 两腿按配置就绪度构造（local.base-url 空→无本地腿；cloud.token 空→无云腿）
     */
    @Bean
    @ConditionalOnMissingBean(DocumentTranscriber.class)
    public DocumentTranscriber mineruTranscriber(MineruProperties properties) {
        MineruLocalClient local = StringUtils.hasText(properties.getLocal().getBaseUrl())
                ? new MineruLocalClient(properties) : null;
        MineruCloudClient cloud = StringUtils.hasText(properties.getCloud().getToken())
                ? new MineruCloudClient(properties) : null;
        return new MineruTranscriber(properties, local, cloud);
    }
}
