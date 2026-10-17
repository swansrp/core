package com.bidr.insight.smartquery.vo;

import com.bidr.admin.vo.PortalWithColumnsRes;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.kernel.vo.common.KeyValueResVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: SmartQueryPlanRes
 * Description: plan 端点响应（§45.1）：校验结论 + SQL 口径展示 +
 * portalConfig（url 指向 smart-query 端点）+ charts[indicator + queryContext]。
 * queryContext 为 semantic_query 原文不透明载荷，前端原样带回 statistic/table 端点
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
public class SmartQueryPlanRes {

    @ApiModelProperty("校验是否通过")
    private boolean valid;

    @ApiModelProperty("阻断性问题（SKILL.md 规则号定位）")
    private List<ValidationResult.Issue> errors = new ArrayList<>();

    @ApiModelProperty("放行但需标注的警告")
    private List<ValidationResult.Issue> warnings = new ArrayList<>();

    @ApiModelProperty("解析/生成阶段错误信息")
    private String errorMessage;

    @ApiModelProperty("参数化 SQL（回答必须展示）")
    private String sql;

    @ApiModelProperty("SQL 绑定参数")
    private List<Object> params = new ArrayList<>();

    @ApiModelProperty("口径备注")
    private List<String> notes = new ArrayList<>();

    @ApiModelProperty("同源推导的 Portal 配置（只读，url 指向 smart-query 端点）")
    private PortalWithColumnsRes portalConfig;

    @ApiModelProperty("推导字典（SELECT 列 reference → 码值对）：前端渲染前注册 dictStore，不落字典表")
    private Map<String, List<KeyValueResVO>> dicts = new LinkedHashMap<>();

    @ApiModelProperty("图表规划产物（当前恒为单图）")
    private List<ChartItem> charts = new ArrayList<>();

    /** 单图规划：indicator（ChartCard 可直接渲染的完整指标配置）+ queryContext（往返载荷） */
    @Data
    public static class ChartItem {

        @ApiModelProperty("完整 indicator 配置（dataMetrics/firstDimension/filterConditions，与卡片配置同构）")
        private Object indicator;

        private String queryContext;
    }
}
