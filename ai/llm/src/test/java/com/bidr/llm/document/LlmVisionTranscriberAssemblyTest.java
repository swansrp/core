package com.bidr.llm.document;

import com.bidr.document.DocumentAutoConfiguration;
import com.bidr.document.VisionTranscriber;
import com.bidr.document.builtin.NoopVisionTranscriber;
import com.bidr.llm.config.LlmDefaultAutoConfiguration;
import com.bidr.llm.config.LlmDocumentToolsAutoConfiguration;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

/**
 * Title: LlmVisionTranscriberAssemblyTest
 * Description: T1 装配用例（C′ 迁移 2026-11-07，报告 §6 T1）——钉「多模态转录口」的
 * 双态装配契约：
 * <ul>
 *   <li><b>llm 在场</b>（两自动配置同上下文）：{@link LlmVisionTranscriber} 真身先评估先注册
 *       （{@code LlmDefaultAutoConfiguration @AutoConfigureBefore(DocumentAutoConfiguration)}），
 *       document 的 {@link NoopVisionTranscriber} 占位见真身即让位——顺序反了占位会抢注、
 *       视觉腿静默变指引异常（本用例专防这种「静默瘸腿」）；read_file 工具
 *       {@link FileMarkdownTool} 默认 Bean 同上下文在装。</li>
 *   <li><b>llm 缺席</b>（仅 document 自动配置）：占位兜底＝被调即抛带操作指引的
 *       {@link IllegalStateException}（🔴 不许静默出空 md，指引文案历史原文）。</li>
 * </ul>
 * ApplicationContextRunner 的 {@link AutoConfigurations} 走生产同款
 * {@code AutoConfigurationSorter}，Before/After 顺序与真实部署一致。JUnit4（M-7）。
 *
 * @author Sharp
 * @since 2026/11/7
 */
public class LlmVisionTranscriberAssemblyTest {

    private final ApplicationContextRunner bothRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LlmDefaultAutoConfiguration.class, DocumentAutoConfiguration.class,
                    LlmDocumentToolsAutoConfiguration.class));

    private final ApplicationContextRunner documentOnlyRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentAutoConfiguration.class));

    /** llm 在场：真身先装、占位让位（@AutoConfigureBefore 顺序防线），read_file 工具齐装 */
    @Test
    public void llm在场_视觉腿装真身占位让位() {
        bothRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(VisionTranscriber.class);
            assertThat(context.getBean(VisionTranscriber.class)).isInstanceOf(LlmVisionTranscriber.class);
            assertThat(context).hasSingleBean(FileMarkdownTool.class);
        });
    }

    /** llm 缺席：占位兜底，被调即抛带操作指引的 IllegalStateException（历史文案原文） */
    @Test
    public void llm缺席_占位兜底被调即抛指引() {
        documentOnlyRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(VisionTranscriber.class);
            assertThat(context.getBean(VisionTranscriber.class)).isInstanceOf(NoopVisionTranscriber.class);
            VisionTranscriber transcriber = context.getBean(VisionTranscriber.class);
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> transcriber.transcribe(new byte[]{1}, "image/png", null));
            assertThat(ex.getMessage())
                    .startsWith("扫描件/图片解析需要多模态（视觉）模型")
                    .contains("VisionConfigProvider")
                    .contains("llm.vision.*");
        });
    }
}
