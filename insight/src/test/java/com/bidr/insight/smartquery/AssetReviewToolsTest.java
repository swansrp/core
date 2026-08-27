package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.service.AssetReviewTools;
import com.bidr.insight.smartquery.service.GenTaskContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AssetReviewToolsTest
 * Description: AI 评审链产出工具单测（2026-08-25 背景：历史认证结论质量复核——
 * 评审链只读不注册写工具，结论经 add_review_item 累积、submit_review 落盘结构化报告）：
 * 条目累积与同表同疑点覆盖、商榷项必带疑点与证据、空提交拒绝、报告 JSON 结构、
 * finish 前置闸（未落盘先拒、二次强制收口）、停止守卫拦截
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class AssetReviewToolsTest {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 报告落盘捕获器（替代 SmartAgentMetaService.saveReviewReport） */
    private final List<String[]> saved = new ArrayList<>();

    private AssetReviewTools tools() {
        GenTaskContext ctx = new GenTaskContext("test_agent", GenTaskContext.MODE_REVIEW);
        return new AssetReviewTools(ctx, (code, json) -> saved.add(new String[]{code, json}),
                line -> { }, () -> false);
    }

    /** 累积与覆盖：同表同疑点后来者覆盖，条目总数不变；不同疑点各自累积 */
    @Test
    public void accumulatesAndCoversDuplicate() {
        AssetReviewTools t = tools();
        Assert.assertTrue(t.addReviewItem("dw.ht_info", "ok", "角色/单位/键结论合理", "", "", "").startsWith("已添加"));
        Assert.assertTrue(t.addReviewItem("dw.ht_info", "questionable", "amt 单位存疑", "采样最大值为 1.2 亿，与「万元」量级不符", "改为「元」", "amt")
                .startsWith("已添加"));
        Assert.assertEquals(2, t.getItemCount());
        // 同表同疑点重提（修正证据后）：覆盖不留陈旧条目（column 一并更新）
        Assert.assertTrue(t.addReviewItem("dw.ht_info", "questionable", "amt 单位存疑", "复核后确认单位为元", "改为「元」", "amt")
                .startsWith("已更新"));
        Assert.assertEquals("覆盖不增条目", 2, t.getItemCount());
        Assert.assertEquals(1, t.getQuestionableCount());
    }

    /** 商榷项守卫：疑点与证据链必填，无证据的直觉不算疑点 */
    @Test
    public void questionableRequiresIssueAndEvidence() {
        AssetReviewTools t = tools();
        Assert.assertTrue(t.addReviewItem("dw.ht_info", "questionable", "", "证据", "建议", "").startsWith("拒绝"));
        Assert.assertTrue(t.addReviewItem("dw.ht_info", "questionable", "疑点", "", "建议", "").startsWith("拒绝"));
        Assert.assertTrue(t.addReviewItem("", "ok", "要点", "", "", "").startsWith("拒绝"));
        Assert.assertTrue(t.addReviewItem("dw.ht_info", "maybe", "要点", "", "", "").startsWith("拒绝"));
        Assert.assertEquals("非法入参不累积", 0, t.getItemCount());
    }

    /** 空提交拒绝：无任何评审条目时 submit_review 不落盘 */
    @Test
    public void submitRejectsEmpty() {
        AssetReviewTools t = tools();
        Assert.assertTrue(t.submitReview().startsWith("拒绝"));
        Assert.assertTrue(saved.isEmpty());
        Assert.assertFalse(t.isReportSubmitted());
    }

    /** 报告 JSON 结构：元信息 + 计数 + 条目明细（NON_NULL：ok 条目不携带空证据/建议字段） */
    @Test
    public void submitSerializesReport() throws Exception {
        AssetReviewTools t = tools();
        t.addReviewItem("dw.ht_info", "ok", "结论要点", "", "", "");
        t.addReviewItem("dw.ht_pay", "questionable", "键不唯一", "COUNT 与 COUNT(DISTINCT) 不一致", "改业务键为 id+dy", "");
        t.addReviewItem("dw.ht_info", "questionable", "amt 单位存疑", "采样最大值为 1.2 亿", "改为「元」", "amt");
        Assert.assertTrue(t.submitReview().startsWith("评审报告已落盘"));
        Assert.assertTrue(t.isReportSubmitted());
        Assert.assertEquals(1, saved.size());
        Assert.assertEquals("test_agent", saved.get(0)[0]);
        JsonNode root = OM.readTree(saved.get(0)[1]);
        Assert.assertEquals("1.0", root.path("schema_version").asText());
        Assert.assertEquals("test_agent", root.path("agent_code").asText());
        Assert.assertEquals(3, root.path("total").asInt());
        Assert.assertEquals(2, root.path("questionable").asInt());
        Assert.assertFalse(root.path("reviewed_at").asText().isEmpty());
        JsonNode items = root.path("items");
        Assert.assertEquals(3, items.size());
        Assert.assertEquals("dw.ht_info", items.get(0).path("table").asText());
        Assert.assertEquals("ok", items.get(0).path("verdict").asText());
        Assert.assertFalse("ok 条目不携带空证据", items.get(0).has("evidence"));
        Assert.assertEquals("questionable", items.get(1).path("verdict").asText());
        Assert.assertEquals("键不唯一", items.get(1).path("issue").asText());
        Assert.assertEquals("改业务键为 id+dy", items.get(1).path("suggestion").asText());
        Assert.assertFalse("表级疑点不携带空列名", items.get(1).has("column"));
        // 列级疑点携带 column（前端「去修正」据此滚动定位并高亮该列）
        Assert.assertEquals("amt", items.get(2).path("column").asText());
    }

    /** finish 前置闸：未落盘报告先拒一次，落盘后正常收口；无报告二次调用为强制收口逃生口 */
    @Test
    public void finishGatedBySubmit() {
        AssetReviewTools t = tools();
        Assert.assertTrue("未提交先拒", t.finish("总结").startsWith("评审报告尚未落盘"));
        Assert.assertFalse(t.isFinished());
        t.addReviewItem("dw.ht_info", "ok", "结论要点", "", "", "");
        t.submitReview();
        Assert.assertTrue(t.finish("2 张表，1 条商榷").startsWith("已确认收口"));
        Assert.assertTrue(t.isFinished());
        Assert.assertEquals("2 张表，1 条商榷", t.getFinishSummary());

        // 逃生口：无报告二次 finish 强制收口
        AssetReviewTools t2 = tools();
        Assert.assertTrue(t2.finish("第一次").startsWith("评审报告尚未落盘"));
        Assert.assertTrue(t2.finish("第二次").startsWith("已确认收口"));
    }

    /** 停止守卫：任务被停止后所有工具拒绝调用 */
    @Test
    public void stopGuardBlocksAllTools() {
        GenTaskContext ctx = new GenTaskContext("test_agent", GenTaskContext.MODE_REVIEW);
        AssetReviewTools t = new AssetReviewTools(ctx, (code, json) -> saved.add(new String[]{code, json}),
                line -> { }, () -> true);
        String blocked = "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}";
        Assert.assertEquals(blocked, t.addReviewItem("dw.ht_info", "ok", "要点", "", "", ""));
        Assert.assertEquals(blocked, t.submitReview());
        Assert.assertEquals(blocked, t.finish("总结"));
        Assert.assertEquals(blocked, t.reportUnconfirmed("疑点", "口径", "证据", "影响"));
        Assert.assertTrue(saved.isEmpty());
    }
}
