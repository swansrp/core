package com.bidr.task;

import lombok.Data;

/**
 * 异步任务进度快照（M16 棒 2）：受理/展示侧轮询的数据源（无任务行返回 0% 空态，不抛）。
 *
 * @author sharp
 */
@Data
public class AsyncTaskProgress {

    /** 任务类型 */
    private String taskType;

    /** 业务参照（幂等键后半；前端轮询收它不收任务 id） */
    private String bizRef;

    /** 当前阶段标识（无任务行为 null） */
    private String stage;

    /** 当前阶段展示名（未配 stageEnum 的类型为 null） */
    private String stageLabel;

    /** 进度百分比 0-100（按阶段 order 推导；SUCCESS 恒 100） */
    private Integer progress;

    /** 任务状态（PENDING/RUNNING/SUCCESS/FAILED/DEAD；无任务行为 null） */
    private String status;

    /** 最近错误 */
    private String error;

    /** 已尝试次数 */
    private Integer attempt;
}
