package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ChatBiSpec
 * Description: 智能问数生成物协议——大模型输出的轻量图表/表格编排指令，
 * 前端据此合并语义层完整配置后复用现有 ChartCard / Portal 表格渲染
 *
 * @author Sharp
 * @since 2026/8/14
 */
@Data
public class ChatBiSpec {

    @ApiModelProperty(value = "图表生成物列表")
    private List<ChartSpec> charts;

    @ApiModelProperty(value = "表格生成物列表")
    private List<TableSpec> tables;
}
