package com.bidr.llm.flow;

import java.util.List;

/**
 * Title: FlowDefinitionVersionStore
 * Description: 流程定义版本化 SPI——业务模块提供存储实现（如落关系表）后，
 * 编排可按版本保存与回溯运行：每次保存产生一个不可变版本号（或覆盖指定版本），
 * {@link FlowManagerService#getFlowVersion} / {@link FlowManagerService#runFlowVersion}
 * 据此读取历史版本。与既有 {@link FlowDefinitionStore}（无版本维度的"当前生效"编排）
 * 相互独立、可并存：当前生效编排走旧 SPI，历史版本走本 SPI，互不干扰。
 * <p>
 * 版本号约定：正整数递增（{@link #latestVersion} 返回 0 表示尚无版本）；
 * 无实现的应用版本化能力整体关闭（管理服务按"未启用"明确报错，不静默回落）。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
public interface FlowDefinitionVersionStore {

    /**
     * 某流程的全部版本号（升序）；无版本返回空清单
     */
    List<Integer> versions(String flowKey);

    /**
     * 读指定版本记录；版本不存在返回 null
     */
    FlowDefinitionRecord load(String flowKey, int version);

    /**
     * 保存版本（graph 为该版本的 DAG 定义 JSON 串；同一版本号重复保存由实现方决定覆盖或报错）
     */
    void save(String flowKey, String name, String graphJson, int version);

    /**
     * 最新版本号；无版本返回 0
     */
    int latestVersion(String flowKey);
}
