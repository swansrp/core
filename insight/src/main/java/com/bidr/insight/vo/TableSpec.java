package com.bidr.insight.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: TableSpec
 * Description: 单个表格生成物：复用 portal 穿透表（advance-condition 条件协议）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class TableSpec {

    @ApiModelProperty(value = "表格标题")
    private String title;

    @ApiModelProperty(value = "生成该表格的原因（中文一句话）")
    private String reason;

    @ApiModelProperty(value = "筛选条件（与 portal 穿透表 advance-condition 叶子条件一致）")
    private List<ConditionListNode> conditions;
}
