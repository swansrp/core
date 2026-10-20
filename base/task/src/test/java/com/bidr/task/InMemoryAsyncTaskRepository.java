package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 内存版 {@link AsyncTaskRepository}（测试件）：单线程条件下复刻条件 UPDATE 语义
 * （抢占当锁、乐观认领、**执行侧写口的 owner＋RUNNING 谓词**），并留一份事件流水供
 * 「先后顺序」类判据断言（如切面记账须在业务事务回滚决策之后）。行存副本——底盘持有的
 * 实体后续被改不影响「库内」行，对齐真实库。
 *
 * @author sharp
 */
public class InMemoryAsyncTaskRepository implements AsyncTaskRepository {

    /** 事件流水（判据断言用）：写操作与测试注入的时钟点都会记录 */
    public final List<String> events = new ArrayList<>();

    /** 时钟（可注入：退避/租约到期后的下一 tick 用它跳时间，测试不用真等） */
    public Supplier<Date> clock = Date::new;

    /** 取排队数时抛这个（提交闸门「取不到排队数放行」腿用） */
    public RuntimeException countFailure;

    private final Map<Long, SaAsyncTask> rows = new LinkedHashMap<>();

    private final AtomicLong seq = new AtomicLong();

    private Date now() {
        return clock.get();
    }

    /** 当前注入时钟的读数（测试把跑批的租约/回收时钟与本库对齐用） */
    public Date clock() {
        return clock.get();
    }

    private static SaAsyncTask copyOf(SaAsyncTask task) {
        SaAsyncTask copy = new SaAsyncTask();
        BeanUtils.copyProperties(task, copy);
        return copy;
    }

    /**
     * 模拟「行已易主/已被回收」（A3 迟到写被拒腿）：把库内行的持有者改成别的实例。
     * 真实库里这是另一实例 claim/回收 写进去的值，这里只跳过条件 UPDATE 的编排。
     */
    public void handOver(Long id, String newOwner) {
        SaAsyncTask row = rows.get(id);
        if (row != null) {
            row.setOwner(newOwner);
            events.add("hand-over:" + id + ":" + newOwner);
        }
    }

    @Override
    public SaAsyncTask insert(SaAsyncTask task) {
        if (task.getValid() == null) {
            task.setValid("1"); // 库默认值（char(1) DEFAULT '1'）的内存等价物
        }
        task.setId(seq.incrementAndGet());
        rows.put(task.getId(), copyOf(task));
        events.add("insert:" + task.getId());
        return task;
    }

    @Override
    public SaAsyncTask findById(Long id) {
        SaAsyncTask row = rows.get(id);
        return row == null ? null : copyOf(row);
    }

    @Override
    public SaAsyncTask findByBizKey(String taskType, String bizRef) {
        SaAsyncTask hit = null;
        for (SaAsyncTask row : rows.values()) {
            if (taskType.equals(row.getTaskType()) && bizRef.equals(row.getBizRef())
                    && "1".equals(row.getValid()) && (hit == null || row.getId() > hit.getId())) {
                hit = row;
            }
        }
        return hit == null ? null : copyOf(hit);
    }

    @Override
    public Map<String, SaAsyncTask> findByBizKeys(String taskType, Collection<String> bizRefs) {
        Map<String, SaAsyncTask> latest = new LinkedHashMap<>();
        if (bizRefs == null || bizRefs.isEmpty()) {
            return latest;
        }
        for (SaAsyncTask row : rows.values()) {
            if (taskType.equals(row.getTaskType()) && bizRefs.contains(row.getBizRef())
                    && "1".equals(row.getValid())) {
                SaAsyncTask existed = latest.get(row.getBizRef());
                if (existed == null || row.getId() > existed.getId()) {
                    latest.put(row.getBizRef(), copyOf(row));
                }
            }
        }
        return latest;
    }

    @Override
    public List<SaAsyncTask> listRecentByCreator(String createBy, int limit) {
        List<SaAsyncTask> out = new ArrayList<>();
        if (createBy == null || createBy.trim().isEmpty() || limit <= 0) {
            return out;
        }
        List<SaAsyncTask> hits = new ArrayList<>();
        for (SaAsyncTask row : rows.values()) {
            if (createBy.trim().equals(row.getCreateBy()) && "1".equals(row.getValid())) {
                hits.add(copyOf(row));
            }
        }
        hits.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        for (int i = 0; i < Math.min(limit, hits.size()); i++) {
            out.add(hits.get(i));
        }
        return out;
    }

    @Override
    public int queuePosition(Long id, String taskType, Date now) {
        if (id == null) {
            return 0;
        }
        int ahead = 0;
        for (SaAsyncTask row : rows.values()) {
            if (taskType.equals(row.getTaskType()) && claimable(row, now) && row.getId() <= id) {
                ahead++;
            }
        }
        return ahead;
    }

    @Override
    public List<Long> claimableIds(String taskType, Date now) {
        List<Long> ids = new ArrayList<>();
        for (SaAsyncTask row : rows.values()) {
            if (taskType.equals(row.getTaskType()) && claimable(row, now)) {
                ids.add(row.getId());
            }
        }
        ids.sort(Long::compareTo);
        return ids;
    }

    @Override
    public List<SaAsyncTask> ownedRunning(String taskType, String owner, int limit) {
        Date now = now();
        List<SaAsyncTask> out = new ArrayList<>();
        for (SaAsyncTask row : rows.values()) {
            if (!taskType.equals(row.getTaskType()) || !AsyncTaskStatus.RUNNING.name().equals(row.getStatus())
                    || !Objects.equals(owner, row.getOwner())
                    || row.getLeaseExpire() == null || !row.getLeaseExpire().after(now)) {
                continue;
            }
            if (row.getNextRunAt() != null && row.getNextRunAt().after(now)) {
                continue; // pending 退避未到：本实例下轮也不重跑
            }
            if (out.size() >= limit) {
                break;
            }
            out.add(copyOf(row));
        }
        return out;
    }

    @Override
    public List<SaAsyncTask> claimBatch(String taskType, String owner, Date leaseExpire, int limit) {
        Date now = now();
        List<SaAsyncTask> claimed = new ArrayList<>();
        if (limit <= 0) {
            return claimed;
        }
        for (SaAsyncTask row : rows.values()) {
            if (claimed.size() >= limit) {
                break;
            }
            if (!taskType.equals(row.getTaskType()) || !claimable(row, now)) {
                continue;
            }
            // 条件 UPDATE 当锁：单线程下重检即抢占
            row.setStatus(AsyncTaskStatus.RUNNING.name());
            row.setOwner(owner);
            row.setLeaseExpire(leaseExpire);
            row.setError(null);
            events.add("claim:" + row.getId());
            claimed.add(copyOf(row));
        }
        return claimed;
    }

    private static boolean claimable(SaAsyncTask row, Date now) {
        boolean queued = AsyncTaskStatus.PENDING.name().equals(row.getStatus())
                || AsyncTaskStatus.FAILED.name().equals(row.getStatus());
        boolean due = row.getNextRunAt() == null || !row.getNextRunAt().after(now);
        boolean leaseFree = row.getLeaseExpire() == null || row.getLeaseExpire().before(now);
        return queued && due && leaseFree && "1".equals(row.getValid());
    }

    @Override
    public boolean renew(Long id, String owner, Date leaseExpire) {
        SaAsyncTask row = rows.get(id);
        if (row == null || !AsyncTaskStatus.RUNNING.name().equals(row.getStatus())
                || !Objects.equals(owner, row.getOwner())) {
            return false;
        }
        row.setLeaseExpire(leaseExpire);
        events.add("renew:" + id);
        return true;
    }

    @Override
    public List<SaAsyncTask> expiredRunning(String taskType, Date now, int limit) {
        List<SaAsyncTask> out = new ArrayList<>();
        for (SaAsyncTask row : rows.values()) {
            if (out.size() >= limit) {
                break;
            }
            if (taskType.equals(row.getTaskType()) && AsyncTaskStatus.RUNNING.name().equals(row.getStatus())
                    // 租约空也算过期：历史僵尸行（owner/lease 全空）只有这条扫描能看见它
                    && (row.getLeaseExpire() == null || row.getLeaseExpire().before(now))
                    && "1".equals(row.getValid())) {
                out.add(copyOf(row));
            }
        }
        return out;
    }

    /** 执行侧写口的统一谓词（内存等价物）：行存在 ＋ 仍 RUNNING ＋ 持有者一致（NULL 按 NULL 比） */
    private SaAsyncTask ownedRow(Long id, String owner) {
        SaAsyncTask row = rows.get(id);
        if (row == null || !AsyncTaskStatus.RUNNING.name().equals(row.getStatus())
                || !Objects.equals(owner, row.getOwner())) {
            return null;
        }
        return row;
    }

    @Override
    public boolean updateStage(Long id, String owner, String stage) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("update-stage-rejected:" + id);
            return false;
        }
        row.setStage(stage);
        row.setStatus(AsyncTaskStatus.RUNNING.name());
        events.add("update-stage:" + id + ":" + stage);
        return true;
    }

    @Override
    public boolean updatePending(Long id, String owner, Date nextRunAt, Date leaseExpire) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("update-pending-rejected:" + id);
            return false;
        }
        row.setNextRunAt(nextRunAt);
        row.setLeaseExpire(leaseExpire);
        events.add("update-pending:" + id);
        return true;
    }

    @Override
    public boolean updateTerminal(Long id, String owner, String status, String stage, String error, Integer attempt) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("update-terminal-rejected:" + id);
            return false;
        }
        row.setStatus(status);
        row.setStage(stage);
        row.setError(error);
        if (attempt != null) {
            row.setAttempt(attempt);
        }
        row.setNextRunAt(null);
        row.setOwner(null);
        row.setLeaseExpire(null);
        events.add("update-terminal:" + id + ":" + status);
        return true;
    }

    @Override
    public boolean updateFailed(Long id, String owner, String error, int attempt, Date nextRunAt) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("update-failed-rejected:" + id);
            return false;
        }
        row.setStatus(AsyncTaskStatus.FAILED.name());
        row.setError(error);
        row.setAttempt(attempt);
        row.setNextRunAt(nextRunAt);
        row.setOwner(null);
        row.setLeaseExpire(null);
        events.add("update-failed:" + id + ":attempt" + attempt);
        return true;
    }

    @Override
    public boolean updateRequeue(Long id, String firstStage) {
        SaAsyncTask row = rows.get(id);
        if (row == null) {
            return false;
        }
        row.setStage(firstStage);
        row.setStatus(AsyncTaskStatus.PENDING.name());
        row.setAttempt(0);
        row.setError(null);
        row.setNextRunAt(null);
        row.setExternalId(null);
        row.setExternalStatus(null);
        row.setExternalSnapshot(null);
        row.setOwner(null);
        row.setLeaseExpire(null);
        events.add("update-requeue:" + id);
        return true;
    }

    @Override
    public boolean updateAbort(Long id, String error) {
        SaAsyncTask row = rows.get(id);
        if (row == null) {
            return false;
        }
        boolean abortable = AsyncTaskStatus.PENDING.name().equals(row.getStatus())
                || AsyncTaskStatus.RUNNING.name().equals(row.getStatus())
                || AsyncTaskStatus.FAILED.name().equals(row.getStatus());
        if (!abortable) {
            events.add("update-abort-rejected:" + id);
            return false;
        }
        row.setStatus(AsyncTaskStatus.DEAD.name());
        row.setError(error);
        row.setNextRunAt(null);
        row.setOwner(null);
        row.setLeaseExpire(null);
        events.add("update-abort:" + id);
        return true;
    }

    @Override
    public boolean updateExternal(Long id, String owner, String externalId, String externalStatus, String snapshotJson) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("update-external-rejected:" + id);
            return false;
        }
        if (externalId != null) {
            row.setExternalId(externalId);
        }
        if (externalStatus != null) {
            row.setExternalStatus(externalStatus);
        }
        if (snapshotJson != null) {
            row.setExternalSnapshot(snapshotJson);
        }
        events.add("update-external:" + id);
        return true;
    }

    @Override
    public boolean claimExternal(Long id, String owner, String externalStatus, String snapshotJson, String notStatus) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("claim-external-rejected:" + id);
            return false;
        }
        if (row.getExternalStatus() != null && notStatus.equals(row.getExternalStatus())) {
            events.add("claim-external-miss:" + id);
            return false;
        }
        row.setExternalStatus(externalStatus);
        if (snapshotJson != null) {
            row.setExternalSnapshot(snapshotJson);
        }
        events.add("claim-external-hit:" + id);
        return true;
    }

    @Override
    public boolean clearExternal(Long id, String owner) {
        SaAsyncTask row = ownedRow(id, owner);
        if (row == null) {
            events.add("clear-external-rejected:" + id);
            return false;
        }
        row.setExternalId(null);
        row.setExternalStatus(null);
        row.setExternalSnapshot(null);
        events.add("clear-external:" + id);
        return true;
    }

    /** 受理侧终态封存（内存等价物）：无 owner 限，行须已终态（SUCCESS/DEAD）且未封过同值 */
    @Override
    public boolean sealExternal(Long id, String externalStatus) {
        SaAsyncTask row = rows.get(id);
        if (row == null || !AsyncTaskStatus.isTerminal(row.getStatus())) {
            events.add("seal-external-rejected:" + id);
            return false;
        }
        if (row.getExternalStatus() != null && row.getExternalStatus().equals(externalStatus)) {
            events.add("seal-external-miss:" + id);
            return false;
        }
        row.setExternalStatus(externalStatus);
        events.add("seal-external-hit:" + id);
        return true;
    }

    @Override
    public int deleteByBizKey(String taskType, String bizRef) {
        int removed = 0;
        java.util.Iterator<SaAsyncTask> it = rows.values().iterator();
        while (it.hasNext()) {
            SaAsyncTask row = it.next();
            if (taskType.equals(row.getTaskType()) && bizRef.equals(row.getBizRef())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            events.add("delete:" + taskType + ":" + bizRef);
        }
        return removed;
    }

    @Override
    public long countClaimable(String taskType, Date now) {
        if (countFailure != null) {
            throw countFailure;
        }
        long count = 0;
        for (SaAsyncTask row : rows.values()) {
            if (taskType.equals(row.getTaskType()) && claimable(row, now)) {
                count++;
            }
        }
        return count;
    }
}
