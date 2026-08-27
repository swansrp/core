package com.bidr.insight.smartquery.service;

import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

/**
 * Title: SmartQueryAskPromptsTest
 * Description: 提示词资源完备性回归（2026-08-26 背景：提示词由 Java 常量迁入 resources yaml，
 * 按功能分文件约定——问数链 prompts-ask.yml、生成链 prompts.yml、评审链 prompts-review.yml）：
 * ① 问数链各段模板键齐全且模板占位符完整（占位符丢失会导致运行时替换落空、LLM 收到占位符原文）；
 * ② 生成/评审链拆分后资源仍可加载且键集不相交（defaultPrompts 合并口径不变）
 *
 * @author Sharp
 * @since 2026/8/26
 */
public class SmartQueryAskPromptsTest {

    /** 问数链模板键齐全且非空（键缺失/为空在运行期抛异常阻断，此处提前暴露打包问题） */
    @Test
    public void askPromptKeysPresent() {
        Map<String, String> m = SmartQueryMaintainService.loadAskPrompts();
        for (String key : new String[]{
                "parseBusiness", "parsePrompt", "clarifySuffix", "planExample",
                "maintainBusiness", "maintainPrompt", "toolModeSuffix",
                "repairBusiness", "repairPrompt",
                "escapeBusiness", "escapePrompt", "agentBusiness"}) {
            Assert.assertNotNull("问数链提示词缺键: " + key, m.get(key));
            Assert.assertFalse("问数链提示词键值为空: " + key, m.get(key).trim().isEmpty());
        }
    }

    /** 模板占位符完整（编排按这些占位符注入索引/问题/错误等上下文） */
    @Test
    public void askPromptPlaceholdersIntact() {
        Map<String, String> m = SmartQueryMaintainService.loadAskPrompts();
        for (String ph : new String[]{"{asset_index}", "{question}"}) {
            Assert.assertTrue("parsePrompt 缺占位符 " + ph, m.get("parsePrompt").contains(ph));
        }
        for (String ph : new String[]{"{question}", "{errors_json}", "{asset_index}"}) {
            Assert.assertTrue("maintainPrompt 缺占位符 " + ph, m.get("maintainPrompt").contains(ph));
        }
        for (String ph : new String[]{"{question}", "{error}", "{semantic_query}", "{asset_index}"}) {
            Assert.assertTrue("repairPrompt 缺占位符 " + ph, m.get("repairPrompt").contains(ph));
        }
        for (String ph : new String[]{"{question}", "{reason}", "{entities}", "{facts}"}) {
            Assert.assertTrue("escapePrompt 缺占位符 " + ph, m.get("escapePrompt").contains(ph));
        }
    }

    /** 生成/评审链按功能拆分后：两文件各自可加载、键集不相交、评审模板占位符完整 */
    @Test
    public void generateReviewPromptsSplitLoadable() {
        Map<String, String> generate = SmartAgentAssetGenerateService.loadYamlPrompts("smartquery/prompts.yml");
        Map<String, String> review = SmartAgentAssetGenerateService.loadYamlPrompts("smartquery/prompts-review.yml");
        Assert.assertTrue("生成链缺 autonomousPrompt", generate.containsKey("autonomousPrompt"));
        Assert.assertTrue("生成链缺 metricsPrompt", generate.containsKey("metricsPrompt"));
        Assert.assertFalse("reviewPrompt 应已迁出 prompts.yml", generate.containsKey("reviewPrompt"));
        Assert.assertTrue("评审链缺 reviewPrompt", review.containsKey("reviewPrompt"));
        Assert.assertTrue("reviewPrompt 缺占位符 {entities_json}",
                review.get("reviewPrompt").contains("{entities_json}"));
        for (String key : review.keySet()) {
            Assert.assertFalse("生成/评审链键集相交: " + key, generate.containsKey(key));
        }
    }
}
