package com.bidr.task;

import com.bidr.kernel.exception.NoticeException;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 异步任务受理与记账底盘（M16 棒 2）。
 *
 * <p><b>受理侧＝异步注册，只落库不执行</b>（D15 无同步执行口）：{@link #submit}／{@link #begin}／
 * {@link #find}／{@link #requeue} 业务显式调用，幂等键 {@code (task_type, biz_ref)} 归这里——
 * 同键再 submit＝同一行；{@code submit} 返回的是<b>任务句柄，永远不含业务产物</b>。
 * 落库后发一次 {@link TaskWakeSignal} 进程内信号（加速器，轮询兜底）。</p>
 *
 * <p><b>提交闸门</b>：yml {@code base.task.max-queue-ahead}（只读 Spring Environment，R-9）——
 * 同类型可抢占排队数超过阈值即拒收；🔴 取不到排队数（查询失败）放行，闸门不得误杀受理。</p>
 *
 * <p><b>记账口</b>（advance/pending/succeed/fail/dead）由切面与跑批调用——执行侧阶段体内
 * 手写这些调用属 R-11 禁词，业务代码只返回 {@link StageOutcome}。终态两档
 * （SUCCESS/DEAD）触发 {@link AsyncTaskObserver} 链（逐件 try-catch，观察者异常不改任务终态）。</p>
 *
 * <p>🔴 <b>所有权语义（M16 修复轮 A3）</b>：执行侧写口全部带 {@code owner + status='RUNNING'}
 * 谓词，写失败＝本次已丢失所有权（行被回收/易主）——此时<b>不写内存行、不通知观察者</b>，
 * 只记一条 WARN；丢失所有权＝进度不写回，不是业务失败。终态通知只在写成功之后发（D12
 * 「DEAD 只发一次」）。受理侧的「用户当场作废」走 {@link #abort}（不要求 owner）。</p>
 *
 * @author sharp
 */
@Service
public class AsyncTaskService {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskService.class);

    /** 失败退避（毫秒）：可重试失败的下一次可执行时间 */
    private static final long FAIL_BACKOFF_MS = 60_000L;

    private final AsyncTaskRepository repository;

    private final TaskFlowRegistry registry;

    private final TaskWakeSignal wakeSignal;

    /** 提交闸门：同类型可抢占排队数超过它即拒收（取不到排队数放行） */
    private final int maxQueueAhead;

    private final List<AsyncTaskObserver> observers;

    private final TaskContextBridge bridge;

    @Autowired
    public AsyncTaskService(AsyncTaskRepository repository, TaskFlowRegistry registry,
                            TaskWakeSignal wakeSignal, TaskContextBridge bridge,
                            List<AsyncTaskObserver> observers,
                            @Value("${base.task.max-queue-ahead:1000}") int maxQueueAhead) {
        this.repository = repository;
        this.registry = registry;
        this.wakeSignal = wakeSignal;
        this.bridge = bridge;
        this.observers = observers == null ? Collections.emptyList() : observers;
        this.maxQueueAhead = maxQueueAhead;
    }

    // ==================== 受理侧（业务显式调用；只落库不执行） ====================

    /**
     * 提交（异步注册）：无同键行则落一行 PENDING（首阶段、attempt=0），有则返回既有行
     * （幂等键 {@code (task_type, biz_ref)}：同键再 submit＝同一行，不新建、不改状态）。
     * 新落行后发一次进程内唤醒信号。
     *
     * <p>并发撞唯一键（内容寻址键如 {@code material:{业务维度}:{blobId}} 必撞）时重读返回既有行：
     * find-then-insert 之间的竞态由 {@code uk_type_biz} 最终裁决，不向上抛 500。</p>
     *
     * @param taskType 任务类型（须已注册的流程类型）
     * @param bizRef   业务参照（幂等键后半；版本/对象变即换串＝新任务新行）
     * @param payload  业务入参 JSON（跨阶段载体一；可空）
     * @param operator 操作人工号（落 {@code create_by}＝上下文重建原料；可空）
     * @return 任务句柄（含 id 与状态；永远不含业务产物）
     */
    public SaAsyncTask submit(String taskType, String bizRef, String payload, String operator) {
        TaskFlowRegistry.FlowDef flow = registry.require(taskType);
        SaAsyncTask existed = repository.findByBizKey(taskType, bizRef);
        if (existed != null) {
            return existed;
        }
        gate(taskType);
        SaAsyncTask task = new SaAsyncTask();
        task.setTaskType(taskType);
        task.setBizRef(bizRef);
        task.setPayload(payload);
        task.setStage(flow.firstStage().getValue());
        task.setStatus(AsyncTaskStatus.PENDING.name());
        task.setAttempt(0);
        task.setCreateBy(operator);
        try {
            repository.insert(task);
        } catch (DuplicateKeyException e) {
            // 并发同键：唯一键是最终裁决者，重读返回既有行（不改成 INSERT IGNORE：会丢 id 回填语义）
            SaAsyncTask raced = repository.findByBizKey(taskType, bizRef);
            if (raced == null) {
                throw e;
            }
            log.info("任务并发提交撞幂等键，返回既有行: type={} task={} bizRef={}", taskType, raced.getId(), bizRef);
            return raced;
        }
        wakeSignal.wake(taskType);
        return task;
    }

    /**
     * 取或建（不抢占不执行）：无同键行则与 {@link #submit} 同形落库并唤醒；
     * 有行（含终态）原行返回、状态不动——重跑正源是 {@link #requeue}，不在受理侧。
     */
    public SaAsyncTask begin(String taskType, String bizRef, String payload, String operator) {
        return submit(taskType, bizRef, payload, operator);
    }

    /** 按幂等键取任务行（无则 null；前端轮询/句柄找回都走这里，收 biz_ref 不收 id） */
    public SaAsyncTask find(String taskType, String bizRef) {
        return repository.findByBizKey(taskType, bizRef);
    }

    /**
     * 按幂等键批量取任务行（列表页用，免逐键 N+1；M16 棒 4 纯加法读口）：
     * biz_ref → 最新任务行，无任务的键不出现。
     */
    public Map<String, SaAsyncTask> findByBizRefs(String taskType, Collection<String> bizRefs) {
        return repository.findByBizKeys(taskType, bizRefs);
    }

    /**
     * 本人最近任务行（跨类型、create_by 硬绑、新→旧截断；M16 棒 4 纯加法读口）——
     * 「后台任务」个人视图的 sa_async_task 侧数据源（双轨期与 engine_task 合并读）。
     */
    public List<SaAsyncTask> listRecentByCreator(String createBy, int limit) {
        return repository.listRecentByCreator(createBy, limit);
    }

    /**
     * 同类型当前排队位次（1 起；非排队态 0；M16 棒 4 纯加法读口）——「排队第 N 位」展示口径，
     * 排队判据与提交闸门同一份。M16 修复轮 B5 起内部改走批量读口 {@link #claimableIds}：
     * 一次取回全量 claimable id、内存换算位次，不再逐行 COUNT。
     */
    public int queuePosition(SaAsyncTask task) {
        if (task == null || task.getId() == null) {
            return 0;
        }
        return positionIn(repository.claimableIds(task.getTaskType(), new Date()), task.getId());
    }

    /**
     * 位次换算（纯函数口；M16 修复轮 B5）：「claimable id 升序列表」→ 某行的排队位次＝
     * 列表里 {@code id <= 它} 的行数——与逐行 COUNT 的 {@code id <= ?} 口径逐字等价
     * （行自身不在列表——已被抢占/退避中——时，排在它前面的 claimable 行数照算）。
     * 列表页批量算位次与 {@link #queuePosition} 单行走同一把尺，两处不漂。
     */
    public static int positionIn(List<Long> claimableIdsAsc, Long id) {
        if (id == null) {
            return 0;
        }
        int position = 0;
        for (Long claimableId : claimableIdsAsc) {
            if (claimableId > id) {
                break;
            }
            position++;
        }
        return position;
    }

    /**
     * 同类型全部可抢占行 id（升序；M16 修复轮 B5 批量读口）——列表页一次性取回、
     * 内存算每行位次，不再逐行 COUNT。
     */
    public List<Long> claimableIds(String taskType) {
        return repository.claimableIds(taskType, new Date());
    }

    /** 按主键取任务行（无则 null） */
    public SaAsyncTask findById(Long id) {
        return repository.findById(id);
    }

    /**
     * 人工重试入队（受理侧动作）：回首阶段＋PENDING＋attempt 清零，清 error/外部观测/owner/租约。
     * 只改库不执行：任务由跑批在下一 tick 抢走。
     */
    public void requeue(String taskType, String bizRef) {
        TaskFlowRegistry.FlowDef flow = registry.require(taskType);
        SaAsyncTask task = repository.findByBizKey(taskType, bizRef);
        if (task == null) {
            throw new NoticeException("任务不存在（type=" + taskType + " bizRef=" + bizRef + "），无法重新入队");
        }
        repository.updateRequeue(task.getId(), flow.firstStage().getValue());
        wakeSignal.wake(taskType);
        log.info("任务已重新入队（人工重试）: type={} task={} bizRef={}", taskType, task.getId(), bizRef);
    }

    /**
     * 受理侧清理（M16 修复轮 A9）：按类型＋幂等键物理删任务行，幂等。
     * 用于删除业务对象时顺手收口它的任务行（在途行删后 runner 的写口因行不存在而失败并被忽略）。
     *
     * @return 删除行数
     */
    public int discard(String taskType, String bizRef) {
        if (!StringUtils.hasText(taskType) || !StringUtils.hasText(bizRef)) {
            return 0;
        }
        int removed = repository.deleteByBizKey(taskType, bizRef);
        if (removed > 0) {
            log.info("任务行已清理（业务对象删除）: type={} bizRef={} 行数={}", taskType, bizRef, removed);
        }
        return removed;
    }

    /**
     * 受理侧终止（用户当场作废；M16 修复轮 A3）：谓词 {@code id + status IN (PENDING,RUNNING,FAILED)}，
     * 置 DEAD 并清 owner/lease/next_run_at——PENDING 行没有 owner，所以这条口<b>不受所有权约束</b>。
     * 写成功才发一次 DEAD 通知。
     *
     * @return true＝本次确实作废了一行
     */
    public boolean abort(SaAsyncTask task, String error) {
        if (task == null || task.getId() == null) {
            return false;
        }
        boolean aborted = repository.updateAbort(task.getId(), error);
        if (!aborted) {
            log.debug("受理侧作废未生效（行已终态或不存在）: task={} status={}", task.getId(), task.getStatus());
            return false;
        }
        task.setStatus(AsyncTaskStatus.DEAD.name());
        task.setError(error);
        task.setOwner(null);
        task.setLeaseExpire(null);
        task.setNextRunAt(null);
        log.info("任务已被受理侧作废: type={} task={} bizRef={} 原因={}",
                task.getTaskType(), task.getId(), task.getBizRef(), error);
        notifyTerminal(task, AsyncTaskStatus.DEAD);
        return true;
    }

    /**
     * 受理侧终态封存（M16 修复轮 A3.4）：材料回收这类「任务已终态后补落对账标记」的 CAS——
     * 谓词 {@code id + status IN (SUCCESS,DEAD) + external_status 为空或≠封存值}，无 owner 限
     * （终态行 owner 已清，与 {@link #abort} 同族不受所有权约束）。FAILED 不进终态集：
     * requeue 会清 external_status，封了也会被洗掉。CAS 语义：已封过同值＝false，
     * 输家分支据此静默（不重复发对账副作用）。
     *
     * @return true＝本次确实落上了封存标记
     */
    public boolean sealExternal(SaAsyncTask task, String externalStatus) {
        if (task == null || task.getId() == null) {
            return false;
        }
        boolean sealed = repository.sealExternal(task.getId(), externalStatus);
        if (!sealed) {
            log.debug("终态封存未生效（行非终态或已封过）: task={} status={}", task.getId(), task.getStatus());
            return false;
        }
        task.setExternalStatus(externalStatus);
        return true;
    }

    /** 进度快照（无任务行返回 0% 空态，不抛——前端首帧即可调用） */
    public AsyncTaskProgress progress(String taskType, String bizRef) {
        SaAsyncTask task = repository.findByBizKey(taskType, bizRef);
        if (task == null) {
            AsyncTaskProgress empty = new AsyncTaskProgress();
            empty.setTaskType(taskType);
            empty.setBizRef(bizRef);
            empty.setProgress(0);
            return empty;
        }
        return progressOf(task);
    }

    /**
     * 进度快照（纯函数口；M16 修复轮 A5）：只用传入的任务行＋注册表内存推导，
     * <b>零额外查询</b>——列表页已批量取过任务行，逐条再查一次就是 N+1。
     * 无行/未注册类型返回 0% 空态不抛。
     */
    public AsyncTaskProgress progressOf(SaAsyncTask task) {
        AsyncTaskProgress progress = new AsyncTaskProgress();
        if (task == null) {
            progress.setProgress(0);
            return progress;
        }
        progress.setTaskType(task.getTaskType());
        progress.setBizRef(task.getBizRef());
        TaskFlowRegistry.FlowDef flow = registry.find(task.getTaskType());
        if (flow == null) {
            progress.setProgress(0);
            return progress;
        }
        progress.setStage(task.getStage());
        TaskFlowRegistry.StageDef stage = flow.stageOf(task.getStage());
        progress.setStageLabel(stage == null ? null : stage.getLabel());
        progress.setProgress(AsyncTaskStatus.SUCCESS.name().equals(task.getStatus())
                ? 100 : flow.progressOf(task.getStage()));
        progress.setStatus(task.getStatus());
        progress.setError(task.getError());
        progress.setAttempt(task.getAttempt());
        return progress;
    }

    /** 提交闸门：同类型排队数超阈即拒收；🔴 取不到排队数（查询失败）放行 */
    private void gate(String taskType) {
        if (maxQueueAhead < 0) {
            return;
        }
        long ahead;
        try {
            ahead = repository.countClaimable(taskType, new Date());
        } catch (Exception e) {
            log.warn("提交闸门取排队数失败，放行: type={} {}", taskType, e.getMessage());
            return;
        }
        if (ahead >= maxQueueAhead) {
            throw new NoticeException("任务排队过多（type=" + taskType + " 排队 " + ahead
                    + " 已达上限 " + maxQueueAhead + "），请稍后再试");
        }
    }

    // ==================== 记账口（切面与跑批调用；R-11：业务阶段体禁手写） ====================

    /**
     * 阶段推进：只许原地或前进——目标 order 小于当前 order 当场抛（进度百分比单调的不变量）；
     * {@code to(当前阶段)} 合法（提交完把当前档再写一遍的形状）。RUNNING 与租约不动。
     *
     * <p>结构错（目标阶段缺失/禁回跳）抛 {@link StageContractException}：属<b>代码契约错误</b>、
     * 重试无意义，由切面统一置 DEAD（不给补偿扫描反复重跑同一阶段体的机会）。</p>
     *
     * <p>真迁移（目标 ≠ 当前）写库成功后发一次 {@link AsyncTaskObserver#onStage}——原地推进
     * 与丢失所有权都不发：原地是常态路径（外部引擎腿轮询每轮原地一遍），发了＝每 tick 轰一次
     * 观察者；丢失所有权与终态语义对称（进度不写回即无迁移可言）。通知在内存行更新之后发，
     * 观察者看到的是已迁移后的行。</p>
     */
    public void advance(SaAsyncTask task, TaskFlowRegistry.FlowDef flow, String targetStage) {
        TaskFlowRegistry.StageDef target = flow.stageOf(targetStage);
        if (target == null) {
            throw new StageContractException("目标阶段不在流程清单内: type=" + flow.getType()
                    + " stage=" + targetStage);
        }
        TaskFlowRegistry.StageDef current = flow.stageOf(task.getStage());
        if (current != null && target.getOrder() < current.getOrder()) {
            throw new StageContractException("阶段禁回跳: type=" + flow.getType()
                    + " from=" + task.getStage() + "(order=" + current.getOrder() + ")"
                    + " to=" + targetStage + "(order=" + target.getOrder() + ")");
        }
        String fromStage = task.getStage();
        if (!repository.updateStage(task.getId(), task.getOwner(), targetStage)) {
            warnLostOwnership(task, "advance->" + targetStage);
            return;
        }
        task.setStage(targetStage);
        task.setStatus(AsyncTaskStatus.RUNNING.name());
        if (!targetStage.equals(fromStage)) {
            notifyStage(task, fromStage, targetStage);
        }
    }

    /**
     * 本轮暂缓：stage 原地不动，退避 {@code backoffMs} 毫秒（owner 保持，本实例下轮再来）；
     * 租约同时延到 {@code now + max(flow.leaseMs, backoffMs)}——退避比租约长时不许被补偿扫描
     * 当成失联回收（否则退避被清、attempt +1、失败原因写成「实例失联」）。
     */
    public void pending(SaAsyncTask task, TaskFlowRegistry.FlowDef flow, long backoffMs) {
        long backoff = Math.max(0L, backoffMs);
        Date now = new Date();
        Date nextRunAt = new Date(now.getTime() + backoff);
        Date leaseExpire = new Date(now.getTime() + Math.max(flow.getLeaseMs(), backoff));
        if (!repository.updatePending(task.getId(), task.getOwner(), nextRunAt, leaseExpire)) {
            warnLostOwnership(task, "pending");
            return;
        }
        task.setNextRunAt(nextRunAt);
        task.setLeaseExpire(leaseExpire);
    }

    /** 外部观测写入（阶段作用域口）：externalId/externalStatus/snapshot 传 null 表示不动该列 */
    public void writeExternal(SaAsyncTask task, String externalId, String externalStatus, String snapshotJson) {
        if (!repository.updateExternal(task.getId(), task.getOwner(), externalId, externalStatus, snapshotJson)) {
            warnLostOwnership(task, "external");
            return;
        }
        if (externalId != null) {
            task.setExternalId(externalId);
        }
        if (externalStatus != null) {
            task.setExternalStatus(externalStatus);
        }
        if (snapshotJson != null) {
            task.setExternalSnapshot(snapshotJson);
        }
    }

    /** 乐观认领外部观测（阶段作用域口）：条件 UPDATE 锁，没抢到什么都不写 */
    public boolean claimExternal(SaAsyncTask task, String externalStatus, String snapshotJson, String notStatus) {
        boolean claimed = repository.claimExternal(task.getId(), task.getOwner(), externalStatus, snapshotJson, notStatus);
        if (claimed) {
            task.setExternalStatus(externalStatus);
            if (snapshotJson != null) {
                task.setExternalSnapshot(snapshotJson);
            }
        }
        return claimed;
    }

    /**
     * 清外部观测（阶段作用域口；M16 修复轮 A8）：失败要重提交新外部单前调用——
     * 不清的话自动重试仍会轮询那张已失效的单，「重提交新单」永远不会发生。
     */
    public void clearExternal(SaAsyncTask task) {
        if (!repository.clearExternal(task.getId(), task.getOwner())) {
            warnLostOwnership(task, "clear-external");
            return;
        }
        task.setExternalId(null);
        task.setExternalStatus(null);
        task.setExternalSnapshot(null);
    }

    /**
     * 成功收口：置末阶段＋SUCCESS、清调度列，并通知观察者（SUCCESS 档必发）。
     * 写失败＝行已易主/已收口：不写内存行、不通知。
     */
    public void succeed(SaAsyncTask task, TaskFlowRegistry.FlowDef flow) {
        if (!repository.updateTerminal(task.getId(), task.getOwner(), AsyncTaskStatus.SUCCESS.name(),
                flow.lastStage().getValue(), null, task.getAttempt())) {
            warnLostOwnership(task, "succeed");
            return;
        }
        task.setStatus(AsyncTaskStatus.SUCCESS.name());
        task.setStage(flow.lastStage().getValue());
        task.setError(null);
        task.setOwner(null);
        task.setLeaseExpire(null);
        notifyTerminal(task, AsyncTaskStatus.SUCCESS);
    }

    /**
     * 可重试失败：attempt+1、FAILED＋退避、放租约交回队列；预算耗尽则转 {@link #dead}。
     */
    public void fail(SaAsyncTask task, TaskFlowRegistry.FlowDef flow, Throwable e) {
        int attempt = attemptOf(task) + 1;
        if (attempt > flow.getMaxRetry()) {
            dead(task, e);
            return;
        }
        Date nextRunAt = new Date(System.currentTimeMillis() + FAIL_BACKOFF_MS);
        if (!repository.updateFailed(task.getId(), task.getOwner(), messageOf(e), attempt, nextRunAt)) {
            warnLostOwnership(task, "fail");
            return;
        }
        task.setStatus(AsyncTaskStatus.FAILED.name());
        task.setAttempt(attempt);
        task.setError(messageOf(e));
        task.setNextRunAt(nextRunAt);
        task.setOwner(null);
        task.setLeaseExpire(null);
    }

    /**
     * 死亡收口：DEAD＋错误串（不再自动重试），并通知观察者（DEAD 档必发）。
     * 写失败＝行已被别人收口（D12：同一行只发一次终态通知）。
     */
    public void dead(SaAsyncTask task, Throwable e) {
        int attempt = attemptOf(task) + 1;
        if (!repository.updateTerminal(task.getId(), task.getOwner(), AsyncTaskStatus.DEAD.name(),
                task.getStage(), messageOf(e), attempt)) {
            warnLostOwnership(task, "dead");
            return;
        }
        task.setStatus(AsyncTaskStatus.DEAD.name());
        task.setAttempt(attempt);
        task.setError(messageOf(e));
        task.setOwner(null);
        task.setLeaseExpire(null);
        log.error("任务已置 DEAD（不再自动重试）: type={} task={} bizRef={} 错误={}",
                task.getTaskType(), task.getId(), task.getBizRef(), task.getError());
        notifyTerminal(task, AsyncTaskStatus.DEAD);
    }

    /** 本次失败后是否已超重试预算（回收与分诊共用同一份口径） */
    public static boolean retryExhausted(TaskFlowRegistry.FlowDef flow, SaAsyncTask task) {
        return attemptOf(task) + 1 > flow.getMaxRetry();
    }

    /**
     * 结构契约错误（M16 修复轮 A4）：目标阶段不在清单内／禁回跳／未知三态——重试无意义的
     * 代码错，由切面统一置 DEAD（观察者发一次），不交给补偿扫描按租约反复重跑阶段体。
     * 🔴 A3 的「丢失所有权」返回 false、不抛本类型（丢失所有权不是结构错）。
     */
    public static class StageContractException extends IllegalStateException {

        public StageContractException(String message) {
            super(message);
        }
    }

    // ==================== 观察者通知（终态 SUCCESS/DEAD 必发、FAILED 不发；阶段真迁移才发；逐件 try-catch） ====================

    /** 在与阶段方法同一份重建上下文里逐件通知（观察者抛异常不改任务终态） */
    public void notifyTerminal(SaAsyncTask task, AsyncTaskStatus status) {
        SaAsyncTask terminal = repository.findById(task.getId());
        SaAsyncTask row = terminal != null ? terminal : task;
        bridge.execute(row, () -> {
            for (AsyncTaskObserver observer : observers) {
                try {
                    observer.onTerminal(row, status);
                } catch (Exception e) {
                    log.error("任务终态观察者异常（不影响任务终态）: observer={} type={} status={} {}",
                            observer.getClass().getName(), row.getTaskType(), status, e.getMessage(), e);
                }
            }
            return null;
        });
    }

    /**
     * 阶段迁移通知（{@link AsyncTaskObserver#onStage}，真迁移才发）：与 {@link #notifyTerminal}
     * 同构（逐件 try-catch，观察者异常只记 ERROR 日志、不影响任务记账），但有两处从简——
     * 不重读库（advance 的内存行刚更新完，行已带新 stage 与 RUNNING 态，无终态幂等诉求）、
     * 不包桥（调用处在跑批重建上下文内：runner 的 bridge.execute 包住整个阶段执行段，含切面
     * 记账，再包一次是空转）。
     */
    public void notifyStage(SaAsyncTask task, String fromStage, String toStage) {
        for (AsyncTaskObserver observer : observers) {
            try {
                observer.onStage(task, fromStage, toStage);
            } catch (Exception e) {
                log.error("任务阶段迁移观察者异常（不影响任务记账）: observer={} type={} {}->{} {}",
                        observer.getClass().getName(), task.getTaskType(), fromStage, toStage, e.getMessage(), e);
            }
        }
    }

    /** 丢失所有权：进度不写回即可，不是业务失败（行已归别人或有更新的状态） */
    private static void warnLostOwnership(SaAsyncTask task, String write) {
        log.warn("任务写回被拒（所有权已丢，本次进度不写回）: type={} task={} stage={} owner={} write={}",
                task.getTaskType(), task.getId(), task.getStage(), task.getOwner(), write);
    }

    private static int attemptOf(SaAsyncTask task) {
        return task.getAttempt() == null ? 0 : task.getAttempt();
    }

    private static String messageOf(Throwable e) {
        if (e == null || !StringUtils.hasText(e.getMessage())) {
            return e == null ? null : e.getClass().getSimpleName();
        }
        return e.getMessage();
    }
}
