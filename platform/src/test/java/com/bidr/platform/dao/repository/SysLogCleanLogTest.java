package com.bidr.platform.dao.repository;

import com.bidr.platform.dao.entity.SysLog;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Title: SysLogCleanLogTest
 * Description: sys_log 批删走位的判定腿（不连库：读头与批删两个口被假实现替换）。
 * 覆盖的是"沿主键升序走到哪儿停"这条决策，不是 SQL 本身——
 * {@code readLogHead} 的投影/limit 与 {@code deleteByIds} 的 IN 批删属 DB 腿，本类不判。
 *
 * @author Sharp
 * @since 2026/10/10
 */
public class SysLogCleanLogTest {

    /** 假表读取批大小：刻意小于生产常量，好让"跨批推进游标"这条腿用少量行就能验 */
    private static final int FAKE_BATCH = 3;

    /** 一天毫秒数，用于造过期/新鲜两族行 */
    private static final long DAY = 24L * 60 * 60 * 1000;

    @Test
    public void nullBoundaryDeletesNothing() {
        FakeSysLogService service = new FakeSysLogService(row(1L, date(10)));
        SysLogService.CleanReport report = service.cleanLog(null, () -> 0L);
        assertEquals(0, report.deletedRows);
        assertEquals("边界为空时一条 SELECT 都不该发", 0, service.reads);
        assertTrue(service.deletedIds.isEmpty());
    }

    @Test
    public void walksStalePrefixAcrossBatchesAndStopsAtFresh() {
        // id 1-5 过期、6-7 新鲜：删 5 行、撞到 6 即停，7 根本不读
        FakeSysLogService service = new FakeSysLogService(
                row(1L, date(1)), row(2L, date(2)), row(3L, date(3)),
                row(4L, date(4)), row(5L, date(5)),
                row(6L, date(99)), row(7L, date(100)));

        SysLogService.CleanReport report = service.cleanLog(date(50), () -> 0L);

        assertEquals(Arrays.asList(1L, 2L, 3L, 4L, 5L), service.deletedIds);
        assertEquals(5, report.deletedRows);
        assertEquals("按批推进：5 行过期 + 1 行新鲜＝两批", 2, service.reads);
        assertEquals(Long.valueOf(6L), report.lastScannedId);
        assertFalse("走满新鲜区属正常收口，不算被预算截断", report.cutByBudget);
    }

    @Test
    public void headAlreadyFreshReadsOneBatchOnly() {
        // 稳态（ nightly 只剩新鲜数据）：只读一批就收，绝不扫全表——这是本次改造的全部意义
        FakeSysLogService service = new FakeSysLogService(
                row(10L, date(90)), row(11L, date(91)), row(12L, date(92)),
                row(13L, date(93)), row(14L, date(94)));

        SysLogService.CleanReport report = service.cleanLog(date(50), () -> 0L);

        assertEquals(0, report.deletedRows);
        assertEquals("表头即新鲜区，只发一次读", 1, service.reads);
        assertTrue(service.deletedIds.isEmpty());
    }

    @Test
    public void nullCreateTimeRowIsKeptButDoesNotBlock() {
        // 旧口径 le() 匹配不到 NULL ⇒ 依旧不删；但它不能变成路障，后面的过期行仍要删掉
        FakeSysLogService service = new FakeSysLogService(
                row(1L, null), row(2L, date(3)), row(3L, date(4)));

        SysLogService.CleanReport report = service.cleanLog(date(50), () -> 0L);

        assertEquals(Arrays.asList(2L, 3L), service.deletedIds);
        assertEquals(2, report.deletedRows);
    }

    @Test
    public void outOfOrderRowWaitsForNextNight() {
        // 异步 appender 的毫秒级乱序：id 2 比 id 3 新。撞到即停＝id 3 今晚留下，下一夜边界越过 id 2 后自会删掉
        FakeSysLogService service = new FakeSysLogService(
                row(1L, date(1)), row(2L, date(99)), row(3L, date(2)));

        SysLogService.CleanReport report = service.cleanLog(date(50), () -> 0L);

        assertEquals(Arrays.asList(1L), service.deletedIds);
        assertEquals(1, report.deletedRows);
    }

    @Test
    public void emptyTableCostsOneRead() {
        FakeSysLogService service = new FakeSysLogService();
        SysLogService.CleanReport report = service.cleanLog(date(50), () -> 0L);
        assertEquals(0, report.deletedRows);
        assertEquals(1, service.reads);
        assertNull(report.lastScannedId);
    }

    @Test
    public void timeBudgetCutsTheRunAndSaysSo() {
        FakeSysLogService service = new FakeSysLogService(
                row(1L, date(1)), row(2L, date(2)), row(3L, date(3)), row(4L, date(4)));
        // 首取 0ms，之后一律 6 分钟：第一批删完即触发耗时闸
        AtomicInteger calls = new AtomicInteger();
        LongSupplier clock = () -> calls.getAndIncrement() == 0 ? 0L : 6 * 60 * 1000L;

        SysLogService.CleanReport report = service.cleanLog(date(50), clock);

        assertEquals(Arrays.asList(1L, 2L, 3L), service.deletedIds);
        assertEquals(3, report.deletedRows);
        assertTrue("被预算截断必须显式报出来，否则运维以为删干净了", report.cutByBudget);
        assertEquals("剩余量留到下一夜，不能继续空转读第二批", 1, service.reads);
    }

    @Test
    public void freshRowWinsOverBudgetFlag() {
        FakeSysLogService service = new FakeSysLogService(row(1L, date(1)), row(2L, date(99)));
        SysLogService.CleanReport report = service.cleanLog(date(50), () -> 0L);
        assertEquals(Arrays.asList(1L), service.deletedIds);
        assertFalse(report.cutByBudget);
    }

    // ---- 假实现与造数 ----

    private static class FakeSysLogService extends SysLogService {
        private final List<SysLog> table = new ArrayList<>();
        private final List<Long> deletedIds = new ArrayList<>();
        private int reads;

        FakeSysLogService(SysLog... rows) {
            table.addAll(Arrays.asList(rows));
        }

        @Override
        protected List<SysLog> readLogHead(Long cursor) {
            reads++;
            List<SysLog> batch = new ArrayList<>();
            Iterator<SysLog> it = table.iterator();
            while (it.hasNext() && batch.size() < FAKE_BATCH) {
                SysLog row = it.next();
                if (cursor == null || row.getLogId() > cursor) {
                    batch.add(row);
                }
            }
            return batch;
        }

        @Override
        protected void deleteByIds(List<SysLog> rows) {
            for (SysLog row : rows) {
                deletedIds.add(row.getLogId());
                table.remove(row);
            }
        }
    }

    private static SysLog row(Long logId, Date createTime) {
        SysLog row = new SysLog();
        row.setLogId(logId);
        row.setCreateTime(createTime);
        return row;
    }

    private static Date date(long daysFromEpoch) {
        return new Date(daysFromEpoch * DAY);
    }
}
