package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: ToolResultOffloaderTest
 * Description: 入场卸载纯函数单测（计划 §6.3）：锁死指针格式契约——I7（句柄前 60 字符内、
 * 经 digest brief(100) 截断后仍可反解）、I5（A10 起卸载判定与通道位无关）、I6（neverOffload 命中不卸载）、
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
    }

    /**
     * I7 的真实形态维度（层 2 真模型实测抓出的盲区回归）：生产 tool_call_id 长 29 字（OpenAI 风格
     * {@code call_<24hex>}），首行按可照抄口径再写一次 id 后整体 124 字。旧实现把「首行 &lt;100 字」
     * 当成不变式，真环境对任何结果都放弃卸载（累计卸载恒 0），而上述短 id 用例全绿——两形态从未交叉。
     * 本用例锁死：长 id 必须照常卸载，且句柄在 brief(100)/brief(300) 截断后仍可反解
     */
    @Test
    public void 生产形态长id仍卸载且截断后句柄可反解() {
        String id = "call_39c844940c8c42a59dda6743";
        Assert.assertEquals("真实形态 id 长度", 29, id.length());
        String text = big(2633);
        Assert.assertTrue(ToolResultOffloader.shouldOffload("search_docs", text, 2000,
                Collections.singleton("waitExternalTask")));
        String p = ToolResultOffloader.pointerOf(id, "search_docs", text.length(), 900, text, 1000);
        Assert.assertNotNull("长 id 不得放弃卸载（放弃＝真环境零卸载）", p);
        Assert.assertTrue("指针仍须严格短于原文,实际 " + p.length(), p.length() < text.length());
        String firstLine = p.split("\n", 2)[0];
        Assert.assertTrue("首行长 id 形态本就 >100 字,实际 " + firstLine.length(), firstLine.length() > 100);
        Assert.assertEquals(id, ToolResultOffloader.handleOf(brief(p, 100)));
        Assert.assertEquals(id, ToolResultOffloader.handleOf(brief(p, 300)));
        // I7 保证的真实下限：句柄结束位置 ≤60 ⇒ 任何 ≥60 字截断可反解（长 id 形态结束于 45 字）；
        // brief(40) 反解不出属预期——把断言写在 40 上等于要求 id ≤24 字，那才是旧实现的隐藏假设
        Assert.assertEquals(id, ToolResultOffloader.handleOf(brief(p, 60)));
    }

    /** I7 边界：id 长到句柄落不进前 60 字符时放弃卸载（防截断后无句柄的死指针） */
    @Test
    public void 句柄落不进前60字符时放弃卸载() {
        String tooLong = "call_" + String.join("", Collections.nCopies(40, "a"));
        String text = big(5000);
        Assert.assertNull("id 结束位置越过 60 字符须放弃",
                ToolResultOffloader.pointerOf(tooLong, "t", text.length(), 1500, text, 1000));
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
                    ToolResultOffloader.shouldOffload(name, bigText, 4000, never));
        }
        Assert.assertTrue("不在豁免集且超长应卸载",
                ToolResultOffloader.shouldOffload("otherTool", bigText, 4000, never));
    }

    /** 引用 AgentToolRecall.TOOL_NAME 的常量镜像（避免测试对 N3 类加载顺序的耦合，值一致性另有断言） */
    private static final class AgentToolRecallNames {
        static final String RECALL = "recallToolResult";
    }

    /** A10：卸载判定不再看过道位——阈值与豁免集为唯一判据（run 缓冲保证卸载必有取回路径） */
    @Test
    public void 卸载判定与回捞通道无关() {
        Assert.assertTrue(ToolResultOffloader.shouldOffload("bigReport", big(5000), 4000,
                Collections.<String>emptySet()));
    }

    /** 阈值边界：len==offloadChars 不卸载、+1 卸载 */
    @Test
    public void 阈值边界等长不卸载超长卸载() {
        String exact = big(4000);
        Assert.assertFalse("等阈值不卸载", ToolResultOffloader.shouldOffload("t", exact, 4000,
                Collections.<String>emptySet()));
        Assert.assertTrue("+1 卸载", ToolResultOffloader.shouldOffload("t", exact + "多", 4000,
                Collections.<String>emptySet()));
    }

    /** I9：offloadChars=0 关闭态一律不卸载 */
    @Test
    public void 关闭态阈值0一律不卸载() {
        Assert.assertFalse(ToolResultOffloader.shouldOffload("t", big(100000), 0,
                Collections.<String>emptySet()));
        Assert.assertFalse(ToolResultOffloader.shouldOffload("t", big(100000), -1,
                Collections.<String>emptySet()));
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
        Assert.assertFalse(ToolResultOffloader.shouldOffload(null, null, 4000, null));
        Assert.assertNull(ToolResultOffloader.pointerOf(null, null, 0, 0, null, 1000));
    }
}
