package com.bidr.task.dao.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bidr.kernel.config.log.LogSilent;
import com.bidr.task.AsyncTaskRepository;
import com.bidr.task.AsyncTaskStatus;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AsyncTaskRepository} 的 MyBatis-Plus 实现（M16 棒 2）：语义照现网跑批原型逐条搬入
 * （条件 UPDATE 当锁、清列走 {@code update(patch, wrapper.set(列, null))}——既带实体保审计填充、
 * 又只改要改的列，不做整行覆盖），改通用列名。
 *
 * <p><b>执行侧写口统一谓词（M16 修复轮 A3）</b>：{@code owner = ? AND status = 'RUNNING'}
 * （owner 为 NULL 的行用 {@code owner IS NULL}——历史僵尸行只有补偿扫描写得到它）。迟到持有者
 * 的写会更新 0 行、返回 false，由 {@code AsyncTaskService} 记 WARN 并放弃内存行与终态通知。
 * 🔴 这些 SQL 形状未做真库实测（R-6：无真库腿），语义由 {@code InMemoryAsyncTaskRepository}
 * 的同一套谓词契约腿覆盖。</p>
 *
 * <p>跑批三口（ownedRunning/claimBatch/expiredRunning/countClaimable）挂 {@code @LogSilent}：
 * 它们每 tick 都发 SELECT，队列空时也发（现网同形状方法三天写百万行日志的教训在案）。</p>
 *
 * @author sharp
 */
@Service
public class MybatisAsyncTaskRepository implements AsyncTaskRepository {

    /** 快照落库上限：外部观测快照可能很长，超出的截断（对账只看头部关键项） */
    private static final int SNAPSHOT_MAX = 4000;

    /** 错误串落库上限（列宽 varchar(1024)，按码点截不劈代理对） */
    private static final int ERROR_MAX = 1024;

    private final SaAsyncTaskService taskService;

    public MybatisAsyncTaskRepository(SaAsyncTaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public SaAsyncTask insert(SaAsyncTask task) {
        taskService.save(task);
        return task;
    }

    @Override
    public SaAsyncTask findById(Long id) {
        return taskService.getById(id);
    }

    @Override
    public SaAsyncTask findByBizKey(String taskType, String bizRef) {
        return taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .eq(SaAsyncTask::getBizRef, bizRef)
                .eq(SaAsyncTask::getValid, "1")
                .orderByDesc(SaAsyncTask::getId)
                .last("limit 1")
                .one();
    }

    @Override
    public Map<String, SaAsyncTask> findByBizKeys(String taskType, Collection<String> bizRefs) {
        if (bizRefs == null || bizRefs.isEmpty()) {
            return new HashMap<>();
        }
        List<SaAsyncTask> tasks = taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .in(SaAsyncTask::getBizRef, bizRefs)
                .eq(SaAsyncTask::getValid, "1")
                .orderByAsc(SaAsyncTask::getId)
                .list();
        // 升序遍历、后写覆盖 → 留下每个键的 id 最大者（与 findByBizKey 的 orderByDesc＋limit 1 同口径）
        Map<String, SaAsyncTask> latest = new HashMap<>(tasks.size());
        for (SaAsyncTask task : tasks) {
            latest.put(task.getBizRef(), task);
        }
        return latest;
    }

    @Override
    public List<SaAsyncTask> listRecentByCreator(String createBy, int limit) {
        if (!StringUtils.hasText(createBy) || limit <= 0) {
            return new ArrayList<>();
        }
        return taskService.lambdaQuery()
                .eq(SaAsyncTask::getCreateBy, createBy.trim())
                .eq(SaAsyncTask::getValid, "1")
                .orderByDesc(SaAsyncTask::getId)
                .last("limit " + limit)
                .list();
    }

    @Override
    @LogSilent
    public int queuePosition(Long id, String taskType, Date now) {
        if (id == null) {
            return 0;
        }
        long ahead = taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.PENDING.name(), AsyncTaskStatus.FAILED.name())
                .and(w -> w.isNull(SaAsyncTask::getNextRunAt).or().le(SaAsyncTask::getNextRunAt, now))
                .and(w -> w.isNull(SaAsyncTask::getLeaseExpire).or().lt(SaAsyncTask::getLeaseExpire, now))
                .eq(SaAsyncTask::getValid, "1")
                .le(SaAsyncTask::getId, id)
                .count();
        return (int) Math.max(0L, ahead);
    }

    @Override
    @LogSilent
    public List<Long> claimableIds(String taskType, Date now) {
        List<SaAsyncTask> rows = taskService.lambdaQuery()
                .select(SaAsyncTask::getId)
                .eq(SaAsyncTask::getTaskType, taskType)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.PENDING.name(), AsyncTaskStatus.FAILED.name())
                .and(w -> w.isNull(SaAsyncTask::getNextRunAt).or().le(SaAsyncTask::getNextRunAt, now))
                .and(w -> w.isNull(SaAsyncTask::getLeaseExpire).or().lt(SaAsyncTask::getLeaseExpire, now))
                .eq(SaAsyncTask::getValid, "1")
                .orderByAsc(SaAsyncTask::getId)
                .list();
        List<Long> ids = new ArrayList<>(rows.size());
        for (SaAsyncTask row : rows) {
            ids.add(row.getId());
        }
        return ids;
    }

    @Override
    @LogSilent
    public List<SaAsyncTask> ownedRunning(String taskType, String owner, int limit) {
        Date now = new Date();
        return taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .eq(SaAsyncTask::getStatus, AsyncTaskStatus.RUNNING.name())
                .eq(SaAsyncTask::getOwner, owner)
                .gt(SaAsyncTask::getLeaseExpire, now)
                // pending 退避也在本实例名下：退避未到不重跑（三态 pending 的机器形态）
                .and(w -> w.isNull(SaAsyncTask::getNextRunAt).or().le(SaAsyncTask::getNextRunAt, now))
                .eq(SaAsyncTask::getValid, "1")
                .orderByAsc(SaAsyncTask::getId)
                .last("limit " + limit)
                .list();
    }

    @Override
    @LogSilent
    public List<SaAsyncTask> claimBatch(String taskType, String owner, Date leaseExpire, int limit) {
        List<SaAsyncTask> claimed = new ArrayList<>();
        if (limit <= 0) {
            return claimed;
        }
        Date now = new Date();
        List<SaAsyncTask> candidates = taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.PENDING.name(), AsyncTaskStatus.FAILED.name())
                .and(w -> w.isNull(SaAsyncTask::getNextRunAt).or().le(SaAsyncTask::getNextRunAt, now))
                .and(w -> w.isNull(SaAsyncTask::getLeaseExpire).or().lt(SaAsyncTask::getLeaseExpire, now))
                .eq(SaAsyncTask::getValid, "1")
                .orderByAsc(SaAsyncTask::getId)
                .last("limit " + limit)
                .list();
        for (SaAsyncTask candidate : candidates) {
            if (!tryClaim(candidate.getId(), owner, leaseExpire, now)) {
                continue;
            }
            candidate.setStatus(AsyncTaskStatus.RUNNING.name());
            candidate.setOwner(owner);
            candidate.setLeaseExpire(leaseExpire);
            candidate.setError(null);
            claimed.add(candidate);
        }
        return claimed;
    }

    /** 抢占＝条件 UPDATE 当锁：仅当仍为 PENDING/FAILED 且租约空或过期才改成 RUNNING（抢占即清错误） */
    private boolean tryClaim(Long id, String owner, Date leaseExpire, Date now) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setStatus(AsyncTaskStatus.RUNNING.name());
        patch.setOwner(owner);
        patch.setLeaseExpire(leaseExpire);
        LambdaUpdateWrapper<SaAsyncTask> where = new LambdaUpdateWrapper<>();
        where.eq(SaAsyncTask::getId, id)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.PENDING.name(), AsyncTaskStatus.FAILED.name())
                .and(w -> w.isNull(SaAsyncTask::getLeaseExpire).or().lt(SaAsyncTask::getLeaseExpire, now))
                .set(SaAsyncTask::getError, null);
        return taskService.update(patch, where);
    }

    @Override
    public boolean renew(Long id, String owner, Date leaseExpire) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setLeaseExpire(leaseExpire);
        LambdaUpdateWrapper<SaAsyncTask> where = new LambdaUpdateWrapper<>();
        where.eq(SaAsyncTask::getId, id)
                .eq(SaAsyncTask::getOwner, owner)
                .eq(SaAsyncTask::getStatus, AsyncTaskStatus.RUNNING.name());
        return taskService.update(patch, where);
    }

    @Override
    @LogSilent
    public List<SaAsyncTask> expiredRunning(String taskType, Date now, int limit) {
        return taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .eq(SaAsyncTask::getStatus, AsyncTaskStatus.RUNNING.name())
                // 租约空也算过期：历史僵尸行（owner/lease 全空）三条扫描里只有这一条能看见它
                .and(w -> w.isNull(SaAsyncTask::getLeaseExpire).or().lt(SaAsyncTask::getLeaseExpire, now))
                .eq(SaAsyncTask::getValid, "1")
                .orderByAsc(SaAsyncTask::getId)
                .last("limit " + limit)
                .list();
    }

    @Override
    public boolean updateStage(Long id, String owner, String stage) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setStage(stage);
        patch.setStatus(AsyncTaskStatus.RUNNING.name());
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        return taskService.update(patch, where);
    }

    @Override
    public boolean updatePending(Long id, String owner, Date nextRunAt, Date leaseExpire) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setNextRunAt(nextRunAt);
        patch.setLeaseExpire(leaseExpire);
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        return taskService.update(patch, where);
    }

    @Override
    public boolean updateTerminal(Long id, String owner, String status, String stage, String error, Integer attempt) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setStatus(status);
        patch.setStage(stage);
        patch.setError(trim(error, ERROR_MAX));
        if (attempt != null) {
            patch.setAttempt(attempt);
        }
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        where.set(SaAsyncTask::getNextRunAt, null)
                .set(SaAsyncTask::getOwner, null)
                .set(SaAsyncTask::getLeaseExpire, null);
        return taskService.update(patch, where);
    }

    @Override
    public boolean updateFailed(Long id, String owner, String error, int attempt, Date nextRunAt) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setStatus(AsyncTaskStatus.FAILED.name());
        patch.setError(trim(error, ERROR_MAX));
        patch.setAttempt(attempt);
        patch.setNextRunAt(nextRunAt);
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        // owner/租约置空＝交回队列：不清的话，claimBatch 的租约条件会把重试拖到租约自然过期
        where.set(SaAsyncTask::getOwner, null)
                .set(SaAsyncTask::getLeaseExpire, null);
        return taskService.update(patch, where);
    }

    @Override
    public boolean updateRequeue(Long id, String firstStage) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setStage(firstStage);
        patch.setStatus(AsyncTaskStatus.PENDING.name());
        patch.setAttempt(0);
        LambdaUpdateWrapper<SaAsyncTask> where = new LambdaUpdateWrapper<>();
        where.eq(SaAsyncTask::getId, id)
                .set(SaAsyncTask::getError, null)
                .set(SaAsyncTask::getNextRunAt, null)
                .set(SaAsyncTask::getExternalId, null)
                .set(SaAsyncTask::getExternalStatus, null)
                .set(SaAsyncTask::getExternalSnapshot, null)
                .set(SaAsyncTask::getOwner, null)
                .set(SaAsyncTask::getLeaseExpire, null);
        return taskService.update(patch, where);
    }

    @Override
    public boolean updateAbort(Long id, String error) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setStatus(AsyncTaskStatus.DEAD.name());
        patch.setError(trim(error, ERROR_MAX));
        LambdaUpdateWrapper<SaAsyncTask> where = new LambdaUpdateWrapper<>();
        // 受理侧作废不受所有权约束：PENDING 行没有 owner；已 SUCCESS/DEAD 的行不再改
        where.eq(SaAsyncTask::getId, id)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.PENDING.name(), AsyncTaskStatus.RUNNING.name(),
                        AsyncTaskStatus.FAILED.name())
                .set(SaAsyncTask::getNextRunAt, null)
                .set(SaAsyncTask::getOwner, null)
                .set(SaAsyncTask::getLeaseExpire, null);
        return taskService.update(patch, where);
    }

    @Override
    public boolean updateExternal(Long id, String owner, String externalId, String externalStatus, String snapshotJson) {
        SaAsyncTask patch = new SaAsyncTask();
        if (externalId != null) {
            patch.setExternalId(trim(externalId, 64));
        }
        if (externalStatus != null) {
            patch.setExternalStatus(trim(externalStatus, 20));
        }
        if (snapshotJson != null) {
            patch.setExternalSnapshot(trim(snapshotJson, SNAPSHOT_MAX));
        }
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        return taskService.update(patch, where);
    }

    @Override
    public boolean claimExternal(Long id, String owner, String externalStatus, String snapshotJson, String notStatus) {
        SaAsyncTask patch = new SaAsyncTask();
        patch.setExternalStatus(trim(externalStatus, 20));
        if (snapshotJson != null) {
            patch.setExternalSnapshot(trim(snapshotJson, SNAPSHOT_MAX));
        }
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        where.and(w -> w.isNull(SaAsyncTask::getExternalStatus)
                .or().ne(SaAsyncTask::getExternalStatus, notStatus));
        return taskService.update(patch, where);
    }

    @Override
    public boolean sealExternal(Long id, String externalStatus) {
        String sealed = trim(externalStatus, 20);
        SaAsyncTask patch = new SaAsyncTask();
        patch.setExternalStatus(sealed);
        LambdaUpdateWrapper<SaAsyncTask> where = new LambdaUpdateWrapper<>();
        // 受理侧终态封存不受所有权约束：只钉已终态（SUCCESS/DEAD）的行；已封过同值＝CAS 输家不写
        where.eq(SaAsyncTask::getId, id)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.SUCCESS.name(), AsyncTaskStatus.DEAD.name())
                .and(w -> w.isNull(SaAsyncTask::getExternalStatus)
                        .or().ne(SaAsyncTask::getExternalStatus, sealed));
        return taskService.update(patch, where);
    }

    @Override
    public boolean clearExternal(Long id, String owner) {
        LambdaUpdateWrapper<SaAsyncTask> where = ownedRunning(id, owner);
        where.set(SaAsyncTask::getExternalId, null)
                .set(SaAsyncTask::getExternalStatus, null)
                .set(SaAsyncTask::getExternalSnapshot, null);
        return taskService.update(new SaAsyncTask(), where);
    }

    @Override
    public int deleteByBizKey(String taskType, String bizRef) {
        long before = taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .eq(SaAsyncTask::getBizRef, bizRef)
                .count();
        if (before == 0) {
            return 0;
        }
        taskService.lambdaUpdate()
                .eq(SaAsyncTask::getTaskType, taskType)
                .eq(SaAsyncTask::getBizRef, bizRef)
                .remove();
        return (int) before;
    }

    @Override
    @LogSilent
    public long countClaimable(String taskType, Date now) {
        return taskService.lambdaQuery()
                .eq(SaAsyncTask::getTaskType, taskType)
                .in(SaAsyncTask::getStatus, AsyncTaskStatus.PENDING.name(), AsyncTaskStatus.FAILED.name())
                .and(w -> w.isNull(SaAsyncTask::getNextRunAt).or().le(SaAsyncTask::getNextRunAt, now))
                .and(w -> w.isNull(SaAsyncTask::getLeaseExpire).or().lt(SaAsyncTask::getLeaseExpire, now))
                .eq(SaAsyncTask::getValid, "1")
                .count();
    }

    /** 执行侧写口的统一谓词：id ＋ 当前持有者 ＋ 仍在跑（owner NULL 的行用 IS NULL） */
    private static LambdaUpdateWrapper<SaAsyncTask> ownedRunning(Long id, String owner) {
        LambdaUpdateWrapper<SaAsyncTask> where = new LambdaUpdateWrapper<>();
        where.eq(SaAsyncTask::getId, id)
                .eq(SaAsyncTask::getStatus, AsyncTaskStatus.RUNNING.name());
        if (owner == null) {
            where.isNull(SaAsyncTask::getOwner);
        } else {
            where.eq(SaAsyncTask::getOwner, owner);
        }
        return where;
    }

    /** 码点尺截断（不劈代理对） */
    private static String trim(String s, int max) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        return s.substring(0, s.offsetByCodePoints(0, max));
    }
}
