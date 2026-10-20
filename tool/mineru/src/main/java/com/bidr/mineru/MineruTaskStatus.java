package com.bidr.mineru;

import lombok.Data;

/**
 * Title: MineruTaskStatus
 * Description: 两腿轮询的归一状态（提交返回的 taskId/batchId 都用它查进度）。
 * 状态词表统一为 PENDING / RUNNING / COMPLETED / FAILED 四值——自建腿的
 * pending/completed/failed 与公网腿的 done/failed/pending/waiting-file/running/converting
 * 各自归一进来，腿部私有的中间词落 {@link #phase} 供展示。
 *
 * @author Sharp
 * @since 2026/10/17
 */
@Data
public class MineruTaskStatus {

    public static final String PENDING = "PENDING";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    /** 归一四态之一 */
    private String status;

    /** 腿部私有中间词（自建的 backend、公网的 state 原词），仅展示用，可空 */
    private String phase;

    /** FAILED 时的原因（公网令牌过期 A0211 等；自建为 error 字段） */
    private String errorMessage;

    /** 自建腿排队中前方任务数（并发闸门用），公网腿无此概念，为 null */
    private Integer queueAhead;
}
