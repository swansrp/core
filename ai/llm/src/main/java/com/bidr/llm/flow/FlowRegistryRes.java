package com.bidr.llm.flow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: FlowRegistryRes
 * Description: skill 注册表——skill 工作台的启动数据源：skill 下的链清单（封闭集成员，
 * 画布链切换数据源）与画布可用结点类型元数据（schema 驱动 palette 与属性表单渲染，
 * 新结点类型只需执行器声明 {@link FlowNodeMeta}，前端零改动）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class FlowRegistryRes {

    /**
     * skill 标识（回显）
     */
    private String skillCode;

    /**
     * skill 下的链清单（flowKey 封闭集成员）
     */
    private List<FlowSummary> flows = new ArrayList<>();

    /**
     * 画布可用结点类型元数据（该 skill 各链当前生效 graph 出现过的类型并集）
     */
    private List<FlowNodeMeta> nodeTypes = new ArrayList<>();

    /**
     * Title: FlowSummary
     * Description: 链摘要（不含 graph——编排详情另查 FlowManagerService.getFlow）
     *
     * @author Sharp
     * @since 2026/8/16
     */
    @Data
    public static class FlowSummary {

        /**
         * 流程标识
         */
        private String flowKey;

        /**
         * 显示名
         */
        private String displayName;
    }
}
