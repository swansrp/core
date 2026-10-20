package com.bidr.document;

import com.bidr.document.spi.IntakeStep;
import com.bidr.document.spi.ArtifactStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A12 机器腿（M16 修复轮）：
 * <ul>
 *   <li>{@link VisionEndpoint#isUsable()} 放宽到「地址＋模型名」——内网/自建 OpenAI 兼容端点
 *       常不收密钥，要求密钥等于把它们判成「没配视觉腿」；</li>
 *   <li>三个单槽口（VisionConfigProvider／ArtifactStep／IntakeStep）改 {@code getIfUnique}：
 *       同槽多候选时<b>取不到就按缺省档走</b>，不许炸容器（{@code getIfAvailable} 遇多候选抛
 *       NoUniqueBeanDefinitionException）。</li>
 * </ul>
 *
 * @author sharp
 */
class VisionEndpointUsabilityTest {

    @Test
    @DisplayName("A12：空 apiKey 端点可解析（内网/自建端点不收密钥）")
    void emptyApiKeyStillUsable() {
        VisionEndpoint blankKey = VisionEndpoint.builder()
                .baseUrl("http://10.0.0.8:8000/v1").apiKey("").modelName("qwen-vl-max").build();
        assertTrue(blankKey.isUsable());

        VisionEndpoint nullKey = VisionEndpoint.builder()
                .baseUrl("http://localhost:11434/v1").modelName("llava").build();
        assertTrue(nullKey.isUsable());

        assertFalse(VisionEndpoint.builder().apiKey("sk-x").modelName("qwen-vl-max").build().isUsable(),
                "缺地址＝不可用");
        assertFalse(VisionEndpoint.builder().baseUrl("http://x/v1").apiKey("sk-x").build().isUsable(),
                "缺模型名＝不可用");
    }

    /** 同槽两候选：容器照常启动，槽口取不到 → 按缺省档（视觉配置判「未提供」） */
    @Configuration
    static class DuplicateSlotConfig {

        @Bean
        VisionConfigProvider visionA() {
            return () -> VisionEndpoint.builder().baseUrl("http://a/v1").modelName("m-a").build();
        }

        @Bean
        VisionConfigProvider visionB() {
            return () -> VisionEndpoint.builder().baseUrl("http://b/v1").modelName("m-b").build();
        }

        @Bean
        IntakeStep intakeA() {
            return in -> new byte[0];
        }

        @Bean
        IntakeStep intakeB() {
            return in -> new byte[0];
        }

        @Bean
        ArtifactStep artifactA() {
            return (markdown, trace) -> new DocumentArtifact(markdown, trace);
        }

        @Bean
        ArtifactStep artifactB() {
            return (markdown, trace) -> new DocumentArtifact(markdown, trace);
        }
    }

    @Test
    @DisplayName("A12：三个单槽口各两候选 → 容器不炸，门面按缺省档装配")
    void duplicateSlotBeansDoNotBreakContainer() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DocumentAutoConfiguration.class))
                .withUserConfiguration(DuplicateSlotConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DocumentProcessor.class);
                    assertThat(context).hasNotFailed();
                    DocumentProcessor processor = context.getBean(DocumentProcessor.class);
                    // 视觉配置取不到（多候选）→ 扫描页抛带指引的异常，而不是容器启动就炸
                    IllegalStateException e = org.junit.jupiter.api.Assertions.assertThrows(
                            IllegalStateException.class,
                            () -> processor.process(emptyPdf(), "扫描件.pdf", null));
                    assertThat(e.getMessage()).contains("多模态");
                });
    }

    /** 空内容页的最小 PDF（同 RouteTraceTwoLaneTest 的扫描页构造） */
    private static byte[] emptyPdf() throws java.io.IOException {
        org.apache.pdfbox.pdmodel.PDDocument pdf = new org.apache.pdfbox.pdmodel.PDDocument();
        pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        pdf.save(bos);
        return bos.toByteArray();
    }
}
