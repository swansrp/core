package com.bidr.insight.smartquery.vo;

import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: SmartQueryTableReq
 * Description: table 端点（穿透明细）请求 = 现有 AdvancedQueryReq 协议
 * （buildDrillConditionFromStatistic 产出的穿透条件 + 分页）+ queryContext
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmartQueryTableReq extends AdvancedQueryReq {

    @ApiModelProperty(value = "查询上下文（semantic_query 原文，不透明载荷）", required = true)
    private String queryContext;
}
