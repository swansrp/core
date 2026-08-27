package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.service.TableProfiler;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: TableProfilerTest
* Description: 表画像采集纯函数回归——分区列识别（dy 优先/dm 月快照/dd 回落/均无）、键列识别
 * （primaryKey 优先/首个 *id 启发式/无键兜底）、画像渲染（年快照跨分区重复提示、
 * 日快照宽值域截断、无分区表全局唯一/重复、无键不炸）。背景：2026-08-23 om_revenue
 * 自主会话实证 R3~R13 共 10+ 轮全部花在表形态探测（数行数/探分区/判快照），下沉为
 * 骨架阶段确定性 SQL 后由画像文本注入提示词，此测试锁渲染契约不回退
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class TableProfilerTest {

    /** 实体构造助手：字段名清单 + 可选主键 */
    private static EntityDef entity(String table, List<String> pks, String... fields) {
        EntityDef e = new EntityDef();
        e.setName(table);
        e.setTable(table);
        List<EntityDef.EntityFieldDef> list = new ArrayList<>();
        for (String f : fields) {
            EntityDef.EntityFieldDef fd = new EntityDef.EntityFieldDef();
            fd.setName(f);
            list.add(fd);
        }
        e.setFields(list);
        e.setPrimaryKey(pks);
        return e;
    }

    // ---------------- detectPartition ----------------

    @Test
    public void detectPartitionPrefersDyOverDd() {
        EntityDef e = entity("dw_dim.t_dyf", null, "project_id", "dy", "dd", "name");
        Assert.assertEquals("dy", TableProfiler.detectPartition(e));
    }

    /** 2026-08-23 补：数仓月快照分区列 dm，粒度序 dy > dm > dd（dm 优先于 dd，退于 dy） */
    @Test
    public void detectPartitionPrefersDyOverMonthlyDm() {
        EntityDef e = entity("dw_dim.t_dmf", null, "project_id", "dm", "dy", "name");
        Assert.assertEquals("dy", TableProfiler.detectPartition(e));
    }

    @Test
    public void detectPartitionFallsToMonthlyDmBeforeDd() {
        EntityDef e = entity("dw_ods.t_dmf", null, "opportunityid", "dd", "dm", "name");
        Assert.assertEquals("dm", TableProfiler.detectPartition(e));
    }

    @Test
    public void detectPartitionFallsToDd() {
        EntityDef e = entity("dw_ods.t_ddf", null, "opportunityid", "dd", "name");
        Assert.assertEquals("dd", TableProfiler.detectPartition(e));
    }

    @Test
    public void detectPartitionNoneWithoutSnapshotColumns() {
        EntityDef e = entity("dw_dim.t_cfg", null, "code", "name");
        Assert.assertNull(TableProfiler.detectPartition(e));
    }

    /** 2026-08-23 补：实体字段 partition_column（骨架预选/人工确认都落这里）优先于启发式，
     *  即使表里同时存在 dy 列——人工结论不被启发式翻案 */
    @Test
    public void detectPartitionRespectsEntityField() {
        EntityDef e = entity("dw_dim.t", null, "project_id", "biz_date", "dy");
        e.setPartitionColumn("biz_date");
        Assert.assertEquals("biz_date", TableProfiler.detectPartition(e));
    }

    // ---------------- detectKey ----------------

    @Test
    public void detectKeyPrefersPrimaryKey() {
        EntityDef e = entity("dw_dim.t", Arrays.asList("biz_key"), "biz_key", "name");
        Assert.assertEquals("biz_key", TableProfiler.detectKey(e));
    }

    @Test
    public void detectKeyHeuristicTakesFirstIdField() {
        EntityDef e = entity("dw_dim.t_dyf", null, "name", "project_id", "ownerid", "dy");
        Assert.assertEquals("project_id", TableProfiler.detectKey(e));
    }

    @Test
    public void detectKeyNoneWhenNoIdField() {
        EntityDef e = entity("dw_dim.t", null, "code", "label");
        Assert.assertNull(TableProfiler.detectKey(e));
    }

    // ---------------- render：快照表（分区+键） ----------------

    /** 年快照跨分区重复（本次事故的原型：dim_om_project_dyf，键全局 distinct < 总行数 → 计数纪律提示） */
    @Test
    public void renderYearSnapshotCrossPartitionDuplicate() {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"2023", 6077L, 6077L},
                new Object[]{"2024", 8547L, 8547L},
                new Object[]{"2025", 10918L, 10918L},
                new Object[]{"2026", 12236L, 12236L});
        String text = TableProfiler.render("dw_dim.dim_om_project_dyf", "dy", "project_id", true,
                37778L, 12238L, rows);
        Assert.assertTrue("应含分区值域: " + text, text.contains("分区列 dy（4 个值：2023~2026）"));
        Assert.assertTrue("应含总行数与全局键数: " + text, text.contains("总 37778 行") && text.contains("全局 distinct 12238"));
        Assert.assertTrue("应含快照型计数纪律: " + text, text.contains("键跨分区重复（快照型表：按业务键计数/去重须限定单一分区）"));
        Assert.assertTrue("应含分区内唯一结论: " + text, text.contains("各分区内键唯一"));
        Assert.assertTrue("启发式键应标注: " + text, text.contains("（启发式）"));
        Assert.assertTrue("分区明细全列: " + text, text.contains("2023 行 6077/键 6077") && text.contains("2026 行 12236/键 12236"));
    }

    /** 日快照宽值域（61 个 dd 分区）截断：只展示最近 5 个 + min~max，防画像段刷屏上下文 */
    @Test
    public void renderDailySnapshotTruncatesWidePartitionValues() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= 61; i++) {
            String dd = String.format("2026%02d%02d", (i / 28) + 6, ((i - 1) % 28) + 1);
            rows.add(new Object[]{dd, 6700L + i, 6700L + i});
        }
        String text = TableProfiler.render("dw_ods.t_ddf", "dd", "tid", false,
                405856L, 6740L, rows);
        Assert.assertTrue("应含分区总数与值域: " + text,
                text.contains("61 个值：") && text.contains("~"));
        Assert.assertTrue("应提示截断: " + text, text.contains("分区值过多（61 个）只列最近 5 个"));
        Assert.assertTrue("最新分区应可见: " + text, text.contains("20260805 行 6761/键 6761"));
        Assert.assertFalse("早期分区不应出现: " + text, text.contains("20260601 行"));
    }

    /** 键全局唯一（无重复）：不输出快照纪律提示 */
    @Test
    public void renderUniqueKeysNeedNoSnapshotDiscipline() {
        List<Object[]> rows = Collections.singletonList(new Object[]{"2026", 100L, 100L});
        String text = TableProfiler.render("dw_dim.t_dyf", "dy", "kid", false, 100L, 100L, rows);
        Assert.assertTrue(text.contains("（全局唯一）"));
        Assert.assertFalse("无跨区重复不应提示快照纪律: " + text, text.contains("快照型"));
    }

    // ---------------- render：无分区表 ----------------

    @Test
    public void renderNoPartitionTableWithGloballyUniqueKey() {
        String text = TableProfiler.render("dw_dim.t_cfg", null, "code", false, 500L, 500L, new ArrayList<>());
        Assert.assertTrue("应含无分区与唯一性: " + text,
                text.contains("无分区列") && text.contains("（全局唯一）"));
    }

    @Test
    public void renderNoPartitionTableWithDuplicateKeys() {
        String text = TableProfiler.render("dw_dim.t_cfg", null, "code", true, 500L, 480L, new ArrayList<>());
        Assert.assertTrue(text.contains("（全局存在重复）"));
        Assert.assertTrue(text.contains("（启发式）"));
    }

    /** 无分区无键（极端：全文本配置表）：占位行不炸、不含 distinct 文案 */
    @Test
    public void renderNoPartitionNoKeyDoesNotBlow() {
        String text = TableProfiler.render("dw_dim.t_txt", null, null, false, 42L, -1L, new ArrayList<>());
        Assert.assertTrue("应含行数占位: " + text, text.contains("无分区列，无可用键列，共 42 行"));
        Assert.assertFalse(text.contains("distinct"));
    }

    /** 有分区无键：分布行加总行数、分区明细只写行数不写键 */
    @Test
    public void renderPartitionWithoutKeySumsFromRows() {
        List<Object[]> rows = Arrays.asList(
                new Object[]{"2023", 10L},
                new Object[]{"2024", 20L});
        String text = TableProfiler.render("dw_dim.t_dyf", "dy", null, false, 30L, -1L, rows);
        Assert.assertTrue("行数来自分布加总: " + text, text.contains("总 30 行"));
        Assert.assertTrue("分区明细无键列: " + text, text.contains("2023 行 10") && !text.contains("2023 行 10/键"));
    }
}
