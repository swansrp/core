package com.bidr.llm.agent;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link SessionCurrentUserTool} 离线单测：工具面永不抛，五条回落口径逐条断
 * （SPI 缺席/查无/字段缺失/解析异常/发起人缺失）。文本断言只钉关键行首，
 * 不锁整段文案（文案可调，口径不可丢）。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class SessionCurrentUserToolTest {

    @Test
    public void spiAbsentFallsBackToOperatorRaw() {
        SessionCurrentUserTool tool = new SessionCurrentUserTool("10086", null);
        String text = tool.currentUser();
        Assert.assertTrue(text.contains("10086"));
        Assert.assertTrue(text.contains("宿主未提供人员档案"));
    }

    @Test
    public void profilePresentReturnsNameAndDepartment() {
        SessionCurrentUserTool tool = new SessionCurrentUserTool("10086",
                op -> new OperatorProfile("沙若鹏", "监理部"));
        String text = tool.currentUser();
        Assert.assertTrue(text.contains("当前用户姓名：沙若鹏"));
        Assert.assertTrue(text.contains("所属部门：监理部"));
    }

    @Test
    public void profileNullFallsBackToOperatorRaw() {
        SessionCurrentUserTool tool = new SessionCurrentUserTool("10086", op -> null);
        String text = tool.currentUser();
        Assert.assertTrue(text.contains("10086"));
        Assert.assertTrue(text.contains("人员档案查无"));
    }

    @Test
    public void blankProfileFieldsFallBackFieldWise() {
        SessionCurrentUserTool tool = new SessionCurrentUserTool("10086",
                op -> new OperatorProfile("  ", null));
        String text = tool.currentUser();
        // 姓名缺失回落发起人原文，部门缺失报未知——两字段各自回落，不互相拖垮
        Assert.assertTrue(text.contains("当前用户姓名：10086"));
        Assert.assertTrue(text.contains("所属部门：未知"));
    }

    @Test
    public void resolverExceptionNeverThrows() {
        SessionCurrentUserTool tool = new SessionCurrentUserTool("10086",
                op -> {
                    throw new IllegalStateException("db down");
                });
        String text = tool.currentUser();
        Assert.assertTrue(text.contains("10086"));
        Assert.assertTrue(text.contains("档案解析失败"));
    }

    @Test
    public void blankOperatorNormalizesToAnonymous() {
        SessionCurrentUserTool tool = new SessionCurrentUserTool("  ", null);
        Assert.assertTrue(tool.currentUser().contains("anonymous"));
    }
}
