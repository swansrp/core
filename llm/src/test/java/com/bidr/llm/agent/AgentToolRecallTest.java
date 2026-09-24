package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Title: AgentToolRecallTest
 * Description: 句柄回捞工具三层硬闸单测（修正案 A7 🟡-1 测试同步）：闸门键经 resolveFloor 已回落
 * 保守默认，本类锁死构造器防御纵深——maxPerRun=0 即禁、maxChars=0 返回明确提示文本而非空串，
 * 0 只会更严不会失控（对齐 I6/三层硬闸第一道）
 *
 * @author sharp
 * @since 2026/9/25
 */
public class AgentToolRecallTest {

    /** 只读取数桩：recallToolResult 命中固定全文，supportsToolResultRecall=true */
    private static final AgentLoopListener SOURCE = new AgentLoopListener() {
        @Override
        public void log(String line) {
        }

        @Override
        public boolean shouldStop() {
            return false;
        }

        @Override
        public boolean supportsToolResultRecall() {
            return true;
        }

        @Override
        public String recallToolResult(String toolCallId) {
            return "call-1".equals(toolCallId) ? "原文全文内容较长用于验证截断与关闭" : null;
        }
    };

    /** maxPerRun=0 即禁：任何回捞一律回收口指令，绝不透原文 */
    @Test
    public void maxPerRun为0即禁止回捞() {
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 0, 20000, new ArrayList<>());
        String out = recall.recallToolResult("call-1");
        Assert.assertTrue("maxPerRun=0 应收预算用尽指令", out.contains("回捞预算已用尽"));
        Assert.assertFalse("禁止回捞不得透出原文", out.contains("原文全文"));
    }

    /** maxChars=0 即禁回填：命中原文也返回明确提示文本而非空串/截断空内容（防模型见空反复重试） */
    @Test
    public void maxChars为0返回明确提示而非空串() {
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 3, 0, new ArrayList<>());
        String out = recall.recallToolResult("call-1");
        Assert.assertFalse("不得返回空串", out.trim().isEmpty());
        Assert.assertTrue(out.contains("回捞已关闭"));
        Assert.assertFalse(out.contains("原文全文"));
    }

    /** 正常闸门：maxPerRun=3 时前 3 次透原文、第 4 次收口；maxChars 生效截断 */
    @Test
    public void 正常闸门内透原文超限收口且按maxChars截断() {
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 3, 8, new ArrayList<>());
        String first = recall.recallToolResult("call-1");
        Assert.assertTrue("应按 maxChars 截断", first.startsWith("原文全文内容"));
        Assert.assertTrue(first.contains("本次返回 8 字"));
        recall.recallToolResult("call-1");
        recall.recallToolResult("call-1");
        Assert.assertTrue("第 4 次收口", recall.recallToolResult("call-1").contains("回捞预算已用尽"));
    }

    /** A9 裁定 4：@Tool description 覆盖两个句柄来源（指针 tool_call_id= / 摘要行内「句柄=」），
     *  且保留「一次一个、禁止重复拉同一句柄」纪律——description 进 specs，此处锁死防回退 */
    @Test
    public void tool描述覆盖双句柄来源且纪律措辞在位() {
        dev.langchain4j.agent.tool.Tool tool = null;
        for (java.lang.reflect.Method m : AgentToolRecall.class.getMethods()) {
            tool = m.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (tool != null) {
                break;
            }
        }
        Assert.assertNotNull(tool);
        String desc = String.join("", tool.value());
        Assert.assertTrue("应保留指针句柄来源", desc.contains("tool_call_id="));
        Assert.assertTrue("应新增摘要行内句柄来源（A9）", desc.contains("句柄="));
        Assert.assertTrue("应保留探索摘要来源标识", desc.contains("【探索记录摘要】"));
        Assert.assertTrue("一次一个纪律措辞在位", desc.contains("一次只回捞一个句柄"));
        Assert.assertTrue("禁止重复措辞在位", desc.contains("禁止用它重复拉取同一句柄"));
    }
}
