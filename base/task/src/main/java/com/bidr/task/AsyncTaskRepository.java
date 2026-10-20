package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 异步任务底盘的持久化口（M16 棒 2）：把「受理落库／阶段推进／抢占租约／终态收口」
 * 的全部表操作收到一处，多实例条件 UPDATE 语义（抢占当锁）也定死在这里。
 *
 * <p><b>执行侧写口的所有权谓词（M16 修复轮 A3）</b>：{@code updateStage/updatePending/
 * updateTerminal/updateFailed/updateExternal/claimExternal/clearExternal} 一律带
 * {@code owner = ? AND status = 'RUNNING'}——阶段体只能写回自己仍持有着的行。租约过期被
 * 回收后迟到的写会更新 0 行、返回 {@code false}，调用方据此「不写内存行、不通知观察者」，
 * 避免把已回收的行写成 {@code RUNNING + owner/lease 全空} 的永久僵尸（三条扫描全漏），
 * 也避免同一行发两次终态通知（D12「DEAD 只发一次」）。</p>
 *
 * <p><b>受理侧写口不受所有权约束</b>：{@link #updateRequeue}（人工重试可作用于终态行）、
 * {@link #updateAbort}（用户当场作废，行可能还是 PENDING＝owner 空）与
 * {@link #sealExternal}（受理侧终态封存，终态行 owner 已清）按 id＋状态集合判定。</p>
 *
 * <p>生产实现＝{@code MybatisAsyncTaskRepository}（MyBatis-Plus 条件 UPDATE，照现网
 * 跑批原型照抄）；离线测试用内存实现驱动同一套底盘——判据①「测试代码零次手工 update」
 * 的机器形态即以此口为准。🔴 生产仓储的 SQL 形状未做真库实测（R-6），语义由内存实现的
 * 同一套谓词契约腿覆盖。</p>
 *
 * @author sharp
 */
public interface AsyncTaskRepository {

    /** 落一行新任务（id 回填；幂等冲突由 {@code (task_type, biz_ref)} 唯一键把守） */
    SaAsyncTask insert(SaAsyncTask task);

    /** 按主键取行（无则 null） */
    SaAsyncTask findById(Long id);

    /** 按幂等键取行（无则 null）：同键再 submit＝同一行的寻址正源 */
    SaAsyncTask findByBizKey(String taskType, String bizRef);

    /**
     * 按幂等键批量取行（列表页用；M16 棒 4 纯加法读口）：同键只留 id 最大者
     * （与 {@link #findByBizKey} 的 orderByDesc＋limit 1 同口径）。
     *
     * @return biz_ref → 最新任务行（无任务的键不出现；入参空＝空表）
     */
    Map<String, SaAsyncTask> findByBizKeys(String taskType, Collection<String> bizRefs);

    /**
     * 本人最近任务行（跨类型、按 {@code create_by} 硬绑、新→旧截断；M16 棒 4 纯加法读口）。
     * 不筛空间：任务归属「这个人发起的活儿」，业务坐标随 {@code payload} 由调用方当信息列展示。
     */
    List<SaAsyncTask> listRecentByCreator(String createBy, int limit);

    /**
     * 同类型当前排队位次（1 起；非排队态返回 0；M16 棒 4 纯加法读口）。排队判据与
     * {@link #countClaimable} 同一份（PENDING/FAILED、到了执行时间、租约空或已过期、id 升序），
     * 所以「第 N 位」就是「再等 N-1 件就轮到我」。
     */
    int queuePosition(Long id, String taskType, Date now);

    /**
     * 同类型全部可抢占行的 id（升序；M16 修复轮 B5 批量读口）：列表页一次取回、内存算位次，
     * 不再逐行 COUNT。
     */
    List<Long> claimableIds(String taskType, Date now);

    /** 本实例名下在途任务（RUNNING、租约未过期、且退避已到）：跨 tick 续跑，不参与抢占 */
    List<SaAsyncTask> ownedRunning(String taskType, String owner, int limit);

    /**
     * 抢占待执行任务（PENDING/FAILED 且到了执行时间、租约已空或过期）。
     * 条件 UPDATE 当锁：两实例同时看中同一行，只有一个能改掉 status，另一个更新 0 行自然让出。
     */
    List<SaAsyncTask> claimBatch(String taskType, String owner, Date leaseExpire, int limit);

    /** 续租：仅当仍为本实例持有且 RUNNING 才生效；false＝已易主（本次别再写任务行） */
    boolean renew(Long id, String owner, Date leaseExpire);

    /**
     * 租约已过期的 RUNNING 行（执行实例失联；回收入口）。{@code lease_expire IS NULL} 的
     * RUNNING 行同样回收——历史遗留的「僵尸行」（owner/lease 全空）三条扫描里只有这一条能看见它。
     */
    List<SaAsyncTask> expiredRunning(String taskType, Date now, int limit);

    /**
     * 阶段推进（执行侧写口，owner＋RUNNING 谓词）：stage 落库、保持 RUNNING 与租约不动；
     * false＝所有权已丢（行被回收/易主），调用方不得写内存行。
     */
    boolean updateStage(Long id, String owner, String stage);

    /**
     * 原地暂缓（执行侧写口，owner＋RUNNING 谓词）：stage 不动，退避到 {@code nextRunAt}；
     * 租约同时延到 {@code leaseExpire}（调用方给 {@code max(leaseMs, backoff)}——退避比租约长时
     * 不许被补偿扫描当成失联回收）。
     */
    boolean updatePending(Long id, String owner, Date nextRunAt, Date leaseExpire);

    /**
     * 终态收口（执行侧写口，owner＋RUNNING 谓词）：SUCCESS/DEAD＋stage＋error＋attempt 落库，
     * 并清空调度列（{@code next_run_at} 必须真清空：留着退避时间会让人误判「它还会自己再跑一次」）。
     * false＝行已易主或已收口：终态通知只在返回 true 后发（D12「DEAD 只发一次」）。
     */
    boolean updateTerminal(Long id, String owner, String status, String stage, String error, Integer attempt);

    /**
     * 可重试失败（执行侧写口，owner＋RUNNING 谓词）：FAILED＋error＋attempt＋退避时间，
     * 并放掉租约（owner/lease 清空，重试不等租约自然过期）。
     */
    boolean updateFailed(Long id, String owner, String error, int attempt, Date nextRunAt);

    /**
     * 重新入队（受理侧口，不受所有权约束：人工重试可作用于终态行）：回首阶段＋PENDING＋attempt 清零，
     * 并清 error/外部观测/owner/租约（外部任务号必须清：留着它，后续阶段会去轮询那个已失效的外部任务）。
     */
    boolean updateRequeue(Long id, String firstStage);

    /**
     * 受理侧当场作废（用户主动取消）：{@code id + status IN (PENDING,RUNNING,FAILED)} → DEAD，
     * 清 owner/lease/next_run_at。PENDING 行没有 owner，所以这条口不带所有权谓词。
     */
    boolean updateAbort(Long id, String error);

    /** 外部观测写入（执行侧写口，owner＋RUNNING 谓词）：external_id／status／快照（快照传 null 表示不动该列） */
    boolean updateExternal(Long id, String owner, String externalId, String externalStatus, String snapshotJson);

    /**
     * 乐观认领外部观测（执行侧写口，owner＋RUNNING 谓词）：仅当 {@code external_status} 为 NULL 或
     * 不等于 {@code notStatus} 时才写入；返回 false＝已被别人认领（不做任何写入）。
     */
    boolean claimExternal(Long id, String owner, String externalStatus, String snapshotJson, String notStatus);

    /**
     * 受理侧终态封存（M16 修复轮 A3.4，不要求 owner）：「任务已终态后补落对账标记」的 CAS——
     * 仅当 {@code status IN (SUCCESS,DEAD)} 且 {@code external_status} 为空或不等于封存值时写入；
     * false＝行非终态或已封过同值（CAS 输家，不做任何写入）。FAILED 不在终态集：
     * requeue 会清 external_status，封了也会被洗掉。
     */
    boolean sealExternal(Long id, String externalStatus);

    /**
     * 清外部观测（执行侧写口，owner＋RUNNING 谓词）：external_id/status/snapshot 三列置空——
     * 阶段失败要重提交新外部单前必须清，否则自动重试还在轮询那张已失效的单。
     */
    boolean clearExternal(Long id, String owner);

    /** 物理删除一条任务行（受理侧清理口，按类型＋幂等键；幂等，返回删除行数） */
    int deleteByBizKey(String taskType, String bizRef);

    /** 提交闸门的排队数：同类型可被抢占（PENDING/FAILED、到了执行时间、租约空或过期）的行数 */
    long countClaimable(String taskType, Date now);
}
