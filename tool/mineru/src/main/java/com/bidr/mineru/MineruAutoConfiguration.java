package com.bidr.mineru;

import com.bidr.document.spi.TranscribeStep;
import com.bidr.llm.parse.DocumentTranscriber;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Title: MineruAutoConfiguration
 * Description: MinerU 通用客户端自动装配。本模块引在 classpath 上、且 yml 选型键
 * {@code tool.document.transcriber=mineru}（R-3：增强腿以 @ConditionalOnProperty 自 gate，
 * 同一时刻至多一条在装）时注册两件：
 * <ul>
 *   <li>{@link TranscribeStep}（tool/document 新口）：{@code DocumentProcessor} 门面经它走增强转写，
 *       失败回落内置腿（门面负责回落、实现方别吞错）；</li>
 *   <li>{@link DocumentTranscriber}（llm 老口，兼容既有消费方）：{@code FileMarkdownService}
 *       构造注入档——老口行为原样保留。</li>
 * </ul>
 * 键空/配了别的值＝两件都不装（§3.1 三态之一：没配→走内置，不抛）；
 * 键指向 mineru 但本包没引＝装配期 WARN「选型指向不存在的腿」（由 tool/document 的
 * {@code TranscribeLegSelector} 发出）后走内置。
 * <p>
 * 腿就绪度不入装配条件、留给 {@link MineruTranscriber#isAvailable(String)} 运行期判定
 * （两腿全空配＝不认领＝门面零变化），避免 NullBean 与「Bean 在但不生效」的歧义。
 * 业务要自定义转录腿（更宽的类型面/别的引擎）＝自己注册同口 Bean，
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
     * tool/document 新口腿（M16 棒 3）：薄委托 {@link MineruTranscriber}。
     * 两腿按配置就绪度构造（local.base-url 空→无本地腿；cloud.token 空→无云腿）。
     */
    @Bean
    @ConditionalOnProperty(name = "tool.document.transcriber", havingValue = MineruTranscribeStep.LEG_NAME)
    @ConditionalOnMissingBean(TranscribeStep.class)
    public TranscribeStep mineruTranscribeStep(MineruProperties properties) {
        return new MineruTranscribeStep(newMineruTranscriber(properties));
    }

    /**
     * llm 老口腿（兼容既有 FileMarkdownService 构造注入消费方）：Bean 与新口同条件
     * （选型键指向 mineru 才亮），两腿全空配时 isAvailable 判不认领＝零变化。
     */
    @Bean
    @ConditionalOnProperty(name = "tool.document.transcriber", havingValue = MineruTranscribeStep.LEG_NAME)
    @ConditionalOnMissingBean(DocumentTranscriber.class)
    public DocumentTranscriber mineruTranscriber(MineruProperties properties) {
        return newMineruTranscriber(properties);
    }

    /** 两腿按配置就绪度构造：local.base-url 空＝无本地腿（null）；cloud.token 空＝无云腿（null） */
    private MineruTranscriber newMineruTranscriber(MineruProperties properties) {
        MineruLocalClient local = StringUtils.hasText(properties.getLocal().getBaseUrl())
                ? new MineruLocalClient(properties) : null;
        MineruCloudClient cloud = StringUtils.hasText(properties.getCloud().getToken())
                ? new MineruCloudClient(properties) : null;
        return new MineruTranscriber(properties, local, cloud);
    }
}
