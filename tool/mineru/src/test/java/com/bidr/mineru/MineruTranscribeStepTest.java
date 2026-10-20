package com.bidr.mineru;

import com.bidr.document.spi.TranscribeStep;
import com.bidr.llm.parse.DocumentTranscriber;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: MineruTranscribeStepTest
 * Description: Transcribe 适配腿判据（M16 棒 3）：腿名＝选型键取值；认领面沿用原实现
 * （pdf/图片认领、office 不认领）；<b>失败如实抛</b>（适配层不许吞错——契约「门面负责回落」
 * 的实现方半边）；装配 gating＝选型键指向 mineru 才亮（R-3：@ConditionalOnProperty 自 gate）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class MineruTranscribeStepTest {

    private AnnotationConfigApplicationContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void 腿名与认领面沿用原实现() {
        MineruProperties properties = new MineruProperties();
        properties.getLocal().setBaseUrl("http://127.0.0.1:9"); // isAvailable 不发请求，只看就绪度
        MineruTranscribeStep step = new MineruTranscribeStep(
                new MineruTranscriber(properties, new MineruLocalClient(properties), null));

        Assert.assertEquals("腿名＝yml 选型键 tool.document.transcriber 的取值", "mineru", step.legName());
        Assert.assertTrue("pdf 认领", step.isAvailable("报告.pdf"));
        Assert.assertTrue("图片认领", step.isAvailable("图.png"));
        Assert.assertFalse("office 不认领（走门面内置 POI 路径）", step.isAvailable("台账.xlsx"));
        Assert.assertFalse("未配置两腿＝整体不认领",
                new MineruTranscribeStep(new MineruTranscriber(new MineruProperties(), null, null))
                        .isAvailable("报告.pdf"));
    }

    @Test
    public void 失败如实上抛不吞错() throws IOException {
        // 两腿均未配置：transcribe 必须把 IOException 原样抛出（回落决策归门面，适配层别吞）
        MineruTranscribeStep step = new MineruTranscribeStep(
                new MineruTranscriber(new MineruProperties(), null, null));
        try {
            step.transcribe("bytes".getBytes("UTF-8"), "a.pdf");
            Assert.fail("适配层不许吞错");
        } catch (IOException expected) {
            Assert.assertTrue(expected.getMessage().contains("两腿均未配置"));
        }
    }

    @Test
    public void 选型键指向mineru才装配() {
        context = contextWith("tool.document.transcriber", "mineru");
        Assert.assertTrue("键=mineru：TranscribeStep 腿在装",
                context.getBeansOfType(TranscribeStep.class).containsKey("mineruTranscribeStep"));
        Assert.assertTrue("键=mineru：老口 DocumentTranscriber 兼容腿同条件在装",
                context.getBeansOfType(DocumentTranscriber.class).containsKey("mineruTranscriber"));
    }

    @Test
    public void 键空则两件都不装() {
        context = contextWith("tool.document.transcriber", "");
        Assert.assertTrue("键空＝不装（三态之一：没配→走内置，不抛）",
                context.getBeansOfType(TranscribeStep.class).isEmpty());
        Assert.assertTrue(context.getBeansOfType(DocumentTranscriber.class).isEmpty());
    }

    @Test
    public void 键指向别的腿也不装() {
        context = contextWith("tool.document.transcriber", "other");
        Assert.assertTrue("键指向别的腿＝本模块让位",
                context.getBeansOfType(TranscribeStep.class).isEmpty());
    }

    private AnnotationConfigApplicationContext contextWith(String key, String value) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        Map<String, Object> props = new HashMap<>();
        props.put(key, value);
        props.put("mineru.local.base-url", "http://127.0.0.1:9");
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", props));
        ctx.register(MineruAutoConfiguration.class);
        ctx.refresh();
        return ctx;
    }
}
