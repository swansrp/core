package com.bidr.llm.agent.session;

import lombok.Data;

import java.io.Serializable;

/**
 * Title: AgentStage
 * Description: 自主 agent 会话执行阶段（阶段状态组件数据源）——业务经
 * {@link AgentSessionContext#defineStages} 声明清单后随执行推进逐段上报
 * （start→done/fail/skip），status 快照与 stage 事件双通道落 store：
 * 前者供 AgentStages 全量渲染，后者供 2s 轮询实时跳动。
 * 未执行段由会话层终态收口统一补齐（STOPPED→stopped / FAILED→error）
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Data
public class AgentStage implements Serializable {

    /** 待执行（声明即 pending） */
    public static final String PENDING = "pending";

    /** 执行中（startedAt 已打戳） */
    public static final String RUNNING = "running";

    /** 完成（可选 detail 摘要，如「实体 12 / 维度 8」） */
    public static final String OK = "ok";

    /** 失败（detail 携原因） */
    public static final String ERROR = "error";

    /** 跳过（如条件不满足直通） */
    public static final String SKIPPED = "skipped";

    /** 被停止（会话收口时对 running 段统一置位） */
    public static final String STOPPED = "stopped";

    /**
     * 阶段标识（业务自定义，上报定位用；同会话内唯一）
     */
    private String key;

    /**
     * 阶段显示名（前端步骤条文案）
     */
    private String title;

    /**
     * 阶段状态（本类常量）
     */
    private String status;

    /**
     * 开始时间（进入 running 打戳）
     */
    private Long startedAt;

    /**
     * 结束时间（ok/error/skipped/stopped 打戳）
     */
    private Long endedAt;

    /**
     * 阶段摘要/失败原因（ok 的成果摘要、error 的原因）
     */
    private String detail;

    /**
     * 预计耗时（秒，可空；AgentStages 估算剩余时间用）
     */
    private Integer estimatedSecs;

    public static AgentStage of(String key, String title) {
        return of(key, title, null);
    }

    public static AgentStage of(String key, String title, Integer estimatedSecs) {
        AgentStage stage = new AgentStage();
        stage.setKey(key);
        stage.setTitle(title);
        stage.setStatus(PENDING);
        stage.setEstimatedSecs(estimatedSecs);
        return stage;
    }
}
