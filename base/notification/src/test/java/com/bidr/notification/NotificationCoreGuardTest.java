package com.bidr.notification;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.notification.dao.entity.NotificationRecord;
import com.bidr.task.AsyncTaskStatus;
import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 站内信 core 骨架守卫（离线，M21）：本批七条必测——
 * DEAD→1 条（每 resolver 一次）／同键再死不新增（store 契约）／FAILED→0 条／
 * publish 吞异常不影响业务／越权 markRead 0 行／tenant null 可见＋IN 过滤／未知 actionType 不跳转。
 * store 用内存替身断言行级语义（真库腿归集成测试）。
 *
 * @author sharp 2026-10-25
 */
public class NotificationCoreGuardTest {

    private final List<Object> published = new ArrayList<>();

    @Before
    public void setUp() {
        TenantContext.remove();
        AccountContext.remove();
    }

    @After
    public void tearDown() {
        TenantContext.remove();
        AccountContext.remove();
    }

    private static <T> ObjectProvider<T> providerOf(List<T> list) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                if (list.isEmpty()) {
                    throw new IllegalStateException("no bean");
                }
                return list.get(0);
            }

            @Override
            public T getObject(Object... args) {
                return getObject();
            }

            @Override
            public T getIfAvailable() {
                return list.isEmpty() ? null : list.get(0);
            }

            @Override
            public T getIfUnique() {
                return list.size() == 1 ? list.get(0) : null;
            }

            @Override
            public Stream<T> orderedStream() {
                return list.stream();
            }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return providerOf(new ArrayList<>());
    }

    private static NotificationStore memoryStore() {
        List<NotificationRecord> rows = new ArrayList<>();
        return new NotificationStore() {
            @Override
            public void save(List<NotificationRecord> newRows) {
                for (NotificationRecord row : newRows) {
                    // 读态归 store 管（模拟 SaNotificationStore：插入/去重更新都置未读）
                    if (row.getDedupeKey() != null) {
                        for (int i = 0; i < rows.size(); i++) {
                            NotificationRecord old = rows.get(i);
                            if (row.getTargetUser().equals(old.getTargetUser())
                                    && row.getDedupeKey().equals(old.getDedupeKey())
                                    && "0".equals(old.getReadFlag())) {
                                row.setReadFlag("0");   // 去重更新分支也保持未读（同 SaNotificationStore）
                                rows.set(i, row); // 只更新不新增
                                return;
                            }
                        }
                    }
                    row.setReadFlag("0");
                    row.setId((long) (rows.size() + 1));
                    rows.add(row);
                }
            }

            @Override
            public Object page(String receiver, long current, long size, String readFlag) {
                return null;
            }

            @Override
            public long unreadCount(String receiver) {
                return rows.stream().filter(r -> r.getTargetUser().equals(receiver)
                        && "0".equals(r.getReadFlag())).count();
            }

            @Override
            public boolean markRead(List<Long> ids, String receiver) {
                boolean hit = false;
                for (NotificationRecord row : rows) {
                    if (ids.contains(row.getId()) && row.getTargetUser().equals(receiver)) {
                        row.setReadFlag("1");
                        hit = true;
                    }
                }
                return hit;
            }

            @Override
            public boolean markAllRead(String receiver) {
                return false;
            }
        };
    }

    /** 任务 stub（DEAD 终态、带 error 与提交人） */
    private static SaAsyncTask deadTask(String taskType, String error, String createBy) {
        SaAsyncTask task = new SaAsyncTask();
        task.setId(1L);
        task.setTaskType(taskType);
        task.setStatus("DEAD");
        task.setError(error);
        task.setCreateBy(createBy);
        task.setBizRef("parse:9:5");
        return task;
    }

    // ---------------------------------------------------------------- G 组：桥（触发纪律）

    @Test
    public void g1_deadTaskProducesOneRowPerResolver() {
        NotificationStore store = memoryStore();
        NotificationPublisher publisher = new NotificationPublisher(providerOf(
                Collections.singletonList(store)));
        NotificationContentResolver resolver = new NotificationContentResolver() {
            @Override
            public boolean supports(String taskType) {
                return "PARSE".equals(taskType);
            }

            @Override
            public Optional<NotificationDraft> resolve(SaAsyncTask task, AsyncTaskStatus status) {
                NotificationDraft d = NotificationDraft.builder()
                        .msgType("FILE_PARSE_FAILED")
                        .level(NotificationLevel.ERROR)
                        .title("《a.pdf》解析失败")
                        .summary(task.getError())
                        .targetUser(task.getCreateBy())
                        .dedupeKey("PARSE:9:5")
                        .build();
                return Optional.of(d);
            }
        };
        AsyncTaskNotificationBridge bridge = new AsyncTaskNotificationBridge(
                providerOf(Collections.singletonList(resolver)), publisher,
                new NotificationGate(emptyProvider()));

        SaAsyncTask task = deadTask("PARSE", "引擎超时", "op-1");
        bridge.onTerminal(task, AsyncTaskStatus.DEAD);
        assertEquals("DEAD→1 条（未读）", 1L, store.unreadCount("op-1"));

        // 同一文件再次死透（dedupe 未读命中）→ 不新增（store 去重契约）
        bridge.onTerminal(deadTask("PARSE", "再次失败", "op-1"), AsyncTaskStatus.DEAD);
        assertEquals("同键未读只更新不新增", 1L, store.unreadCount("op-1"));
    }

    @Test
    public void g2_budgetFailedProducesZeroRows() {
        final int[] calls = {0};
        NotificationStore counting = new NotificationStore() {
            @Override
            public void save(List<NotificationRecord> newRows) {
                calls[0] += newRows.size();
            }

            @Override
            public Object page(String r, long c, long s, String f) {
                return null;
            }

            @Override
            public long unreadCount(String r) {
                return 0;
            }

            @Override
            public boolean markRead(List<Long> ids, String r) {
                return false;
            }

            @Override
            public boolean markAllRead(String r) {
                return false;
            }
        };
        NotificationPublisher publisher = new NotificationPublisher(providerOf(Collections.singletonList(counting)));
        NotificationContentResolver resolver = new NotificationContentResolver() {
            @Override
            public boolean supports(String taskType) {
                return true;
            }

            @Override
            public Optional<NotificationDraft> resolve(SaAsyncTask task, AsyncTaskStatus status) {
                // resolver 故意对 FAILED 也返回 draft——桥必须不给 FAILED 进口
                return Optional.of(NotificationDraft.builder()
                        .msgType("X").title("t").targetUser("op-1").build());
            }
        };
        AsyncTaskNotificationBridge bridge = new AsyncTaskNotificationBridge(
                providerOf(Collections.singletonList(resolver)), publisher,
                new NotificationGate(emptyProvider()));
        bridge.onTerminal(deadTask("PARSE", "第一次失败", "op-1"), AsyncTaskStatus.FAILED);
        assertEquals("预算内 FAILED（桥不监听）→ 0 条", 0, calls[0]);
    }

    @Test
    public void g3_publishNeverThrows_evenWhenStoreExplodes() {
        NotificationStore explosive = new NotificationStore() {
            @Override
            public void save(List<NotificationRecord> newRows) {
                throw new ServiceException("存储炸了");
            }

            @Override
            public Object page(String r, long c, long s, String f) {
                return null;
            }

            @Override
            public long unreadCount(String r) {
                return 0;
            }

            @Override
            public boolean markRead(List<Long> ids, String r) {
                return false;
            }

            @Override
            public boolean markAllRead(String r) {
                return false;
            }
        };
        NotificationPublisher publisher = new NotificationPublisher(providerOf(Collections.singletonList(explosive)));
        NotificationDraft draft = NotificationDraft.builder()
                .msgType("X").title("t").targetUser("op-1").build();
        publisher.publish(draft);
        // 不抛即核心：审计/提醒失败绝不影响业务（调用方 catch-rethrow 场景下业务异常原样上抛）
    }

    @Test
    public void g4_markReadCrossUserIsZeroRows() {
        NotificationStore store = memoryStore();
        NotificationRecord mine = new NotificationRecord();
        mine.setMsgType("X");
        mine.setTitle("t");
        mine.setTargetUser("op-1");
        mine.setReadFlag("0");
        mine.setPushedAt(new java.util.Date());
        store.save(Collections.singletonList(mine));
        assertFalse("他人 markRead 我的消息＝false（0 行生效）", store.markRead(
                Collections.singletonList(mine.getId()), "op-2"));
        assertEquals("我的未读数不变", 1L, store.unreadCount("op-1"));
    }

    @Test
    public void g5_tenantScope_nullAlwaysVisible_membershipFiltered() {
        // SaNotificationStore.applyTenantScope 静态规则：null 行恒可见；有租户集合时 IN 过滤
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NotificationRecord> w =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        TenantContext.set(java.util.Arrays.asList(4L, 5L));
        SaNotificationStore.applyTenantScope(w);
        String sql = w.getSqlSegment();
        assertTrue("含 IS NULL 腿", sql.contains("IS NULL"));
        assertTrue("含 IN 腿", sql.contains("IN"));

        // 空集合不跳条件：退化成只看 NULL（防"被移出租户回看旧消息"）
        TenantContext.set(new ArrayList<>());
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NotificationRecord> w2 =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        SaNotificationStore.applyTenantScope(w2);
        assertTrue("空集合退化＝只有 IS NULL 腿",
                w2.getSqlSegment().contains("IS NULL") && !w2.getSqlSegment().contains("IN"));

        // 无租户上下文（getTenantIds=null）：同样只看 NULL
        TenantContext.remove();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<NotificationRecord> w3 =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        SaNotificationStore.applyTenantScope(w3);
        assertTrue("null 上下文退化＝只有 IS NULL 腿",
                w3.getSqlSegment().contains("IS NULL") && !w3.getSqlSegment().contains("IN"));
    }

    @Test
    public void g6_unknownActionType_droppedButRowKept() {
        List<NotificationRecord> rows = new ArrayList<>();
        NotificationStore store = new NotificationStore() {
            @Override
            public void save(List<NotificationRecord> newRows) {
                rows.addAll(newRows);
            }

            @Override
            public Object page(String r, long c, long s, String f) {
                return null;
            }

            @Override
            public long unreadCount(String r) {
                return 0;
            }

            @Override
            public boolean markRead(List<Long> ids, String r) {
                return false;
            }

            @Override
            public boolean markAllRead(String r) {
                return false;
            }
        };
        NotificationPublisher publisher = new NotificationPublisher(providerOf(Collections.singletonList(store)));
        NotificationDraft bad = NotificationDraft.builder()
                .msgType("X").title("t").targetUser("op-1")
                .action("Bad_Code", "fileId", 1)   // 大写开头不合规
                .build();
        publisher.publish(bad);
        assertEquals("消息照发", 1, rows.size());
        assertNull("动作被丢弃（action_type null＝不可点）", rows.get(0).getActionType());

        NotificationDraft good = NotificationDraft.builder()
                .msgType("X").title("t").targetUser("op-1")
                .action("file_preview", "fileId", 1)
                .actionParam("versionId", 5)
                .build();
        publisher.publish(good);
        assertEquals(2, rows.size());
        assertEquals("合规动作保留", "file_preview", rows.get(1).getActionType());
        assertTrue("参数序列化成 JSON", rows.get(1).getActionParams().contains("\"fileId\":1"));
    }

    @Test
    public void g7_missingStore_droppedWithLog_businessUnaffected() {
        // 无 store 装配：publish 丢弃＋log.error，不抛
        NotificationPublisher publisher = new NotificationPublisher(emptyProvider());
        publisher.publish(NotificationDraft.builder().msgType("X").title("t").targetUser("op-1").build());
    }

    @Test
    public void g8_tenantFallback_fromContext_whenDraftOmitted() {
        List<NotificationRecord> rows = new ArrayList<>();
        NotificationStore store = new NotificationStore() {
            @Override
            public void save(List<NotificationRecord> newRows) {
                rows.addAll(newRows);
            }

            @Override
            public Object page(String r, long c, long s, String f) {
                return null;
            }

            @Override
            public long unreadCount(String r) {
                return 0;
            }

            @Override
            public boolean markRead(List<Long> ids, String r) {
                return false;
            }

            @Override
            public boolean markAllRead(String r) {
                return false;
            }
        };
        NotificationPublisher publisher = new NotificationPublisher(providerOf(Collections.singletonList(store)));
        TenantContext.set(Collections.singletonList(9L));
        publisher.publish(NotificationDraft.builder().msgType("X").title("t").targetUser("op-1").build());
        assertEquals("draft 未显式给租户＋线程有租户上下文＝publisher 兜底填", Long.valueOf(9L),
                rows.get(0).getTenantId());

        // 显式 null（业务对象确实无租户）不被兜底覆盖——draft.builder 默认 null 与兜底无法区分，
        // 故以"显式传值优先"的契约由 resolver 侧保证：传了非 null 值时以它为准
        TenantContext.set(Collections.singletonList(9L));
        publisher.publish(NotificationDraft.builder().msgType("X").title("t").targetUser("op-1")
                .tenantId(4L).build());
        assertEquals("显式租户优先", Long.valueOf(4L), rows.get(1).getTenantId());
    }
}
