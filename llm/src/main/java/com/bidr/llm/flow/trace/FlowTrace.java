package com.bidr.llm.flow.trace;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: FlowTrace
 * Description: 流程执行轨迹模型（Redis 按访问人保留，直接序列化给管理页调试界面）——
 * flow 调试的核心反馈回路：一次 execute 连同挂起续跑归并为一条 {@link TraceRecord}
 * （流式模型回调线程 resume 沿用同一 traceId），每个结点执行记一条 {@link NodeEvent}。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public class FlowTrace {

    @Data
    public static class TraceRecord {

        /**
         * 轨迹标识（UUID，挂起续跑沿用）
         */
        private String traceId;

        /**
         * 流程标识（已注册的 flowKey）
         */
        private String flowKey;

        /**
         * 开始时间（epoch millis）
         */
        private Long startTime;

        /**
         * 结束时间（running 态为 null）
         */
        private Long endTime;

        /**
         * running | success | error
         */
        private String status;

        /**
         * 用户问题摘要（截断 200 字）
         */
        private String question;

        /**
         * 访问人（发起本次执行的操作员标识，免登场景回落 anonymous）
         */
        private String operator;

        /**
         * true=本次执行用的是内置默认链（库中无自定义或非法回落）
         */
        private Boolean builtin;

        /**
         * 失败原因（status=error 时）
         */
        private String error;

        /**
         * 结点事件（按执行顺序；列表视图不带，全文走详情）
         */
        private List<NodeEvent> nodes = new ArrayList<>();
    }

    @Data
    public static class NodeEvent {

        private String nodeId;

        private String type;

        private String name;

        /**
         * ok | skipped | error
         */
        private String status;

        private long elapsedMs;

        /**
         * 变量摘要（一行：模型输出字数/提取结果/目录条数等）
         */
        private String summary;

        /**
         * 调试全文（llm 渲染后提示词与模型回答、提取输入与结果等；其余类型为空）
         */
        private String detail;
    }
}
