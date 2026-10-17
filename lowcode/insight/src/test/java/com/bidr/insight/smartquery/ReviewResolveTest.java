package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.service.SmartAgentMetaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

/**
 * Title: ReviewResolveTest
 * Description: 评审条目处理标记写回回归（2026-08-25 背景：评审报告商榷项修正完后
 * 无入口消除待办——人工消化闭环需「标记已处理」把 resolved 写回报告 JSON 落盘，
 * 面板按 待处理商榷/已处理/全部 筛选）：标记/撤销落字段、其余条目与顶层字段不动、
 * 下标越界抛提示
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class ReviewResolveTest {

    private static final String REPORT = "{\"reviewed_at\":\"2026-08-25 10:00\",\"total\":2,"
            + "\"questionable\":2,\"items\":["
            + "{\"table\":\"dw.a\",\"verdict\":\"questionable\",\"issue\":\"单位存疑\"},"
            + "{\"table\":\"dw.b\",\"verdict\":\"ok\"}]}";

    /** 标记已处理：items[index] 落 resolved=true，其余条目与顶层统计字段原样保留 */
    @Test
    public void resolveMarksItemResolved() throws Exception {
        String updated = SmartAgentMetaService.applyResolved(REPORT, 0, true);
        JsonNode root = new ObjectMapper().readTree(updated);
        Assert.assertTrue(root.get("items").get(0).get("resolved").asBoolean());
        // 未标记条目不带 resolved；顶层统计不被动
        Assert.assertNull(root.get("items").get(1).get("resolved"));
        Assert.assertEquals(2, root.get("questionable").asInt());
        Assert.assertEquals("单位存疑", root.get("items").get(0).get("issue").asText());
    }

    /** 撤销标记：resolved=false 落回（前端面板按 falsy 视为未处理） */
    @Test
    public void resolveUnmarkWritesFalse() throws Exception {
        String marked = SmartAgentMetaService.applyResolved(REPORT, 1, true);
        String unmarked = SmartAgentMetaService.applyResolved(marked, 1, false);
        JsonNode root = new ObjectMapper().readTree(unmarked);
        Assert.assertFalse(root.get("items").get(1).get("resolved").asBoolean());
    }

    /** 下标越界/负数抛提示异常（前端筛选视图传原始下标，越界属程序错误须显式报） */
    @Test
    public void resolveOutOfRangeThrows() {
        try {
            SmartAgentMetaService.applyResolved(REPORT, 5, true);
            Assert.fail("越界下标应抛异常");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("越界"));
        }
        try {
            SmartAgentMetaService.applyResolved(REPORT, -1, true);
            Assert.fail("负下标应抛异常");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("越界"));
        }
    }
}
