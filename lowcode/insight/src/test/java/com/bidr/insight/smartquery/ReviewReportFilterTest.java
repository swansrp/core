package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.service.SmartAgentAssetGenerateService;
import com.bidr.insight.smartquery.service.SmartAgentMetaService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Title: ReviewReportFilterTest
 * Description: 评审报告等非标准资产行过滤回归（2026-08-25 背景：AI 评审报告复用资产表
 * 存储（type=review-report），但不属于八类资产——发布快照/一致性校验/自动修复必须跳过，
 * 与既有 llm-prompts 提示词模板行同口径）：
 * 类型常量不入白名单、过滤保留标准八类、空清单与 null 兜底
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class ReviewReportFilterTest {

    private InsightAgentAsset asset(String type) {
        InsightAgentAsset a = new InsightAgentAsset();
        a.setAssetType(type);
        a.setContent("[]");
        return a;
    }

    /** 评审报告类型不属于标准八类（发布/校验/导入导出的白名单依据） */
    @Test
    public void reviewReportTypeNotStandard() {
        Assert.assertFalse(SmartAgentMetaService.ASSET_TYPES.contains(SmartAgentMetaService.REVIEW_REPORT_TYPE));
        Assert.assertEquals("review-report", SmartAgentMetaService.REVIEW_REPORT_TYPE);
    }

    /** 提示词模板类型同不属于标准八类（2026-08-26 背景：draft-counts 徽标聚合未过滤类型，
     *  review-report/llm-prompts 非标准行永不发布（status 恒 0）导致发布后徽标永不清零；
     *  聚合改为只统计 ASSET_TYPES，本用例守卫不可发布类型不得混入白名单） */
    @Test
    public void llmPromptsTypeNotStandard() {
        Assert.assertFalse(SmartAgentMetaService.ASSET_TYPES.contains(
                SmartAgentAssetGenerateService.PROMPTS_ASSET_TYPE));
        Assert.assertEquals("llm-prompts", SmartAgentAssetGenerateService.PROMPTS_ASSET_TYPE);
    }

    /** 混合清单过滤：评审报告与提示词模板行剔除，标准八类全保留且顺序不变 */
    @Test
    public void standardOnlyKeepsEightTypes() {
        List<InsightAgentAsset> mixed = new ArrayList<>(Arrays.asList(
                asset("entities"),
                asset(SmartAgentMetaService.REVIEW_REPORT_TYPE),
                asset("metrics"),
                asset("llm-prompts"),
                asset("row-policies")));
        List<InsightAgentAsset> standard = SmartAgentMetaService.standardOnly(mixed);
        Assert.assertEquals(3, standard.size());
        Assert.assertEquals("entities", standard.get(0).getAssetType());
        Assert.assertEquals("metrics", standard.get(1).getAssetType());
        Assert.assertEquals("row-policies", standard.get(2).getAssetType());
    }

    /** 空清单与 null 兜底：不抛错返空 */
    @Test
    public void standardOnlyEmptyAndNull() {
        Assert.assertTrue(SmartAgentMetaService.standardOnly(new ArrayList<>()).isEmpty());
        Assert.assertTrue(SmartAgentMetaService.standardOnly(null).isEmpty());
    }
}
