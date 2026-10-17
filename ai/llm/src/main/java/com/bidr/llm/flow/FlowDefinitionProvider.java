package com.bidr.llm.flow;

/**
 * Title: FlowDefinitionProvider
 * Description: 流程定义提供方 SPI——业务模块为每条链注册一个实现（Spring Bean），
 * 声明归属 skill、流程标识、显示名与内置默认链（出厂兜底，编排库无自定义记录或记录非法时回落，
 * 管理页"重置"即回到此链）。这是注册新链的唯一途径：未注册的 flowKey 引擎拒识（封闭集）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public interface FlowDefinitionProvider {

    /**
     * 归属 skill 标识（skill 管理平台的索引维度：链清单/轨迹/评价按 skill 聚合展示，
     * 同一 skillCode 的实现归为一组；取值由业务方自约定，须保持稳定）
     */
    String skillCode();

    /**
     * 流程标识（全局唯一；重复注册时引擎启动即报错）
     */
    String flowKey();

    /**
     * 流程显示名（管理页标题与缺省编排名）
     */
    String displayName();

    /**
     * 内置默认链（提示词真源所在，须结构合法：含 start、无环、结点类型已注册）
     */
    FlowGraph defaultGraph();
}
