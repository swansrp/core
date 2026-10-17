package com.bidr.insight.chatbi.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: SemanticIndicatorGroup
 * Description: 语义目录中的 indicator 筛选组条目（sys_portal_indicator_group + 组下 indicator 项）——
 * 用户口语筛选（如"华北区域"）的落点：命中项的 conditions 即前端筛选提交的叶子条件，
 * 大模型将其原样复制进 chart-spec 的 tables[].conditions
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SemanticIndicatorGroup {

    @ApiModelProperty(value = "组名（如：区域）")
    private String title;

    @ApiModelProperty(value = "组下可选项")
    private List<Item> items;

    /**
     * indicator 项（如：华北），conditions 是该项对应的完整叶子条件集
     */
    @Data
    public static class Item {

        @ApiModelProperty(value = "项名（用户口语指向，如：华北）")
        private String title;

        @ApiModelProperty(value = "项标识（sys_portal_indicator.item_value）")
        private String key;

        @ApiModelProperty(value = "命中该项时应满足的叶子条件（原样复制进 chart-spec）")
        private List<Condition> conditions;
    }

    /**
     * condition JSON（{"conditionList":[{property,relation,value}]}）解析出的叶子条件
     */
    @Data
    public static class Condition {

        @ApiModelProperty(value = "字段属性名")
        private String property;

        @ApiModelProperty(value = "关系（与 chart-spec relation 同义：1-等于 11-在列表内 …）")
        private Integer relation;

        @ApiModelProperty(value = "条件取值")
        private List<String> value;
    }
}
