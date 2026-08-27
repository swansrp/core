package com.bidr.insight.smartquery.vo;

import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: SmartQueryAskRes
 * Description: 维护问数（/maintain/ask）响应：plan 结构（校验/SQL/口径）+ 执行数据
 * 内联返回（rows/statistics，临时层答案无法走 statistic 端点二次取数）+
 * 建议元信息（batchNo/proposedCount）。usedProposals=true 表示本次基于 LLM
 * 临时资产建议作答，建议已落待审提案
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmartQueryAskRes extends SmartQueryPlanRes {

    @ApiModelProperty("本次作答使用的 semantic_query 原文")
    private String semanticQuery;

    @ApiModelProperty("本次会话各阶段发出的提示词全文（阶段 → 全文，调试排查用；不落历史对话）")
    private Map<String, String> prompts;

    @ApiModelProperty("是否基于 LLM 临时资产建议作答（建议已落待审提案）")
    private boolean usedProposals;

    @ApiModelProperty("提案批次号（usedProposals=true 时有值）")
    private String batchNo;

    @ApiModelProperty("本次记录的待审提案条数")
    private int proposedCount;

    @ApiModelProperty("执行结果列（alias → 显示名）")
    private List<ColumnInfo> columns = new ArrayList<>();

    @ApiModelProperty("执行结果行（alias → 值，已做码值翻译）")
    private List<Map<String, Object>> rows = new ArrayList<>();

    @ApiModelProperty("statistic payload 转换结果（图表渲染用，与 statistic 端点同构）")
    private List<StatisticRes> statistics = new ArrayList<>();

    @Data
    public static class ColumnInfo {
        private String alias;
        /** dimension / metric / field */
        private String kind;
        private String display;
    }
}
