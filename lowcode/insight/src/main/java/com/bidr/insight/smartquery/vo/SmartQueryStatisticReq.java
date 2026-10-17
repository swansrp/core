package com.bidr.insight.smartquery.vo;

import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: SmartQueryStatisticReq
 * Description: statistic 端点请求 = 现有 AdvancedStatisticReq 协议（前端
 * buildRequestParams 零改动产出）+ 唯一新增字段 queryContext（plan 下发的
 * semantic_query 原文载荷，前端原样带回）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmartQueryStatisticReq extends AdvancedStatisticReq {

    @ApiModelProperty(value = "查询上下文（semantic_query 原文，不透明载荷）", required = true)
    private String queryContext;
}
