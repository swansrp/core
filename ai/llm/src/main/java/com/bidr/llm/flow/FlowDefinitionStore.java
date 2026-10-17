package com.bidr.llm.flow;

/**
 * Title: FlowDefinitionStore
 * Description: 编排持久化 SPI——业务模块提供存储实现（如落关系表）；无实现的应用引擎照常运行，
 * 只是编排不可自定义（始终用内置默认链）。读写只面向已注册的 flowKey（封闭集由上层校验）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public interface FlowDefinitionStore {

    /**
     * 读自定义编排记录；无记录返回 null
     */
    FlowDefinitionRecord load(String flowKey);

    /**
     * 保存自定义编排（graph 为 DAG 定义 JSON 串，无则插入有则覆盖由实现方决定）
     */
    void save(String flowKey, String name, String graphJson);

    /**
     * 删除自定义编排（重置回内置默认链；幂等，无记录同样成功）
     */
    void delete(String flowKey);
}
