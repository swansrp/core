package com.bidr.insight.smartquery.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: SmartQueryPlanReq
 * Description: plan 端点请求（§45.1）：标准 semantic_query 协议原样传入，
 * chartMode 可选（缺省后端按规则推断），title 可选（缺省用指标措辞）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
public class SmartQueryPlanReq {

    @ApiModelProperty(value = "标准 semantic_query 协议对象（裸对象或 {semantic_query:{...}} 包裹均可）", required = true)
    private JsonNode semanticQuery;

    @ApiModelProperty("图表形态：bar/pie/metricsPie/rankingBar 等；缺省按规则推断")
    private String chartMode;

    @ApiModelProperty("图表标题（用户问题措辞）")
    private String title;
}
