package com.bidr.llm.flow;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: FlowGraph
 * Description: DAG 编排定义模型——存库 JSON 的直接映射，
 * nodes 为结点清单（含提示词模板与画布坐标），edges 为有向连线（可带条件表达式）
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class FlowGraph {

    /**
     * 结点清单
     */
    private List<FlowNode> nodes = new ArrayList<>();

    /**
     * 有向连线清单
     */
    private List<FlowEdge> edges = new ArrayList<>();

    /**
     * Title: FlowNode
     * Description: 流程结点——type 决定执行器（Spring 容器内注册的封闭集），config 为执行器参数（提示词模板/变量名等），x/y 为画布坐标
     */
    @Data
    public static class FlowNode {
        /**
         * 结点 id（流程内唯一，画布连线引用）
         */
        private String id;

        /**
         * 结点类型（start/llm/output 及业务方扩展注册的类型，封闭集）
         */
        private String type;

        /**
         * 结点显示名（画布展示）
         */
        private String name;

        /**
         * 执行器参数（按 type 解释，如 llm 的 template/role/stream/outputVar）
         */
        private Map<String, Object> config = new LinkedHashMap<>();

        /**
         * 画布 x 坐标
         */
        private Double x;

        /**
         * 画布 y 坐标
         */
        private Double y;

        /**
         * 是否启用（false 时引擎跳过执行，控制流直通——沿出边继续）
         */
        private Boolean enabled = Boolean.TRUE;
    }

    /**
     * Title: FlowEdge
     * Description: 有向连线——condition 为空恒真；支持 "var == 'x'" / "var != 'x'" / "notEmpty(var)"
     */
    @Data
    public static class FlowEdge {
        /**
         * 起点结点 id
         */
        private String source;

        /**
         * 终点结点 id
         */
        private String target;

        /**
         * 条件表达式（空=恒真；格式 var == '值' / var != '值' / notEmpty(var)）
         */
        private String condition;
    }
}
