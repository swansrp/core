package com.bidr.llm.flow;

import lombok.Data;

/**
 * Title: FlowSaveReq
 * Description: 编排保存请求——画布保存自定义编排的入参；flowKey 必须是已注册的封闭集成员
 * （{@link FlowManagerService} 校验），name 缺省回落注册显示名。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class FlowSaveReq {

    /**
     * 流程标识（已注册的 flowKey）
     */
    private String flowKey;

    /**
     * 流程名称（空则回落注册显示名）
     */
    private String name;

    /**
     * DAG 定义（引擎结构校验通过后落库）
     */
    private FlowGraph graph;
}
