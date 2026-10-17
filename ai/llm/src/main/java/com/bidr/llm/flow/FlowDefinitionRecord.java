package com.bidr.llm.flow;

import lombok.Data;

/**
 * Title: FlowDefinitionRecord
 * Description: 编排持久化记录——{@link FlowDefinitionStore} 的存取载体，graph 为 DAG 的 JSON 串
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class FlowDefinitionRecord {

    /**
     * 流程标识
     */
    private String flowKey;

    /**
     * 流程名称（自定义编排的显示名）
     */
    private String name;

    /**
     * DAG 定义 JSON 串（nodes+edges，含提示词模板与画布坐标）
     */
    private String graph;
}
