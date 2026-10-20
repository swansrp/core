package com.bidr.llm.config;

import com.bidr.llm.parse.DocumentTranscriber;
import com.bidr.llm.parse.FileMarkdownService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Title: LlmTranscriberListInjectionTest
 * Description: M-8 修法的机器判据（M16 棒 3 判据①的 llm 侧）：两条 {@link DocumentTranscriber}
 * 老口腿同上 classpath 时容器启动成功——历史实现 {@code transcriber.getIfAvailable()} 单 bean
 * 任取会抛 NoUniqueBeanDefinitionException；改 List 收口（orderedStream）后按装配序取第一条，
 * 装配期选型正源＝{@code tool.document.transcriber}（各腿 @ConditionalOnProperty 自 gate，R-3）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class LlmTranscriberListInjectionTest {

    private AnnotationConfigApplicationContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /** 桩腿：认领 .png，返回腿名标记 */
    private static class MarkerTranscriber implements DocumentTranscriber {

        final String marker;

        MarkerTranscriber(String marker) {
            this.marker = marker;
        }

        @Override
        public boolean isAvailable(String filename) {
            return filename != null && filename.toLowerCase().endsWith(".png");
        }

        @Override
        public String transcribe(byte[] content, String filename) {
            return marker;
        }
    }

    @Test
    public void 双腿共装_容器启动成功且FileMarkdownService可取() throws IOException {
        context = new AnnotationConfigApplicationContext();
        // 两条老口腿同注册（历史实现此处创建 fileMarkdownService 时即 NoUniqueBeanDefinitionException）
        context.registerBean("legA", DocumentTranscriber.class, () -> new MarkerTranscriber("A"));
        context.registerBean("legB", DocumentTranscriber.class, () -> new MarkerTranscriber("B"));
        context.register(LlmDefaultAutoConfiguration.class);
        context.refresh();

        Assert.assertTrue("双腿共装必须能启动（List 收口）", context.containsBean("fileMarkdownService"));
        Assert.assertEquals(2, context.getBeansOfType(DocumentTranscriber.class).size());

        FileMarkdownService service = context.getBean(FileMarkdownService.class);
        String markdown = service.toMarkdown(
                new ByteArrayInputStream("fake-image".getBytes(StandardCharsets.UTF_8)), "x.png");
        Assert.assertEquals("按装配序取第一条腿（A 先注册先被咨询）", "A", markdown);
    }

    @Test
    public void 零腿共装_行为不变() {
        context = new AnnotationConfigApplicationContext();
        context.register(LlmDefaultAutoConfiguration.class);
        context.refresh();

        Assert.assertTrue(context.containsBean("fileMarkdownService"));
        Assert.assertTrue("视觉配置口适配器随装配", context.containsBean("llmVisionConfigProvider"));
    }
}
