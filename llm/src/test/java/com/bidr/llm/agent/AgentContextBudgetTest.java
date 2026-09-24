package com.bidr.llm.agent;

import com.bidr.llm.constant.param.LlmParam;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: AgentContextBudgetTest
 * Description: 阈值取值出口单测（计划 §6.2）：纯函数 resolve 直打三级优先级与异常回落，
 * 并守卫岔路 4 定案的「框架默认全关」——新增 Param 键两项主开关默认 "0"（守 I9）、
 * 键名唯一且不与 AgentRuntimeParam 相撞（sys_config 全局唯一口径）
 *
 * @author sharp
 * @since 2026/9/25
 */
public class AgentContextBudgetTest {

    @Test
    public void override正值优先于db值() {
        Assert.assertEquals(4000, AgentContextBudget.resolve("9999", 4000, 0));
    }

    @Test
    public void override为0回落db值() {
        Assert.assertEquals(9999, AgentContextBudget.resolve("9999", 0, 0));
    }

    @Test
    public void db非数字回落默认不抛() {
        Assert.assertEquals(0, AgentContextBudget.resolve("abc", 0, 0));
        Assert.assertEquals(1000, AgentContextBudget.resolve("  ", 0, 1000));
    }

    /** 开关键（offloadChars/tokenBudget）：0 与负值一律视为关闭返回 0（I9 设计本体） */
    @Test
    public void 开关键0与负值视为关闭() {
        Assert.assertEquals(0, AgentContextBudget.resolve("-5", 0, 3));
        Assert.assertEquals(0, AgentContextBudget.resolve("0", 0, 3));
    }

    /** 闸门键（previewChars/recallMaxPerRun/recallMaxChars）：0 或负一律回落保守枚举默认，
     *  而非返回 0（否则运维填 0 反得无限回捞/2 字预览，恰好击穿三层硬闸第一道） */
    @Test
    public void 闸门键0与负值回落保守默认() {
        Assert.assertEquals(3, AgentContextBudget.resolveFloor("-5", 0, 3));
        Assert.assertEquals(3, AgentContextBudget.resolveFloor("0", 0, 3));
        // recallMaxPerRun 语义锚点：db 填 0 应回落枚举默认 3，既非 0（禁）也非 MAX_VALUE（无限）
        Assert.assertEquals(3, AgentContextBudget.resolveFloor("0", 0,
                Integer.parseInt(LlmParam.AGENT_TOOL_RECALL_MAX_PER_RUN.getDefaultValue())));
    }

    /** 闸门键非数字/空仍回落默认（与 resolve 同支）；正值优先 */
    @Test
    public void 闸门键非数字回落默认且正值优先() {
        Assert.assertEquals(1000, AgentContextBudget.resolveFloor("abc", 0, 1000));
        Assert.assertEquals(1000, AgentContextBudget.resolveFloor(null, 0, 1000));
        Assert.assertEquals(1000, AgentContextBudget.resolveFloor("", 0, 1000));
        Assert.assertEquals(2000, AgentContextBudget.resolveFloor("50", 2000, 1000));
        Assert.assertEquals(50, AgentContextBudget.resolveFloor("50", 0, 1000));
    }

    /** previewChars db=0 不产生 2 字预览：经 resolveFloor 回落枚举默认（合计预览字数应 > 0） */
    @Test
    public void previewChars闸填0回落默认不产生极短预览() {
        int def = Integer.parseInt(LlmParam.AGENT_TOOL_RESULT_PREVIEW_CHARS.getDefaultValue());
        int resolved = AgentContextBudget.resolveFloor("0", 0, def);
        Assert.assertEquals(def, resolved);
        Assert.assertTrue("预览字数回落须为正,不得为 0/极短", resolved > 0);
    }

    @Test
    public void db空串回落默认() {
        Assert.assertEquals(3, AgentContextBudget.resolve(null, 0, 3));
        Assert.assertEquals(3, AgentContextBudget.resolve("", 0, 3));
        Assert.assertEquals(3, AgentContextBudget.resolve("   ",  0, 3));
    }

    /** 守 I9/岔路 4：两个行为开关（卸载阈值/token 预算）默认必须为 0=关闭 */
    @Test
    public void LlmParam新增行为开关默认为0即默认关() {
        Assert.assertEquals("0", LlmParam.AGENT_TOOL_RESULT_OFFLOAD_CHARS.getDefaultValue());
        Assert.assertEquals("0", LlmParam.AGENT_CONTEXT_TOKEN_BUDGET.getDefaultValue());
    }

    /** 新增项 defaultValue 均可解析为 int（resolve/enumDefault 依赖该声明契约） */
    @Test
    public void 新增项defaultValue可解析为int() {
        int[] def = parse(LlmParam.AGENT_TOOL_RESULT_OFFLOAD_CHARS.getDefaultValue());
        Assert.assertNotNull(def);
        Assert.assertNotNull(parse(LlmParam.AGENT_TOOL_RESULT_PREVIEW_CHARS.getDefaultValue()));
        Assert.assertNotNull(parse(LlmParam.AGENT_CONTEXT_TOKEN_BUDGET.getDefaultValue()));
        Assert.assertNotNull(parse(LlmParam.AGENT_TOOL_RECALL_MAX_PER_RUN.getDefaultValue()));
        Assert.assertNotNull(parse(LlmParam.AGENT_TOOL_RECALL_MAX_CHARS.getDefaultValue()));
    }

    private static int[] parse(String v) {
        try {
            return new int[]{Integer.parseInt(v)};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 键名守卫：LlmParam 全枚举名唯一；新增五项均带 AGENT_ 前缀且不与 AgentRuntimeParam
     *  的 AGENT_RUNTIME_* 相撞（sys_config 键全局唯一，撞键互覆盖是最难查的静默事故）。
     *  agent-runtime 模块不在 llm 测试类路径上（其反向依赖 llm），经 Class.forName 尽力校验，
     *  不在路径时退化为前缀排它断言 */
    @Test
    public void LlmParam键名唯一且不与AgentRuntimeParam相撞() throws Exception {
        Set<String> names = new HashSet<>();
        Set<String> agentNew = new HashSet<>();
        for (LlmParam p : LlmParam.values()) {
            Assert.assertTrue("LlmParam 键名重复: " + p.name(), names.add(p.name()));
            if (p.name().startsWith("AGENT_")) {
                agentNew.add(p.name());
            }
        }
        Assert.assertTrue(agentNew.contains("AGENT_CONTEXT_TOKEN_BUDGET"));
        for (String k : agentNew) {
            Assert.assertFalse("不得占用 AGENT_RUNTIME_ 前缀（AgentRuntimeParam 键域）: " + k,
                    k.startsWith("AGENT_RUNTIME_"));
        }
        Class<?> runtime;
        try {
            runtime = Class.forName("com.bidr.agent.runtime.constant.param.AgentRuntimeParam");
        } catch (ClassNotFoundException e) {
            return; // 模块不在测试类路径，前缀排它断言已生效
        }
        Assert.assertTrue(runtime.isEnum());
        for (Object c : runtime.getEnumConstants()) {
            String n = ((Enum<?>) c).name();
            Assert.assertFalse("与 AgentRuntimeParam 撞键: " + n, names.contains(n));
        }
        // 反射遍历两枚举的取值方法存在性核验（防未来重构悄悄改签名让 resolve 失明）
        for (Object c : LlmParam.values()) {
            Method m = c.getClass().getMethod("getDefaultValue");
            Assert.assertNotNull(m.invoke(c));
        }
    }
}
