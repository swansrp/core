package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;


/**
 * Title: AgentToolRecallTest
 * Description: 句柄回捞工具三层硬闸单测（修正案 A7 🟡-1 测试同步）：闸门键经 resolveFloor 已回落
 * 保守默认，本类锁死构造器防御纵深——maxPerRun=0 即禁、maxChars=0 返回明确提示文本而非空串，
 * 0 只会更严不会失控（对齐 I6/三层硬闸第一道）；A10 追加两级通道口径：事件流优先、
 * run 作用域缓冲兜底（轻链路零改动可回捞）、指针视为 miss、容量挤出的有界退化表述
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
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 0, 20000, new RunScopedRecallBuffer(0));
        String out = recall.recallToolResult("call-1");
        Assert.assertTrue("maxPerRun=0 应收预算用尽指令", out.contains("回捞预算已用尽"));
        Assert.assertFalse("禁止回捞不得透出原文", out.contains("原文全文"));
    }

    /** maxChars=0 即禁回填：命中原文也返回明确提示文本而非空串/截断空内容（防模型见空反复重试） */
    @Test
    public void maxChars为0返回明确提示而非空串() {
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 3, 0, new RunScopedRecallBuffer(0));
        String out = recall.recallToolResult("call-1");
        Assert.assertFalse("不得返回空串", out.trim().isEmpty());
        Assert.assertTrue(out.contains("回捞已关闭"));
        Assert.assertFalse(out.contains("原文全文"));
    }

    /** 正常闸门：maxPerRun=3 时前 3 次透原文、第 4 次收口；maxChars 生效截断 */
    @Test
    public void 正常闸门内透原文超限收口且按maxChars截断() {
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 3, 8, new RunScopedRecallBuffer(0));
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

    /** 无跨 run 通道的轻链路桩（supportsToolResultRecall 取接口默认 false，recallToolResult 取默认 null） */
    private static final AgentLoopListener NO_CHANNEL = new AgentLoopListener() {
        @Override
        public void log(String line) {
        }

        @Override
        public boolean shouldStop() {
            return false;
        }
    };

    /** A10：轻链路（本无回捞通道）零改动即可经 run 缓冲取回原文——"卸载即失联"不再可能 */
    @Test
    public void 无跨run通道时run缓冲兜底取回原文() {
        RunScopedRecallBuffer lossy = new RunScopedRecallBuffer(10000);
        lossy.archive("call-9", "缓冲里的原文全文");
        AgentToolRecall recall = new AgentToolRecall(NO_CHANNEL, 3, 20000, lossy);
        Assert.assertEquals("缓冲里的原文全文", recall.recallToolResult("call-9"));
    }

    /** A10 优先级：会话事件流命中时用事件流，不取缓冲副本（同一 id 两处内容一致，仅路径不同） */
    @Test
    public void 事件流优先于run缓冲() {
        RunScopedRecallBuffer lossy = new RunScopedRecallBuffer(10000);
        lossy.archive("call-1", "缓冲版本不应被取到");
        AgentToolRecall recall = new AgentToolRecall(SOURCE, 3, 20000, lossy);
        Assert.assertTrue("应取事件流原文", recall.recallToolResult("call-1").contains("原文全文内容较长"));
    }

    /** A10：事件流里混进指针文本（历史形态）视为 miss 落缓冲——杜绝"捞→又是指针"的二次回路 */
    @Test
    public void 事件流返回指针视为miss() {
        AgentLoopListener pointerSource = new AgentLoopListener() {
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
                return "call-p".equals(toolCallId)
                        ? ToolResultOffloader.POINTER_PREFIX + "call-p 原 9999 字】中间省略" : null;
            }
        };
        RunScopedRecallBuffer lossy = new RunScopedRecallBuffer(10000);
        lossy.archive("call-p", "指针背后的真原文");
        AgentToolRecall recall = new AgentToolRecall(pointerSource, 3, 20000, lossy);
        Assert.assertEquals("指针背后的真原文", recall.recallToolResult("call-p"));
    }

    /** A10 有界退化：超容量被挤出的句柄回返回"未找到"+ 清单（清单只列仍在表者，不误导模型重试已失条目） */
    @Test
    public void 缓冲挤出后返回未找到且清单不含失条目() {
        RunScopedRecallBuffer lossy = new RunScopedRecallBuffer(20);
        lossy.archive("old-1", "第一条原文占位超过容量上限");
        lossy.archive("new-2", "第二条原文也超过上限");
        Assert.assertTrue("应发生挤出", lossy.dropped() > 0);
        Assert.assertEquals("仅最新一条在表", java.util.Collections.singletonList("new-2"), lossy.handles());
        AgentToolRecall recall = new AgentToolRecall(NO_CHANNEL, 3, 20000, lossy);
        String out = recall.recallToolResult("old-1");
        Assert.assertTrue("应回未找到指令文本", out.contains("未找到句柄 old-1"));
        Assert.assertTrue("清单应只列仍在表句柄", out.contains("new-2") && !out.contains("old-1、"));
        Assert.assertTrue("应点明挤出这一成因", out.contains("被挤出"));
    }
}
