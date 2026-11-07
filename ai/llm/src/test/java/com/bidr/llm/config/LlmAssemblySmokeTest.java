package com.bidr.llm.config;

import com.bidr.llm.model.LiveModelFactory;
import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: LlmAssemblySmokeTest
 * Description: T0 装配冒烟骨架（lc4j 0.33→1.23 升级防回归锚，报告 §6 T0）——
 * 最小上下文跑一遍 {@link LlmDefaultAutoConfiguration}，钉住三个核心 Bean 必须在装：
 * {@link ChatModel} / {@link StreamingChatModel} / {@link LiveModelFactory}。
 * <p>
 * 本测试专治 {@code @ConditionalOnClass} 字符串写错→静默不装配：条件注解里类名是字符串字面量，
 * 编译期不校验，写错一个字母整段自动配置静默失效、下游全部拿不到模型 Bean——本测试让这种
 * 「静默瘸腿」当场红掉，而不是等业务链路第一次调用时才炸。
 * </p>
 * <p>
 * 最小装配条件（读自 {@link LlmDefaultAutoConfiguration}）：默认模型 Bean 挂
 * {@code @ConditionalOnBean(ModelConfigProvider.class)}——须先放一个桩 Provider 进上下文
 * （桩值合法即可，装配链不发起真实调用）；{@code llm.*} yaml 全走缺省值即可装配。
 * <p>
 * <b>升级翻转点（棒3）</b>：断言类型随 lc4j 1.23 一并翻转为
 * {@code ChatModel} / {@code StreamingChatModel} / {@code LiveModelFactory}（工厂类不变），
 * 断言结构不动。JUnit4（M-7）。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/7
 */
public class LlmAssemblySmokeTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LlmDefaultAutoConfiguration.class))
            .withBean("smokeStubModelConfigProvider", ModelConfigProvider.class,
                    LlmAssemblySmokeTest::stubProvider);

    /** 三核心 Bean 齐装：同步模型 / 流式模型 / 流式进度模型工厂，缺一即装配链断裂 */
    @Test
    public void threeCoreBeansAssembled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(StreamingChatModel.class);
            assertThat(context).hasSingleBean(LiveModelFactory.class);
        });
    }

    /** 桩配置：0.33 装配链的必需依赖（@ConditionalOnBean(ModelConfigProvider)），值合法即可、不被真实调用 */
    private static ModelConfigProvider stubProvider() {
        return new ModelConfigProvider() {
            @Override
            public String getBaseUrl(String purposeType) {
                return "http://127.0.0.1:9/v1";
            }

            @Override
            public String getApiKey(String purposeType, Long userId) {
                return "smoke-stub-key";
            }

            @Override
            public String getModelName(String purposeType) {
                return "smoke-stub-model";
            }

            @Override
            public long getTimeoutSeconds(String purposeType) {
                return 30;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return purposeType + "|smoke-stub";
            }
        };
    }
}
