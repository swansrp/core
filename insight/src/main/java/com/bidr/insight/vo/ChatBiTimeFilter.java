package com.bidr.insight.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatBiTimeFilter
 * Description: 时间范围过滤，前端转换为 BETWEEN 条件（relation=13, value=[start,end]）
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChatBiTimeFilter {

    @ApiModelProperty(value = "日期字段属性名（字段目录 fields[].property）")
    private String property;

    @ApiModelProperty(value = "开始值（含），格式与字段目录一致，如 2024-01-01")
    private String start;

    @ApiModelProperty(value = "结束值（含），如 2024-12-31")
    private String end;
}
