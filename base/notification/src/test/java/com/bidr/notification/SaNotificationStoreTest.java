package com.bidr.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.notification.dao.entity.NotificationRecord;
import com.bidr.notification.dao.repository.SaNotificationRepoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 默认 store 的 SQL 条件守卫（M21 review 补测；接替内存替身的假绿）：
 * mock 仓储、捕获 {@code QueryWrapper}，断言<b>生成到 SQL 的条件段与参数</b>——
 * dedupe 命中键／markRead 双闸（receiver＋read_flag，越权不命中）／租户恒定过滤
 * （IS NULL OR IN，空集退化只看 NULL）／valid 过滤。
 *
 * <p>不需要真库：条件段由 {@code QueryWrapper.getSqlSegment()} 离线生成，
 * 参数值在 {@code getParamNameValuePairs()} 里逐个断言。</p>
 *
 * @author sharp (M21 review 补测 2026-10-25)
 */
class SaNotificationStoreTest {

    private final List<Object> unused = new java.util.ArrayList<>();

    private SaNotificationRepoService repo;
    private SaNotificationStore store;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SaNotificationRepoService.class);
        store = new SaNotificationStore(repo);
        TenantContext.remove();
    }

    @AfterEach
    void tearDown() {
        TenantContext.remove();
    }

    // ==================== helpers ====================

    private static NotificationRecord row(String msgType, String targetUser, String dedupeKey) {
        NotificationRecord r = new NotificationRecord();
        r.setMsgType(msgType);
        r.setLevel("ERROR");
        r.setTitle("t");
        r.setTargetUser(targetUser);
        r.setDedupeKey(dedupeKey);
        return r;
    }

    /** 捕获 update(patch, wrapper) 的 WHERE 条件段与参数值（Java 8：普通静态类） */
    private static class CapturedUpdate {
        final NotificationRecord patch;
        final com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<NotificationRecord> wrapper;

        CapturedUpdate(NotificationRecord patch,
                       com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<NotificationRecord> wrapper) {
            this.patch = patch;
            this.wrapper = wrapper;
        }

        String sql() {
            return wrapper.getSqlSegment();
        }

        java.util.Map<String, Object> params() {
            return wrapper.getParamNameValuePairs();
        }
    }

    // ==================== save：dedupe 三态 ====================

    @Test
    void save_dedupeHit_updatesExistingUnreadRow() {
        NotificationRecord existing = new NotificationRecord();
        existing.setId(7L);
        existing.setReadFlag("0");
        when(repo.getOne(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(existing);

        NotificationRecord incoming = row("FILE_PARSE_FAILED", "op-1", "PARSE:9:5");
        store.save(Collections.singletonList(incoming));

        assertEquals(7L, incoming.getId(), "去重命中＝复用已有行 id");
        assertEquals("0", incoming.getReadFlag(), "更新分支保持未读");
        verify(repo, never()).insert(any(NotificationRecord.class));
        verify(repo).updateById(incoming);
        // 无 dedupe 腿（getOne 已命中）：不会有第二个 update
        verify(repo, never()).update(any(NotificationRecord.class),
                any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class));
    }

    @Test
    void save_dedupeMiss_insertsWithReadStateAndValid() {
        when(repo.getOne(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(null);

        NotificationRecord incoming = row("FILE_PARSE_FAILED", "op-1", "PARSE:9:5");
        store.save(Collections.singletonList(incoming));

        assertEquals("0", incoming.getReadFlag(), "插入分支置未读");
        assertEquals("1", incoming.getValid());
        assertTrue(incoming.getPushedAt() != null, "pushed_at 落库前必须有值（列表排序列）");
        verify(repo).insert(incoming);
        verify(repo, never()).updateById(any(NotificationRecord.class));
    }

    @Test
    void save_dedupeQuery_targetsReceiverAndKeyAndUnread() {
        when(repo.getOne(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(null);
        store.save(Collections.singletonList(row("X", "op-9", "K1")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).getOne(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("target_user"), "按收件人限定: " + sql);
        assertTrue(sql.contains("dedupe_key"), "按去重键限定: " + sql);
        assertTrue(sql.contains("read_flag"), "只命中未读行: " + sql);
        assertTrue(sql.contains("valid"), "软删行不参与去重: " + sql);
    }

    // ==================== markRead：双闸（越权＋幂等） ====================

    @Test
    void markRead_whereForcesReceiverAndUnreadFlag() {
        when(repo.update(Mockito.any(NotificationRecord.class), Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(true);

        store.markRead(Arrays.asList(1L, 2L), "op-1");

        ArgumentCaptor<NotificationRecord> patch = ArgumentCaptor.forClass(NotificationRecord.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<NotificationRecord>> wrapper =
                ArgumentCaptor.forClass((Class) com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class);
        verify(repo).update(patch.capture(), wrapper.capture());

        assertEquals("1", patch.getValue().getReadFlag(), "SET 只动已读两列");
        assertTrue(patch.getValue().getReadAt() != null, "read_at 同时落");
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("id IN"), "按 id 集合: " + sql);
        assertTrue(sql.contains("target_user"), "🔴 越权闸：强制 receiver=登录人: " + sql);
        assertTrue(sql.contains("read_flag"), "🔴 幂等闸：只命中未读行: " + sql);
        // 参数里必须出现登录人（越权传他人 id 时不命中任何行）
        boolean receiverInParams = wrapper.getValue().getParamNameValuePairs().values().stream()
                .anyMatch(v -> "op-1".equals(String.valueOf(v)));
        assertTrue(receiverInParams, "登录人必须在 WHERE 参数里: " + wrapper.getValue().getParamNameValuePairs());
    }

    @Test
    void markRead_emptyIds_noQuery() {
        store.markRead(Collections.emptyList(), "op-1");
        verify(repo, never()).update(Mockito.any(NotificationRecord.class), Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class));
    }

    // ==================== unreadCount / page：租户恒定过滤 ====================

    @Test
    void unreadCount_withTenants_isNullOrInFilter() {
        TenantContext.set(Arrays.asList(4L, 5L));
        when(repo.count(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(3L);

        long n = store.unreadCount("op-1");

        assertEquals(3L, n);
        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).count(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("target_user"), sql);
        assertTrue(sql.contains("read_flag"), sql);
        assertTrue(sql.contains("IS NULL"), "null＝全局消息恒可见: " + sql);
        assertTrue(sql.contains("IN"), "租户集合过滤: " + sql);
    }

    @Test
    void unreadCount_emptyTenantSet_degradesToNullOnly() {
        TenantContext.set(Collections.emptyList());
        when(repo.count(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(0L);

        store.unreadCount("op-1");

        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).count(cap.capture());
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("IS NULL") && !sql.contains("IN"),
                "空集合退化＝只看 NULL（不跳过条件，防回看已离开租户的消息）: " + sql);
    }

    @Test
    void page_mapsMpPageToCoreType_andFilters() {
        TenantContext.set(Arrays.asList(4L));
        NotificationRecord row = row("X", "op-1", null);
        Page<NotificationRecord> mp = new Page<>(1, 20, 1);
        mp.setRecords(Arrays.asList(row));
        when(repo.select(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class), Mockito.anyLong(), Mockito.anyLong())).thenReturn(mp);

        NotificationPage out = store.page("op-1", 1, 20, null);

        assertEquals(1, out.getRecords().size());
        assertEquals(1, out.getTotal());
        assertEquals("X", out.getRecords().get(0).getMsgType());

        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).select(cap.capture(), Mockito.eq(1L), Mockito.eq(20L));
        String sql = cap.getValue().getSqlSegment();
        assertTrue(sql.contains("target_user"), sql);
        assertTrue(sql.contains("valid"), sql);
        assertTrue(sql.contains("IS NULL"), sql);
        assertTrue(sql.contains("pushed_at"), "pushed_at 降序排序: " + sql);
    }

    // ==================== 读面不夹带多余过滤（A 形态零配置） ====================

    @Test
    void readFlagFilter_passesThrough() {
        TenantContext.remove();
        when(repo.count(Mockito.any(com.baomidou.mybatisplus.core.conditions.query.QueryWrapper.class))).thenReturn(0L);
        store.unreadCount("op-1");
        // unreadCount 恒带 read_flag='0'；page 的 readFlag 参数直传——这里验 unread 主路径
        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).count(cap.capture());
        assertTrue(cap.getValue().getSqlSegment().contains("read_flag"));
    }
}
