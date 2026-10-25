package com.bidr.notification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.notification.dao.entity.NotificationRecord;
import com.bidr.notification.dao.repository.SaNotificationRepoService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 默认 store 的 SQL 条件守卫（M21 review 补测；JUnit4）：mock 仓储、捕获
 * {@code QueryWrapper}，断言<b>生成到 SQL 的条件段与参数</b>——dedupe 命中键／
 * markRead 双闸（receiver＋read_flag，越权不命中）／租户恒定过滤（IS NULL OR IN，
 * 空集退化只看 NULL）／valid 过滤。不需要真库：条件段由
 * {@code QueryWrapper.getSqlSegment()} 离线生成。
 *
 * <p>JUnit4 断言口径：{@code assertEquals(message, expected, actual)}、
 * {@code assertTrue(message, condition)}。</p>
 *
 * @author sharp (M21 review 补测 2026-10-25)
 */
public class SaNotificationStoreTest {

    private SaNotificationRepoService repo;
    private SaNotificationStore store;

    @Before
    public void setUp() {
        repo = Mockito.mock(SaNotificationRepoService.class);
        store = new SaNotificationStore(repo);
        TenantContext.remove();
    }

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    private static NotificationRecord row(String msgType, String targetUser, String dedupeKey) {
        NotificationRecord r = new NotificationRecord();
        r.setMsgType(msgType);
        r.setLevel("ERROR");
        r.setTitle("t");
        r.setTargetUser(targetUser);
        r.setDedupeKey(dedupeKey);
        return r;
    }

    private static String sqlOf(QueryWrapper<NotificationRecord> wrapper) {
        return wrapper.getSqlSegment();
    }

    // ==================== save：dedupe 三态 ====================

    @Test
    public void save_dedupeHit_updatesExistingUnreadRow() {
        NotificationRecord existing = new NotificationRecord();
        existing.setId(7L);
        existing.setReadFlag("0");
        when(repo.getOne(any(QueryWrapper.class))).thenReturn(existing);

        NotificationRecord incoming = row("FILE_PARSE_FAILED", "op-1", "PARSE:9:5");
        store.save(Collections.singletonList(incoming));

        assertEquals("去重命中＝复用已有行 id", 7L, incoming.getId().longValue());
        assertEquals("更新分支保持未读", "0", incoming.getReadFlag());
        verify(repo, never()).insert(Mockito.any(NotificationRecord.class));
        verify(repo, never()).update(Mockito.any(NotificationRecord.class),
                Mockito.any(QueryWrapper.class));
    }

    @Test
    public void save_dedupeMiss_insertsWithReadStateAndValid() {
        when(repo.getOne(any(QueryWrapper.class))).thenReturn(null);

        NotificationRecord incoming = row("FILE_PARSE_FAILED", "op-1", "PARSE:9:5");
        store.save(Collections.singletonList(incoming));

        assertEquals("插入分支置未读", "0", incoming.getReadFlag());
        assertEquals("1", incoming.getValid());
        assertTrue("pushed_at 落库前必须有值（列表排序列）", incoming.getPushedAt() != null);
        verify(repo).insert(incoming);
        verify(repo, never()).updateById(Mockito.any(NotificationRecord.class));
    }

    @Test
    public void save_dedupeQuery_targetsReceiverAndKeyAndUnread() {
        when(repo.getOne(any(QueryWrapper.class))).thenReturn(null);
        store.save(Collections.singletonList(row("X", "op-9", "K1")));

        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).getOne(cap.capture());
        String sql = sqlOf(cap.getValue());
        assertTrue("按收件人限定: " + sql, sql.contains("target_user"));
        assertTrue("按去重键限定: " + sql, sql.contains("dedupe_key"));
        assertTrue("只命中未读行: " + sql, sql.contains("read_flag"));
        assertTrue("软删行不参与去重: " + sql, sql.contains("valid"));
    }

    // ==================== markRead：双闸（越权＋幂等） ====================

    @Test
    public void markRead_whereForcesReceiverAndUnreadFlag() {
        when(repo.update(Mockito.any(NotificationRecord.class),
                Mockito.any(QueryWrapper.class))).thenReturn(true);

        store.markRead(Arrays.asList(1L, 2L), "op-1");

        ArgumentCaptor<NotificationRecord> patch = ArgumentCaptor.forClass(NotificationRecord.class);
        ArgumentCaptor<QueryWrapper<NotificationRecord>> wrapper =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).update(patch.capture(), wrapper.capture());

        assertEquals("SET 只动已读两列", "1", patch.getValue().getReadFlag());
        assertTrue("read_at 同时落", patch.getValue().getReadAt() != null);
        String sql = sqlOf(wrapper.getValue());
        assertTrue("按 id 集合: " + sql, sql.contains("IN"));
        assertTrue("越权闸：强制 receiver=登录人: " + sql, sql.contains("target_user"));
        assertTrue("幂等闸：只命中未读行: " + sql, sql.contains("read_flag"));
        boolean receiverInParams = wrapper.getValue().getParamNameValuePairs().values().stream()
                .anyMatch(v -> "op-1".equals(String.valueOf(v)));
        assertTrue("登录人必须在 WHERE 参数里: " + wrapper.getValue().getParamNameValuePairs(),
                receiverInParams);
    }

    @Test
    public void markRead_emptyIds_noQuery() {
        store.markRead(Collections.<Long>emptyList(), "op-1");
        verify(repo, never()).update(Mockito.any(NotificationRecord.class),
                Mockito.any(QueryWrapper.class));
    }

    // ==================== unreadCount / page：租户恒定过滤 ====================

    @Test
    public void unreadCount_withTenants_isNullOrInFilter() {
        TenantContext.set(Arrays.asList(4L, 5L));
        when(repo.count(any(QueryWrapper.class))).thenReturn(3L);

        long n = store.unreadCount("op-1");

        assertEquals("未读数透传", 3L, n);
        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).count(cap.capture());
        String sql = sqlOf(cap.getValue());
        assertTrue("按收件人限定: " + sql, sql.contains("target_user"));
        assertTrue("只数未读: " + sql, sql.contains("read_flag"));
        assertTrue("null＝全局消息恒可见: " + sql, sql.contains("IS NULL"));
        assertTrue("租户集合过滤: " + sql, sql.contains("IN"));
    }

    @Test
    public void unreadCount_emptyTenantSet_degradesToNullOnly() {
        TenantContext.set(Collections.<Long>emptyList());
        when(repo.count(any(QueryWrapper.class))).thenReturn(0L);

        store.unreadCount("op-1");

        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).count(cap.capture());
        String sql = sqlOf(cap.getValue());
        assertTrue("空集合退化＝只看 NULL（不跳过条件，防回看已离开租户的消息）: " + sql,
                sql.contains("IS NULL") && !sql.contains("IN"));
    }

    @Test
    public void page_mapsMpPageToCoreType_andFilters() {
        TenantContext.set(Arrays.asList(4L));
        NotificationRecord row = row("X", "op-1", null);
        row.setReadFlag("0");
        row.setPushedAt(new Date());
        Page<NotificationRecord> mp = new Page<>(1, 20, 1);
        mp.setRecords(Collections.singletonList(row));
        when(repo.select(Mockito.any(QueryWrapper.class), anyLong(), anyLong())).thenReturn(mp);

        NotificationPage out = store.page("op-1", 1, 20, null);

        assertEquals("行映射到 core 类型", 1, out.getRecords().size());
        assertEquals("total 透传", 1L, out.getTotal());
        assertEquals("X", out.getRecords().get(0).getMsgType());

        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).select(cap.capture(), Mockito.eq(1L), Mockito.eq(20L));
        String sql = sqlOf(cap.getValue());
        assertTrue("按收件人限定: " + sql, sql.contains("target_user"));
        assertTrue("valid 过滤: " + sql, sql.contains("valid"));
        assertTrue("租户恒定过滤: " + sql, sql.contains("IS NULL"));
        assertTrue("pushed_at 降序排序: " + sql, sql.contains("pushed_at"));
    }

    @Test
    public void unreadCount_alwaysFiltersUnreadFlag() {
        TenantContext.remove();
        when(repo.count(any(QueryWrapper.class))).thenReturn(0L);
        store.unreadCount("op-1");
        ArgumentCaptor<QueryWrapper<NotificationRecord>> cap =
                ArgumentCaptor.forClass((Class) QueryWrapper.class);
        verify(repo).count(cap.capture());
        assertTrue("无租户上下文也恒带未读过滤", sqlOf(cap.getValue()).contains("read_flag"));
    }
}
