package com.bidr.document;

import com.bidr.document.spi.TranscribeStep;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 双腿共装正向判据（立项 :205 判据①）：两条 transcribe 腿同上 classpath＋yml 选一条，
 * 容器启动成功、只有被选的那条参与装配（M-8 的机器判据——
 * NoUniqueBeanDefinitionException 不再可能：腿收集走 List 收口＋选型复核）。
 * 腿形＝R-3 纪律的样板：每条增强腿 {@code @ConditionalOnProperty(havingValue=…)} 自 gate。
 *
 * @author Sharp
 * @since 2026/10/20
 */
class DocumentAssemblySelectionTest {

    private static class GatedLeg implements TranscribeStep {

        final String name;

        GatedLeg(String name) {
            this.name = name;
        }

        @Override
        public String legName() {
            return name;
        }

        @Override
        public boolean isAvailable(String filename) {
            return true;
        }

        @Override
        public String transcribe(byte[] content, String filename) throws IOException {
            return "FROM_" + name;
        }
    }

    @Configuration
    static class LegAConfig {

        @Bean
        @ConditionalOnProperty(name = "tool.document.transcriber", havingValue = "leg-a")
        TranscribeStep legA() {
            return new GatedLeg("leg-a");
        }
    }

    @Configuration
    static class LegBConfig {

        @Bean
        @ConditionalOnProperty(name = "tool.document.transcriber", havingValue = "leg-b")
        TranscribeStep legB() {
            return new GatedLeg("leg-b");
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentAutoConfiguration.class))
            .withUserConfiguration(LegAConfig.class, LegBConfig.class);

    @Test
    void yml选legA时只有legA被装配() {
        runner.withPropertyValues("tool.document.transcriber=leg-a")
                .run(context -> {
                    assertThat(context).hasSingleBean(DocumentProcessor.class);
                    assertThat(context).hasBean("legA");
                    assertThat(context).doesNotHaveBean("legB");
                    assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep().legName())
                            .isEqualTo("leg-a");
                });
    }

    @Test
    void yml选legB时只有legB被装配() {
        runner.withPropertyValues("tool.document.transcriber=leg-b")
                .run(context -> {
                    assertThat(context).hasBean("legB");
                    assertThat(context).doesNotHaveBean("legA");
                    assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep().legName())
                            .isEqualTo("leg-b");
                });
    }

    @Test
    void 键空时增强腿都不装_门面走内置() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DocumentProcessor.class);
            assertThat(context).doesNotHaveBean("legA");
            assertThat(context).doesNotHaveBean("legB");
            assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep()).isNull();
        });
    }

    @Configuration
    static class UngatedPairConfig {

        /** 用户自注册、未挂条件注解的两条腿：List 收口必须容下（不炸容器），选型键仍按名挑 */
        @Bean
        TranscribeStep freeLegA() {
            return new GatedLeg("leg-a");
        }

        @Bean
        TranscribeStep freeLegB() {
            return new GatedLeg("leg-b");
        }
    }

    @Test
    void 两条未gate的腿共装也不炸_按选型键挑一条() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DocumentAutoConfiguration.class))
                .withUserConfiguration(UngatedPairConfig.class)
                .withPropertyValues("tool.document.transcriber=leg-b")
                .run(context -> {
                    assertThat(context).hasSingleBean(DocumentProcessor.class);
                    assertThat(context).hasBean("freeLegA");
                    assertThat(context).hasBean("freeLegB");
                    assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep().legName())
                            .isEqualTo("leg-b");
                });
    }

    @Test
    void 配了不存在的腿名_容器照常启动并走内置() {
        runner.withPropertyValues("tool.document.transcriber=foxit")
                .run(context -> {
                    assertThat(context).hasSingleBean(DocumentProcessor.class);
                    assertThat(context.getBean(DocumentProcessor.class).getTranscribeStep()).isNull();
                });
    }
}
