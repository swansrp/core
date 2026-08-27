package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Title: AgentFailureBreakerTest
 * Description: 失败分类熔断器单元测试：失败判定（异常文案/error JSON 两种形态）、
 * 分类计数、阈值触发（一次性语义）、成功结果不计数、空规则不动作。
 * debug 背景：17min 死循环案例——同类失败（列不存在）反复出现无机制打断，
 * 收口前模型一直在同方向空转
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class AgentFailureBreakerTest {

    @Test
    public void countsToThresholdAndFiresOnce() {
        AgentFailureBreaker breaker = new AgentFailureBreaker(Arrays.asList(
                AgentFailureBreaker.Rule.of("列不存在", "unknown column|列不存在", 3, "先核实列名")));
        String err = "{\"error\":\"Unknown column 'amt2' in 'field list'\"}";
        Assert.assertNull("第 1 次不触发", breaker.onToolResult(err));
        Assert.assertNull("第 2 次不触发", breaker.onToolResult(err));
        String advice = breaker.onToolResult(err);
        Assert.assertNotNull("第 3 次达阈值触发", advice);
        Assert.assertTrue("触发文案含规则名与建议",
                advice.contains("列不存在") && advice.contains("先核实列名") && advice.contains("3 次"));
        Assert.assertNull("之后不再重复触发（一次性）", breaker.onToolResult(err));
    }

    @Test
    public void frameworkExceptionTextAlsoClassified() {
        AgentFailureBreaker breaker = new AgentFailureBreaker(Collections.singletonList(
                AgentFailureBreaker.Rule.of("表范围", "不在本 Agent 选表范围", 2, "换已选表")));
        // 引擎异常文案形态（executeTool 生成的【工具调用失败】前缀）
        String text = "【工具调用失败】run_sql 执行异常：IllegalArgumentException：表引用 db.x.t 不在本 Agent 选表范围";
        Assert.assertNull(breaker.onToolResult(text));
        Assert.assertNotNull(breaker.onToolResult(text));
    }

    @Test
    public void successResultsIgnored() {
        AgentFailureBreaker breaker = new AgentFailureBreaker(Collections.singletonList(
                AgentFailureBreaker.Rule.of("列不存在", "unknown column", 2, "先核实列名")));
        // 成功结果（含 rows 无 error）：不计数
        Assert.assertNull(breaker.onToolResult("{\"rows\":[{\"a\":1}]}"));
        Assert.assertNull(breaker.onToolResult("{\"rows\":[{\"unknown column\":0}]}"));
        // 仅失败文本计数
        Assert.assertNull(breaker.onToolResult("{\"error\":\"unknown column\"}"));
        Assert.assertNotNull(breaker.onToolResult("{\"error\":\"unknown column\"}"));
    }

    @Test
    public void unmatchedFailureAndEmptyRulesNoAction() {
        AgentFailureBreaker breaker = new AgentFailureBreaker(Collections.singletonList(
                AgentFailureBreaker.Rule.of("列不存在", "unknown column", 2, "先核实列名")));
        // 失败但不命中任何规则：无动作
        for (int i = 0; i < 5; i++) {
            Assert.assertNull(breaker.onToolResult("{\"error\":\"连接超时\"}"));
        }
        // 空规则集：恒无动作
        AgentFailureBreaker empty = new AgentFailureBreaker(null);
        Assert.assertNull(empty.onToolResult("{\"error\":\"unknown column\"}"));
        // 空文本防御
        Assert.assertNull(breaker.onToolResult(null));
        Assert.assertNull(breaker.onToolResult(""));
    }
}
