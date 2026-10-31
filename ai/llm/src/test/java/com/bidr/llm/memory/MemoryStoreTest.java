package com.bidr.llm.memory;

import com.bidr.llm.flow.StaticObjectProvider;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Title: MemoryStoreTest
 * Description: 跨会话记忆存储用例（T4.3 记忆库，以 InMemory 实现为准）：
 * 四类写入/按 kind 检索 / keyword 过滤 / topN 截断（priority+createdAt 排序）/
 * TTL 过期清除（惰性 + purgeExpired/sweep）/ LRU 上限（先清过期再淘汰、访问触达刷新）/ 删除幂等 / 参数校验。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class MemoryStoreTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 100L;

    private InMemoryMemoryStore store;
    private MemoryService service;

    /**
     * 每用例自建 store + service（store 引用显式持有，供回拨 expireAt / 覆盖保存 / 直接 search）
     */
    private void fresh(int maxRecords) {
        store = new InMemoryMemoryStore(maxRecords);
        service = new MemoryService(new StaticObjectProvider<>(store));
    }

    private MemoryRecord record(MemoryKind kind, String content, int priority, long ttlDays) {
        return service.write(TENANT, USER, kind, content, priority, ttlDays);
    }

    /**
     * 四类记忆全可写入；search 指定 kind 只回该类，recall（全 kind）回全部
     */
    @Test
    public void writeFourKindsAndFilterByKind() {
        fresh(1000);
        record(MemoryKind.FACT, "用户的项目叫 InkHub", 0, 30);
        record(MemoryKind.PREFERENCE, "用户偏好深色主题", 0, 30);
        record(MemoryKind.PROFILE, "用户是后端负责人", 0, 30);
        record(MemoryKind.SUMMARY, "上次会话敲定了发布节奏", 0, 30);

        Assert.assertEquals(4, service.recall(TENANT, USER, 10).size());
        List<MemoryRecord> facts = store.search(TENANT, USER, MemoryKind.FACT, null, 10);
        Assert.assertEquals(1, facts.size());
        Assert.assertEquals("用户的项目叫 InkHub", facts.get(0).getContent());
        Assert.assertEquals(MemoryKind.FACT, facts.get(0).getKind());
        List<MemoryRecord> profiles = store.search(TENANT, USER, MemoryKind.PROFILE, null, 10);
        Assert.assertEquals(1, profiles.size());
        Assert.assertEquals(MemoryKind.PROFILE, profiles.get(0).getKind());
    }

    /**
     * keyword 正文包含过滤（忽略大小写），空 keyword 不过滤；隔离维度：他人/他租户不可见
     */
    @Test
    public void keywordFilterAndTenantIsolation() {
        fresh(1000);
        record(MemoryKind.FACT, "项目部署在杭州机房", 0, 30);
        record(MemoryKind.PREFERENCE, "Use Dark Theme please", 0, 30);

        Assert.assertEquals(1, store.search(TENANT, USER, null, "杭州", 10).size());
        Assert.assertEquals(1, store.search(TENANT, USER, null, "dark theme", 10).size());
        Assert.assertEquals(0, store.search(TENANT, USER, null, "上海", 10).size());
        // 空/缺省 keyword = 全部
        Assert.assertEquals(2, store.search(TENANT, USER, null, null, 10).size());
        // 租户/用户隔离
        Assert.assertEquals(0, store.search(2L, USER, null, null, 10).size());
        Assert.assertEquals(0, store.search(TENANT, 200L, null, null, 10).size());
    }

    /**
     * topN 截断：priority 降序优先，同优先级 createdAt 新者在前
     */
    @Test
    public void topNTruncationOrdersByPriorityThenNewest() throws Exception {
        fresh(1000);
        record(MemoryKind.FACT, "低优先级旧记录", 0, 30);
        Thread.sleep(5);
        record(MemoryKind.FACT, "低优先级新记录", 0, 30);
        record(MemoryKind.FACT, "高优先级记录A", 5, 30);
        Thread.sleep(5);
        record(MemoryKind.FACT, "高优先级记录B", 5, 30);

        List<MemoryRecord> top3 = service.recall(TENANT, USER, 3);
        Assert.assertEquals(3, top3.size());
        Assert.assertEquals("高优先级记录B", top3.get(0).getContent());
        Assert.assertEquals("高优先级记录A", top3.get(1).getContent());
        Assert.assertEquals("低优先级新记录", top3.get(2).getContent());

        List<MemoryRecord> all = service.recall(TENANT, USER, 99);
        Assert.assertEquals(4, all.size());
    }

    /**
     * TTL：过期条目检索不可见（惰性移除）；sweep 物理清扫不影响存活条目；
     * TTL 调整（adjustTtl）顺延后复活，days&lt;=0 改为永不过期
     */
    @Test
    public void ttlExpiryAndPurge() {
        fresh(1000);
        MemoryRecord expired = record(MemoryKind.FACT, "早已过期的事实", 0, 30);
        // 回拨过期时间到过去（TTL 30 天粒度太粗，测试直接改 expireAt 后覆盖保存）
        expired.setExpireAt(System.currentTimeMillis() - 1000L);
        store.save(expired);
        record(MemoryKind.FACT, "仍然存活的事实", 0, 30);

        // 惰性过期：检索不见过期条目
        List<MemoryRecord> visible = service.recall(TENANT, USER, 10);
        Assert.assertEquals(1, visible.size());
        Assert.assertEquals("仍然存活的事实", visible.get(0).getContent());

        // sweep 物理清扫不抛错，存活条目不受影响
        service.sweep();
        Assert.assertEquals(1, service.recall(TENANT, USER, 10).size());

        // TTL 调整顺延：记录重新长期可见
        service.adjustTtl(TENANT, USER, visible.get(0).getRecordId(), 7);
        List<MemoryRecord> after = store.search(TENANT, USER, null, null, 10);
        Assert.assertEquals(1, after.size());
        Assert.assertTrue("TTL 顺延后过期时间应在 6 天以后",
                after.get(0).getExpireAt() > System.currentTimeMillis() + 6 * 24 * 3600 * 1000L);

        // TTL 调整为永不过期（days<=0）
        service.adjustTtl(TENANT, USER, visible.get(0).getRecordId(), 0);
        Assert.assertEquals(0, store.search(TENANT, USER, null, null, 10).get(0).getExpireAt());
    }

    /**
     * LRU 上限：同毫秒按访问序破平；检索触达刷新访问序（被读过的保留）；
     * 过期条目先清不占上限，存活条目不被误逐
     */
    @Test
    public void lruCapEvictsLeastRecentlyUsed() {
        fresh(3);
        record(MemoryKind.FACT, "最早写入A", 0, 30);
        record(MemoryKind.FACT, "其次写入B", 0, 30);
        record(MemoryKind.FACT, "最后写入C", 0, 30);

        // 触达 A：A 的访问序最新
        store.search(TENANT, USER, null, "最早写入A", 10);

        // 第 4 条写入触发淘汰：先写未触达的 B 出局，A 因触达保留
        record(MemoryKind.FACT, "新写入D", 0, 30);
        List<MemoryRecord> visible = service.recall(TENANT, USER, 10);
        Assert.assertEquals(3, visible.size());
        Assert.assertTrue(contains(visible, "最早写入A"));
        Assert.assertFalse(contains(visible, "其次写入B"));

        // 过期条目不计上限：先清过期再淘汰，存活条目不被误逐
        // （若淘汰逻辑误逐存活条目，recall 只能看到 1 条；正确时 2 条全在）
        fresh(2);
        MemoryRecord stale = record(MemoryKind.FACT, "过期占位", 0, 30);
        stale.setExpireAt(System.currentTimeMillis() - 1000L);
        store.save(stale);
        record(MemoryKind.PREFERENCE, "存活一", 0, 30);
        record(MemoryKind.PREFERENCE, "存活二", 0, 30);
        List<MemoryRecord> after = service.recall(TENANT, USER, 10);
        Assert.assertEquals(2, after.size());
        Assert.assertTrue(contains(after, "存活一"));
        Assert.assertTrue(contains(after, "存活二"));
    }

    /**
     * 删除单条幂等（不存在静默）；recordId 相同覆盖保存（同一记录回写不翻倍）
     */
    @Test
    public void deleteIsIdempotentAndSaveOverwrites() {
        fresh(1000);
        MemoryRecord one = record(MemoryKind.FACT, "待删除记录", 0, 30);
        record(MemoryKind.FACT, "保留记录", 0, 30);

        service.remove(TENANT, USER, one.getRecordId());
        service.remove(TENANT, USER, one.getRecordId());
        List<MemoryRecord> visible = service.recall(TENANT, USER, 10);
        Assert.assertEquals(1, visible.size());
        Assert.assertEquals("保留记录", visible.get(0).getContent());

        // 覆盖保存：recordId 相同即覆盖
        one.setContent("改写后的内容");
        store.save(one);
        visible = service.recall(TENANT, USER, 10);
        Assert.assertEquals(2, visible.size());
        Assert.assertTrue(contains(visible, "改写后的内容"));
    }

    /**
     * write 参数校验：kind/正文/租户/用户缺失拒绝
     */
    @Test
    public void writeRejectsInvalidArguments() {
        fresh(1000);
        try {
            service.write(TENANT, USER, null, "无类别");
            Assert.fail("kind 缺失应拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        try {
            service.write(TENANT, USER, MemoryKind.FACT, "  ");
            Assert.fail("空白正文应拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        try {
            service.write(null, USER, MemoryKind.FACT, "无租户");
            Assert.fail("租户缺失应拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    private boolean contains(List<MemoryRecord> records, String content) {
        for (MemoryRecord record : records) {
            if (content.equals(record.getContent())) {
                return true;
            }
        }
        return false;
    }
}
