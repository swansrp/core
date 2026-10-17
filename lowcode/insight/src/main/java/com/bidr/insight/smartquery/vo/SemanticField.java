package com.bidr.insight.smartquery.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: SemanticField
 * Description: 语义目录中的筛选字段条目
 * （DATASET 模式取数据集列，其余模式取 portal 可筛选列）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SemanticField {

    @ApiModelProperty(value = "字段属性名（表格条件 property / 时间过滤 property）")
    private String property;

    @ApiModelProperty(value = "字段显示名")
    private String label;

    @ApiModelProperty(value = "语义化字段类型（text/enum/enum-multi/tree/tree-multi/number/money/percent/boolean/date/datetime/entity）")
    private String fieldType;

    @ApiModelProperty(value = "可选值域（enum/tree 取字典项，超长截断；模型取值必须用 value 而非 label）")
    private List<SemanticValue> values;

    @ApiModelProperty(value = "树字典编码（仅 tree/tree-multi 字段有值，自造 treeStackedBar 图表用）")
    private String dictName;

    @ApiModelProperty(value = "日期格式标识（DATETIME/YYYY-MM-DD/YYYYMMDD/YYYY-MM/YYYYMM/YYYY，仅日期字段有值）")
    private String dateFormat;

    @ApiModelProperty(value = "是否聚合列（仅 DATASET 模式有意义）")
    private Boolean aggregate;

    @ApiModelProperty(value = "是否敏感列（值域不外泄：列定义保留供指名查询，values 不下发）")
    private Boolean sensitive;

    @ApiModelProperty(value = "配对替换列属性名（如 项目名称→项目编号，跨轮/批量子集查询改用它做条件）")
    private String replaceProperty;

    /**
     * 值域条目：value 是筛选条件实际生效值（字典 dictValue），label 是业务显示名
     */
    @Data
    public static class SemanticValue {

        @ApiModelProperty(value = "条件取值（字典 dictValue）")
        private String value;

        @ApiModelProperty(value = "业务显示名（字典 label）")
        private String label;

        public SemanticValue() {
        }

        public SemanticValue(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
