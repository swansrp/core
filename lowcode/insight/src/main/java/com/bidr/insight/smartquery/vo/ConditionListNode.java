package com.bidr.insight.smartquery.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ConditionListNode
 * Description: 智能问数表格筛选条件叶子节点，
 * 与前端 ConditionListType 叶子协议对齐（前端包一层 conditionList 后注入穿透表）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ConditionListNode {

    @ApiModelProperty(value = "字段属性名（字段目录 fields[].property）")
    private String property;

    @ApiModelProperty(value = "比较关系：1-等于/2-不等于/3-大于/4-大于等于/5-小于/6-小于等于/9-模糊匹配/11-在列表内/13-介于/15-包含")
    private Integer relation;

    @ApiModelProperty(value = "比较值（介于关系为[start,end]两项，在列表内为多值）")
    private List<String> value;

    @ApiModelProperty(value = "与上一条件的连接方式：0-且/1-或")
    private String andOr;
}
