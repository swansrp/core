package com.bidr.llm.flow;

import lombok.Data;

/**
 * Title: FlowDetailRes
 * Description: 编排详情响应（管理页画布数据源）——builtin=true 表示内置默认链
 * （库中无自定义或非法回落），graph 永远合法可用，保存即修复坏记录。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class FlowDetailRes {

    /**
     * 流程标识（已注册的 flowKey）
     */
    private String flowKey;

    /**
     * 流程名称（自定义编排的保存名 / 内置链的注册显示名）
     */
    private String name;

    /**
     * true=内置默认链（库中无自定义或非法回落）
     */
    private Boolean builtin;

    /**
     * DAG 定义（nodes+edges，含提示词模板与画布坐标）
     */
    private FlowGraph graph;
}
