package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: ToolResultOffloaderTest
 * Description: 入场卸载纯函数单测（计划 §6.3）：锁死指针格式契约——I7（句柄前 60 字符内、
 * 经 digest brief(100) 截断后仍可反解）、I5（无回捞通道不卸载）、I6（neverOffload 命中不卸载）、
 * I9（阈值 0 关闭态一律不卸载）、阈值边界（==阈值不卸载、+1 卸载）、反向膨胀硬校验
 *
 * @author sharp
 * @since 2026/9/25
 */
public class ToolResultOffloaderTest {

    /** 与 ToolAgentRunner#brief 同口径的截断仿真（digestOf 驱逐指针时实际经过的形态） */
    private static String brief(String s, int max) {
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > max ? one.substring(0, max) + "..." : one;
    }

    private static String big(int chars) {
        StringBuilder sb = new StringBuilder(chars);
        for (int i = 0; i < chars; i++) {
            sb.append((char) ('一' + i % 3000));
        }
        return sb.toString();
    }

    /** I7：指针首行前 60 字符内必须含 tool_call_id 原文 */
    @Test
    public void 指针前60字符内含tool_call_id() {
        String p = ToolResultOffloader.pointerOf("call-1", "bigReport", 12345, 4300, big(12345), 1000);
        Assert.assertNotNull(p);
        String firstLine = p.split("\n", 2)[0];
        int pos = firstLine.indexOf("call-1");
        Assert.assertTrue("句柄必须落在首行前 60 字符内,实际位置 " + pos, pos >= 0 && pos + "call-1".length() <= 60);
        Assert.assertTrue("首行整体须短于 100 字符以扛 brief(100),实际 " + firstLine.length(), firstLine.length() < 100);
    }

    /** I7 下游：经 brief(…,100)（digest 驱逐）与 brief(…,300)（日志）截断后句柄仍完整可反解 */
    @Test
    public void 截断后句柄仍完整可反解() {
        String p = ToolResultOffloader.pointerOf("call-9", "bigReport", 30000, 12000, big(30000), 1000);
        Assert.assertNotNull(p);
        Assert.assertEquals("call-9", ToolResultOffloader.handleOf(brief(p, 100)));
        Assert.assertEquals("call-9", ToolResultOffloader.handleOf(brief(p, 300)));
        // digestOf 对结果文本走 brief(…,100)：即使截到只剩省略号前缀,句柄仍可反解
        Assert.assertEquals("call-9", ToolResultOffloader.handleOf(brief(p, 40)));
    }

    /** 反向膨胀硬校验：指针不短于原文（小结果+超长 id 病态情形）时放弃卸载返回 null */
    @Test
    public void 指针不短于原文时放弃卸载() {
        String small = "短结果 abcdefghijklmnop";
        // 结果仅 22 字但阈值被调用方判过——直接调 pointerOf 核验硬校验：预览+提示语必然不短于原文
        String p = ToolResultOffloader.pointerOf("call-" + small, "t", small.length(), 8, small, 1000);
        Assert.assertNull("小结果+长句柄必须放弃卸载", p);
    }

    /** I6：neverOffload 命中（askUser、recallToolResult、自定义 pinned）一律不卸载 */
    @Test
    public void 永不卸载集命中不卸载() {
        Set<String> never = new HashSet<>();
        never.add("askUser");
        never.add(AgentToolRecallNames.RECALL);
        never.add("myPinnedTool");
        String bigText = big(5000);
        for (String name : never) {
            Assert.assertFalse(name + " 应永不卸载",
                    ToolResultOffloader.shouldOffload(name, bigText, 4000, never, true));
        }
        Assert.assertTrue("不在豁免集且超长应卸载",
                ToolResultOffloader.shouldOffload("otherTool", bigText, 4000, never, true));
    }

    /** 引用 AgentToolRecall.TOOL_NAME 的常量镜像（避免测试对 N3 类加载顺序的耦合，值一致性另有断言） */
    private static final class AgentToolRecallNames {
        static final String RECALL = "recallToolResult";
    }

    /** I5：无回捞通道一律原文入窗 */
    @Test
    public void 无回捞通道不卸载() {
        Assert.assertFalse(ToolResultOffloader.shouldOffload("bigReport", big(5000), 4000,
                Collections.<String>emptySet(), false));
    }

    /** 阈值边界：len==offloadChars 不卸载、+1 卸载 */
    @Test
    public void 阈值边界等长不卸载超长卸载() {
        String exact = big(4000);
        Assert.assertFalse("等阈值不卸载", ToolResultOffloader.shouldOffload("t", exact, 4000,
                Collections.<String>emptySet(), true));
        Assert.assertTrue("+1 卸载", ToolResultOffloader.shouldOffload("t", exact + "多", 4000,
                Collections.<String>emptySet(), true));
    }

    /** I9：offloadChars=0 关闭态一律不卸载 */
    @Test
    public void 关闭态阈值0一律不卸载() {
        Assert.assertFalse(ToolResultOffloader.shouldOffload("t", big(100000), 0,
                Collections.<String>emptySet(), true));
        Assert.assertFalse(ToolResultOffloader.shouldOffload("t", big(100000), -1,
                Collections.<String>emptySet(), true));
    }

    /** isPointer 识别自身产物；普通文本/null 不误认；非指针反解句柄返回 null */
    @Test
    public void isPointer识别自身产物() {
        String p = ToolResultOffloader.pointerOf("call-1", "bigReport", 12345, 4300, big(12345), 1000);
        Assert.assertNotNull(p);
        Assert.assertTrue(ToolResultOffloader.isPointer(p));
        Assert.assertTrue(ToolResultOffloader.isPointer(brief(p, 100)));
        Assert.assertFalse(ToolResultOffloader.isPointer("普通工具结果【解析报告】"));
        Assert.assertFalse(ToolResultOffloader.isPointer(null));
        Assert.assertNull(ToolResultOffloader.handleOf("普通工具结果"));
        Assert.assertNull(ToolResultOffloader.handleOf(null));
        Assert.assertEquals("call-1", ToolResultOffloader.handleOf(p));
    }

    /** 工具名为 null 也不抛（防御：模型幻觉调用无 name 的极端形态） */
    @Test
    public void null工具名null结果不抛() {
        Assert.assertFalse(ToolResultOffloader.shouldOffload(null, null, 4000, null, true));
        Assert.assertNull(ToolResultOffloader.pointerOf(null, null, 0, 0, null, 1000));
    }
}
