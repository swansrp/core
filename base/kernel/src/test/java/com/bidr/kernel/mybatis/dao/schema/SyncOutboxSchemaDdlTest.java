package com.bidr.kernel.mybatis.dao.schema;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: SyncOutboxSchemaDdlTest
 * Description: 变更事件发件箱建表面四道闸（T3.3a，JUnit4）。这些错都是"编译期看不出来、
 * 上线第一次调用才炸"的形态：
 * <ol>
 *   <li>实体每个映射列都真在 DDL 里——漏一列＝第一条 {@code record(...)} 就在落库线程报
 *       Unknown column，而调用方若无兜底，业务写路径跟着炸；</li>
 *   <li>列集与钉住的清单一致——发件箱列即对外契约，悄悄加/删列会让消费方反序列化静默丢字段；</li>
 *   <li>{@code occurred_at} 与它的索引在——保留期清理与"按发生时刻判新旧"都靠它，
 *       缺了只能按落库时刻裁，补录/积压场景会把还在用的增量提前删掉；</li>
 *   <li>DDL 不写 CHARSET/COLLATE/CHARACTER SET/ENGINE（本项目建表纪律，跟随库默认）。</li>
 * </ol>
 * <p>
 * 🔴 每组判据都配一条负对照腿（合成缺列 DDL 必须判红）：判据函数本身写错时，
 * 正向腿会因为"什么都没检查"而跑绿，这是守卫最常见的假绿形态。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
public class SyncOutboxSchemaDdlTest {

    /**
     * 发件箱的钉住列集：改这里＝改对外增量契约，必须同步消费方。
     */
    private static final Set<String> EXPECTED_COLUMNS = new HashSet<>(Arrays.asList(
            "id", "tenant_id", "agg_type", "agg_id", "event_type", "payload", "occurred_at", "create_at"));

    /**
     * 负对照用合成 DDL：结构同真 DDL，但缺 {@code tenant_id}（最容易"顺手漏掉"的一列）
     */
    private static final String SYNTHETIC_DDL_MISSING_TENANT_ID = "CREATE TABLE IF NOT EXISTS `sync_outbox`\n"
            + "(\n"
            + "    `id`          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT 'id',\n"
            + "    `agg_type`    varchar(64)  NOT NULL COMMENT '聚合类型',\n"
            + "    `agg_id`      bigint(20)   NOT NULL COMMENT '聚合标识',\n"
            + "    `event_type`  varchar(64)  NOT NULL COMMENT '事件类型',\n"
            + "    `payload`     text         DEFAULT NULL COMMENT '载荷',\n"
            + "    `occurred_at` datetime(3)  NOT NULL COMMENT '事件发生时刻',\n"
            + "    `create_at`   datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '落库时刻',\n"
            + "    PRIMARY KEY (`id`)\n"
            + ") COMMENT='缺 tenant_id 的负对照'";

    private final SyncOutboxSchema schema = new SyncOutboxSchema();

    @Test
    public void tableNameIsBareSyncOutbox() {
        Assert.assertEquals("sync_outbox", schema.getTableName());
        Assert.assertTrue("DDL 必须用 CREATE TABLE IF NOT EXISTS 形态（宿主每次启动都会执行它）",
                schema.getCreateSql().contains("CREATE TABLE IF NOT EXISTS `sync_outbox`"));
    }

    /** id 是增量游标的载体：必须自增（去掉自增＝插入期就错，游标单调性也失去依据） */
    @Test
    public void idIsAutoIncrement() {
        Assert.assertTrue("id 必须 AUTO_INCREMENT（增量游标依赖自增单调）",
                schema.getCreateSql().contains("AUTO_INCREMENT"));
    }

    /** 实体列 ⊆ DDL 列：漏列不会编译报错，只在落库线程炸 */
    @Test
    public void everyEntityFieldMapsToAnExistingColumn() {
        Set<String> ddlColumns = columnsOf(schema.getCreateSql());
        for (Field field : SyncOutbox.class.getDeclaredFields()) {
            String column = columnOf(field);
            if (column == null) {
                continue;
            }
            Assert.assertTrue("实体字段 " + field.getName() + " 映射到列 " + column + "，但 DDL 里没有这一列",
                    ddlColumns.contains(column));
        }
        Assert.assertEquals("DDL 列集与钉住的清单不一致（加/删列要同步本测试与消费方增量契约）",
                EXPECTED_COLUMNS, ddlColumns);
    }

    /** 负对照腿：列集判据对"缺 tenant_id"的合成 DDL 必须判红 */
    @Test
    public void columnSetCriterionFiresOnSyntheticDdlMissingTenantId() {
        // 🔴 先捕获再在外面断言：若把 Assert.fail 写在 try 里，它抛的 AssertionError 会被本 catch 吞掉，
        // 用例恒绿（2026-11-03 review 发现）
        AssertionError caught = null;
        try {
            assertColumnSetMatches(SYNTHETIC_DDL_MISSING_TENANT_ID);
        } catch (AssertionError e) {
            caught = e;
        }
        Assert.assertNotNull("列集判据对缺 tenant_id 的合成 DDL 报绿——判据已失效，正向腿的绿不可信", caught);
        Assert.assertTrue("负对照必须点名缺的列: " + caught.getMessage(),
                caught.getMessage().contains("tenant_id"));
        // 同一判据对真 DDL 必须通过：证明它不是"永远抛"的另一种失效
        assertColumnSetMatches(schema.getCreateSql());
    }

    /** tenant_id 建表即带：事件归属是消费方的解释依据，事后补列会让老行永久归属不明 */
    @Test
    public void tenantIdColumnExistsOnCreate() {
        Assert.assertTrue("DDL 必须带 tenant_id（空＝全局事件）",
                columnsOf(schema.getCreateSql()).contains("tenant_id"));
    }

    /** 保留期与新旧判定都按 occurred_at，索引必须有 */
    @Test
    public void occurredAtColumnAndItsIndexExist() {
        String ddl = schema.getCreateSql();
        Assert.assertTrue("缺 occurred_at：保留期只能按落库时刻裁", ddl.contains("`occurred_at`"));
        Assert.assertTrue("缺 idx_sync_outbox_occurred：清理会走全表扫", ddl.contains("idx_sync_outbox_occurred"));
    }

    /** 本项目建表纪律：不写字符集/引擎子句，跟随库默认 */
    @Test
    public void ddlCarriesNoCharsetOrEngineClause() {
        String ddl = schema.getCreateSql().toUpperCase();
        Assert.assertFalse("建表不得写字符集", ddl.contains("CHARSET"));
        Assert.assertFalse("建表不得写排序规则", ddl.contains("COLLATE"));
        Assert.assertFalse("建表不得写 CHARACTER SET", ddl.contains("CHARACTER SET"));
        Assert.assertFalse("建表不得写引擎", ddl.contains("ENGINE="));
    }

    /** 判据本体：真 DDL 与合成 DDL 走同一个函数（否则负对照证明不了正向腿） */
    private static void assertColumnSetMatches(String ddl) {
        Set<String> columns = columnsOf(ddl);
        Set<String> missing = new HashSet<>(EXPECTED_COLUMNS);
        missing.removeAll(columns);
        if (!missing.isEmpty()) {
            throw new AssertionError("DDL 缺列: " + missing);
        }
        Assert.assertEquals(EXPECTED_COLUMNS, columns);
    }

    /** 列名解析：本表 DDL 每列一行、列名在第一对反引号内 */
    private static Set<String> columnsOf(String ddl) {
        Set<String> columns = new HashSet<>();
        for (String line : ddl.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("`")) {
                int end = trimmed.indexOf('`', 1);
                if (end > 1) {
                    columns.add(trimmed.substring(1, end));
                }
            }
        }
        return columns;
    }

    /** 字段→列名：@TableId 优先，其次 @TableField（去掉反引号包裹） */
    private static String columnOf(Field field) {
        TableId tableId = field.getAnnotation(TableId.class);
        String raw = tableId != null ? tableId.value() : null;
        if (raw == null) {
            TableField tableField = field.getAnnotation(TableField.class);
            raw = tableField == null ? null : tableField.value();
        }
        return raw == null ? null : raw.replace("`", "");
    }
}