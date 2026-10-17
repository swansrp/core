package com.bidr.agent.runtime.relay;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Title: AgentRuntimeRelayUploadNameTest
 * Description: relay 代收转推的**名字压平**测试。provider 的 {@code uploadFile} 现在按相对路径写工作区
 * （业务侧要能派多目录底稿），所以"浏览器只许交一个展示名"这道收口必须落在 relay 层——
 * 否则 {@code ../../x} 会被当成合法嵌套递到上游。
 * <p>
 * 两层职责分开锁：本层只压掉目录层级，真正的拒（绝对路径／盘符／{@code ..} 段）在 provider 的清洗里。
 * 压平后仍可能剩一个纯点号名（{@code a/.. → ".."}），那必须由 provider 拒，所以两道闸是<b>叠加</b>
 * 关系，不是relay 侧清洗完 provider 就能省事。
 * <p>
 * ⚠️ 本模块跑在 JUnit 5（surefire-junit-platform）上：写成 JUnit 4 会被**静默跳过**。
 *
 * @author sharp
 * @since 2026/10/3
 */
public class AgentRuntimeRelayUploadNameTest {

    @Test
    public void 目录穿越与绝对路径只留下文件名() {
        Assertions.assertEquals("passwd", AgentRuntimeRelayController.basenameOf("../../etc/passwd"));
        Assertions.assertEquals("passwd", AgentRuntimeRelayController.basenameOf("/etc/passwd"));
        Assertions.assertEquals("evil.txt", AgentRuntimeRelayController.basenameOf("..\\..\\windows\\evil.txt"));
        Assertions.assertEquals("a.txt", AgentRuntimeRelayController.basenameOf("docs/a.txt"));
    }

    @Test
    public void 压平只吃掉目录层级不留分隔符() {
        for (String risky : new String[]{"..", ".", "...", "..%2f", "./..", "/", "a/b/", "\\\\"}) {
            String name = AgentRuntimeRelayController.basenameOf(risky);
            Assertions.assertFalse(name.isEmpty(), "不得为空名：" + risky);
            Assertions.assertFalse(name.contains("/") || name.contains("\\"),
                    "不得残留分隔符：" + risky + " → " + name);
        }
        // 单层点号名（".."/"..."）压不出分隔符、本层不判它违法——穿越那道闸在 provider 的路径清洗里，
        // 两层叠加而不是互相顶替：所以这里锁"只压层级"，不在 relay 重复实现一遍清洗。
        Assertions.assertEquals("..", AgentRuntimeRelayController.basenameOf("a/.."));
    }

    @Test
    public void 空名与纯目录名退化成兜底名() {
        Assertions.assertEquals("file", AgentRuntimeRelayController.basenameOf(null));
        Assertions.assertEquals("file", AgentRuntimeRelayController.basenameOf("   "));
        Assertions.assertEquals("file", AgentRuntimeRelayController.basenameOf("docs/"));
        Assertions.assertEquals("file", AgentRuntimeRelayController.basenameOf("docs/   "));
    }

    @Test
    public void 常规名与中文名原样保留() {
        Assertions.assertEquals("施工方案.md", AgentRuntimeRelayController.basenameOf("施工方案.md"));
        Assertions.assertEquals("v1.2-final.tar.gz", AgentRuntimeRelayController.basenameOf("v1.2-final.tar.gz"));
        Assertions.assertEquals("a b;c.txt", AgentRuntimeRelayController.basenameOf("a b;c.txt"));
    }
}
