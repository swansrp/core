package com.bidr.llm.agent;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: ContextTokenEstimatorTest
 * Description: token 估算器单测（计划 §6.1）：锁死 I8 方向性（宁高不低）、混合文本口径、
 * 固定结构开销、各消息类型计入、单调性（trimToolMemory 反向累加算法的可用性前提）
 *
 * @author sharp
 * @since 2026/9/25
 */
public class ContextTokenEstimatorTest {

    /** I8：中文按 1 字 = 1 token（真实 qwen 约 0.6~1.0 字/token，取 1.0 恒不低估） */
    @Test
    public void 纯中文估算不低于字符数() {
        String cn = "上下文预算治理机制测试样本";
        Assert.assertTrue(ContextTokenEstimator.estimateText(cn) >= cn.length());
    }

    /** I8：英文 0.3/字符 ≈ 3.3 字符/token，比真实 4 字符/token 保守约 33%，即估算高于真实四分之一 */
    @Test
    public void 纯英文估算高于真实四分之一() {
        String en = "the quick brown fox jumps over the lazy dog repeatedly";
        int est = ContextTokenEstimator.estimateText(en);
        Assert.assertTrue(est > en.length() / 4);
    }

    /** 混排天然取「CJK×1.0 + 其他×0.3」两者之和，无需分词 */
    @Test
    public void 混排取两者之和() {
        String mixed = "中文abc";
        // 2 CJK × 1.0 + 3 other × 0.3 = 2.9 → ceil = 3
        Assert.assertEquals(3, ContextTokenEstimator.estimateText(mixed));
    }

    @Test
    public void null与空串返回0不抛() {
        Assert.assertEquals(0, ContextTokenEstimator.estimateText(null));
        Assert.assertEquals(0, ContextTokenEstimator.estimateText(""));
        Assert.assertEquals(0, ContextTokenEstimator.estimateMessages(null));
        Assert.assertEquals(0, ContextTokenEstimator.estimateMessages(Collections.<ChatMessage>emptyList()));
        Assert.assertEquals(0, ContextTokenEstimator.estimateSpecs(null));
        Assert.assertEquals(0, ContextTokenEstimator.estimateSpecs(Collections.<ToolSpecification>emptyList()));
        Assert.assertEquals(0, ContextTokenEstimator.countChars(null));
    }

    /** 每条消息 +4 固定结构开销（role/分隔/tool-call 标记） */
    @Test
    public void 每条消息计入固定开销() {
        List<ChatMessage> one = Collections.<ChatMessage>singletonList(UserMessage.from("ab"));
        int estOne = ContextTokenEstimator.estimateMessages(one);
        List<ChatMessage> two = Arrays.<ChatMessage>asList(UserMessage.from("ab"), UserMessage.from("ab"));
        int estTwo = ContextTokenEstimator.estimateMessages(two);
        Assert.assertEquals("多一条消息恰好多一条固定开销", estOne + ContextTokenEstimator.estimateMessage(UserMessage.from("ab")), estTwo);
        Assert.assertEquals("单条消息估算=正文+固定开销",
                ContextTokenEstimator.estimateText("ab") + ContextTokenEstimator.TOKENS_PER_MESSAGE,
                ContextTokenEstimator.estimateMessage(UserMessage.from("ab")));
        Assert.assertEquals("null 消息也按固定开销宁高不低",
                ContextTokenEstimator.TOKENS_PER_MESSAGE, ContextTokenEstimator.estimateMessage(null));
    }

    /** AiMessage 除正文外，每个工具调用的 arguments JSON 必须计入（否则预算严重低估） */
    @Test
    public void AiMessage的工具参数被计入() {
        String args = "{\"topic\":\"AAAAAAAAAA\"}";
        AiMessage withCall = AiMessage.from(ToolExecutionRequest.builder()
                .id("call-1").name("bigReport").arguments(args).build());
        // 调用型 AiMessage 正文为空：估算 = 固定开销 + arguments JSON（漏算参数则只剩 4）
        Assert.assertEquals(ContextTokenEstimator.TOKENS_PER_MESSAGE + ContextTokenEstimator.estimateText(args),
                ContextTokenEstimator.estimateMessage(withCall));
        Assert.assertTrue("参数 JSON 计入后应显著大于只算固定开销",
                ContextTokenEstimator.estimateMessage(withCall) > ContextTokenEstimator.TOKENS_PER_MESSAGE);
        // 清单级同理
        int listEst = ContextTokenEstimator.estimateMessages(Collections.<ChatMessage>singletonList(withCall));
        Assert.assertEquals(ContextTokenEstimator.estimateMessage(withCall), listEst);
    }

    @Test
    public void 工具结果与System与User消息均计入正文() {
        String cn = "大段中文工具结果正文";
        int est = ContextTokenEstimator.estimateMessage(ToolExecutionResultMessage.from("call-1", "bigReport", cn));
        Assert.assertTrue(est >= cn.length());
        Assert.assertTrue(ContextTokenEstimator.estimateMessage(SystemMessage.from(cn)) >= cn.length());
        Assert.assertTrue(ContextTokenEstimator.estimateMessage(UserMessage.from(cn)) >= cn.length());
    }

    /** specs 估算含 description 与参数 schema，且 ×1.2 安全系数（每轮全量重发） */
    @Test
    public void estimateSpecs含描述与参数schema且大于0() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("bigReport")
                .description("生成一份约三万字的中文解析报告，主题由 topic 参数指定")
                .build();
        int est = ContextTokenEstimator.estimateSpecs(Collections.singletonList(spec));
        Assert.assertTrue("含中文描述的 spec 估算必须 > 0", est > 0);
        // 描述为空 → 估算显著下降（证明 description 被计入）
        ToolSpecification bare = ToolSpecification.builder().name("bigReport").build();
        Assert.assertTrue(est > ContextTokenEstimator.estimateSpecs(Collections.singletonList(bare)));
    }

    /** 全角标点（\uFF00-\uFFEF、\u3000-\u303F）与日文假名（\u3040-\u30FF）走 CJK 权重 1.0 */
    @Test
    public void 全角标点与日文假名走CJK权重() {
        Assert.assertEquals(1, ContextTokenEstimator.estimateText("，"));
        Assert.assertEquals(1, ContextTokenEstimator.estimateText("あ"));
        Assert.assertEquals(1, ContextTokenEstimator.estimateText("ア"));
        Assert.assertEquals(4, ContextTokenEstimator.estimateText("中文，あ"));
    }

    /** emoji（代理对）只抛异常不吞字符：每 char 计一个，宁高不低 */
    @Test
    public void emoji与代理对不抛异常() {
        int est = ContextTokenEstimator.estimateText("结果😀OK");
        Assert.assertTrue(est > 0);
        // 消息级同样不抛
        Assert.assertTrue(ContextTokenEstimator.estimateMessages(
                Collections.<ChatMessage>singletonList(UserMessage.from("结果😀OK"))) > 0);
    }

    /** 单调性：文本加长估算不减（反向累加算法可用的前提） */
    @Test
    public void 单调性() {
        String base = "中文abc";
        int prev = ContextTokenEstimator.estimateText(base);
        for (int i = 0; i < 200; i++) {
            base = base + "补充内容xyz";
            int now = ContextTokenEstimator.estimateText(base);
            Assert.assertTrue("估算必须单调不减", now >= prev);
            prev = now;
        }
        // 消息清单级：尾部追加消息不减总量
        List<ChatMessage> msgs = new java.util.ArrayList<>();
        msgs.add(UserMessage.from("第一条"));
        int mPrev = ContextTokenEstimator.estimateMessages(msgs);
        msgs.add(AiMessage.from("第二条"));
        Assert.assertTrue(ContextTokenEstimator.estimateMessages(msgs) > mPrev);
    }
}
