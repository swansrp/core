package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;

/**
 * 阶段作用域（M16 棒 2）：切面注入到阶段方法的第二参数——业务在阶段体内向底盘要的四个写口。
 *
 * <p>🔴 只做「写穿到通用列」，不提供任何内存/ThreadLocal 跨阶段载体（纪律 §4.2.4-④：
 * 那正是「进程重启就从头再来」要防的东西）；跨阶段要传结构就经对象存储拿摘要串放
 * {@code payload}/{@code external_id}。</p>
 *
 * @author sharp
 */
public final class AsyncStageScope {

    private final AsyncTaskService taskService;

    private final SaAsyncTask task;

    AsyncStageScope(AsyncTaskService taskService, SaAsyncTask task) {
        this.taskService = taskService;
        this.task = task;
    }

    /** 外部观测留痕（如外部引擎的子状态快照）：切面统一写 external_status/external_snapshot */
    public void snapshot(String externalStatus, String snapshotJson) {
        taskService.writeExternal(task, null, externalStatus, snapshotJson);
    }

    /**
     * 乐观认领外部观测（同一件事只让一个人做的条件 UPDATE 锁）：仅当 {@code external_status}
     * 为 NULL 或不等于 {@code notStatus} 时才写入；没抢到返回 false、什么都不写，调用方整段跳过。
     * 🔴 幂等锁是业务动作（R-11 白名单词），切面只提供这个条件写口。
     */
    public boolean claimExternal(String externalStatus, String snapshotJson, String notStatus) {
        return taskService.claimExternal(task, externalStatus, snapshotJson, notStatus);
    }

    /** 外部任务 id 写入（{@code external_id}＝跨阶段载体二；阶段推进间靠它对在途账） */
    public void externalId(String externalId) {
        taskService.writeExternal(task, externalId, null, null);
    }

    /**
     * 清外部观测（M16 修复轮 A8）：<b>要重提交新外部单的抛出点先清再抛</b>——
     * 引擎报 FAILED、等待超预算这两类失败会走自动重试，external_id 留着的话下一轮仍去轮询
     * 那张已失效的单，「重提交新单」永远不会发生。清完重试会走 submit 分支。
     */
    public void releaseExternal() {
        taskService.clearExternal(task);
    }
}
