package com.bidr.insight.chatbi.vo;

import com.bidr.insight.smartquery.vo.SemanticField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ChatBiSemanticCatalog
 * Description: 智能问数语义目录——按 tableId 聚合的指标卡片目录与筛选字段目录，
 * 是大模型可引用的全部语义边界，也是前端合并生成物的依据
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChatBiSemanticCatalog {

    @ApiModelProperty(value = "表格code")
    private String tableId;

    @ApiModelProperty(value = "表格配置名称（portalName）")
    private String portalName;

    @ApiModelProperty(value = "指标卡片目录（已按当前用户权限过滤）")
    private List<SemanticIndicator> indicators;

    @ApiModelProperty(value = "indicator 筛选组目录（口语筛选项，conditions 可原样进 chart-spec）")
    private List<SemanticIndicatorGroup> indicatorGroups;

    @ApiModelProperty(value = "可引用的筛选字段目录")
    private List<SemanticField> fields;
}
