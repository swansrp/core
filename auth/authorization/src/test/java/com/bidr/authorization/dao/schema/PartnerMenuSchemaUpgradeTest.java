package com.bidr.authorization.dao.schema;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: PartnerMenuSchemaUpgradeTest
 * Description: 方案B 棒1 两表 Schema 升级腿的拼产物断言（离线：不起 Spring、不连库，
 * 只验 {@code getUpgradeScripts()} 注册形状与 {@code getCreateSql()} 字面量——
 * 形态照 base/platform SysLogSchemaUpgradeTest 先例）。
 * <p>
 * 断言面：①{@code ac_menu} v1 必须注册且从 1 起、SQL 为 ADD COLUMN {@code app_code}
 * ＋ADD KEY 形态，create DDL 新装库同形状（否则同一版本两套库形状不同）；
 * ②{@code ac_partner} v1 同理，SQL 为 ADD UNIQUE KEY {@code uk_app_key} 形态，
 * create DDL 含唯一键且不得再出现旧普通 KEY 字面量；③shouldSkip 对 {@code ac_menu}
 * 升级腿按「列已存在」幂等跳过（M-1），对 {@code ac_partner} 升级腿按「唯一键已存在」
 * 幂等跳过（kernel ADD UNIQUE KEY 分支，I-1——fresh install 不跳就撞 1061 卡死版本闸），
 * 列/索引缺失都必须放行执行。
 * 真库执行（ALTER 是否建成/存量重复 app_key 会不会炸）不属本类，属启动腿。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-06
 */
public class PartnerMenuSchemaUpgradeTest {

    /** 只取 shouldSkip 这条默认方法，其余接口方法不碰（SysLogSchemaUpgradeTest 同款） */
    private static class Probe implements com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf {
        @Override
        public String getTableName() {
            return "ac_menu";
        }

        @Override
        public String getCreateSql() {
            return null;
        }

        @Override
        public LinkedHashMap<Integer, String> getUpgradeScripts() {
            return new LinkedHashMap<>();
        }
    }

    // ------------------------- ac_menu：app_code 归属应用列 -------------------------

    /** 腿1：ac_menu v1 升级腿已注册、版本从 1 起且连续，SQL 为 ADD COLUMN + ADD KEY 形态 */
    @Test
    public void acMenuUpgradeRegistersVersionOneAddColumnAndKey() {
        LinkedHashMap<Integer, String> scripts = new AcMenuSchema().getUpgradeScripts();

        assertVersionsStartAtOneAndContiguous("ac_menu", scripts);
        String sql = scripts.get(1);
        assertTrue("升级语句必须是 ADD COLUMN 形态（shouldSkip 列闸只认这个分词）: " + sql,
                sql.toUpperCase().contains("ADD COLUMN"));
        assertTrue("必须新增归属应用列 app_code: " + sql,
                sql.contains("ADD COLUMN `app_code`"));
        assertTrue("必须同腿补 app_code 索引: " + sql,
                sql.contains("ADD KEY `app_code` (`app_code`)"));
    }

    /** 腿2：ac_menu create DDL 与升级后形状一致——新装库直接带列与索引 */
    @Test
    public void acMenuCreateDdlCarriesAppCodeForFreshInstalls() {
        String create = new AcMenuSchema().getCreateSql();
        assertTrue("createDDL 必须与 v1 升级后的形状一致（列定义）: " + create,
                create.contains("`app_code` varchar(64) DEFAULT NULL"));
        assertTrue("createDDL 必须与 v1 升级后的形状一致（索引）: " + create,
                create.contains("KEY `app_code` (`app_code`)"));
    }

    /** 腿3：ac_menu 升级腿幂等——列已存在（重复发版/运维手工加过列）必须跳过 */
    @Test
    public void acMenuUpgradeSkipsWhenColumnAlreadyPresent() throws Exception {
        String sql = new AcMenuSchema().getUpgradeScripts().get(1);
        assertTrue(new Probe().shouldSkip(metaDataWithColumn("app_code", true), sql));
    }

    /** 腿4：ac_menu 升级腿在列缺失（存量库未升级）时必须放行执行 */
    @Test
    public void acMenuUpgradeRunsWhenColumnMissingOnLiveTable() throws Exception {
        String sql = new AcMenuSchema().getUpgradeScripts().get(1);
        assertFalse(new Probe().shouldSkip(metaDataWithColumn("app_code", false), sql));
    }

    // ------------------------- ac_partner：uk_app_key 唯一键 -------------------------

    /** 腿5：ac_partner v1 升级腿已注册、版本从 1 起且连续，SQL 为 ADD UNIQUE KEY 形态 */
    @Test
    public void acPartnerUpgradeRegistersVersionOneAddUniqueKey() {
        LinkedHashMap<Integer, String> scripts = new AcPartnerSchema().getUpgradeScripts();

        assertVersionsStartAtOneAndContiguous("ac_partner", scripts);
        String sql = scripts.get(1);
        assertTrue("升级语句必须是 ADD UNIQUE KEY 形态: " + sql,
                sql.toUpperCase().contains("ADD UNIQUE KEY"));
        assertTrue("必须把唯一键建在 app_key 上: " + sql,
                sql.contains("ADD UNIQUE KEY `uk_app_key` (`app_key`)"));
    }

    /** 腿6：ac_partner create DDL 含唯一键，且旧普通 KEY 形态不得残留 */
    @Test
    public void acPartnerCreateDdlCarriesUniqueKeyOnly() {
        String create = new AcPartnerSchema().getCreateSql();
        assertTrue("createDDL 必须与 v1 升级后的形状一致（唯一键）: " + create,
                create.contains("UNIQUE KEY `uk_app_key` (`app_key`)"));
        assertFalse("旧普通 KEY app_key 形态不得残留（唯一键语义就是这条腿的意义）: " + create,
                create.contains("KEY `app_key` (`app_key`)"));
    }

    /**
     * 腿7（I-1）：唯一键已存在 ⇒ shouldSkip=true——fresh install 的 create DDL 已带 uk_app_key，
     * 不跳过则升级腿重复执行撞 MySQL ERROR 1061 且版本闸卡 0，挡死后续全部升级腿。
     */
    @Test
    public void acPartnerUpgradeSkipsWhenUniqueKeyAlreadyPresent() throws Exception {
        String sql = new AcPartnerSchema().getUpgradeScripts().get(1);
        assertTrue(new Probe().shouldSkip(metaDataWithIndex("uk_app_key", true), sql));
    }

    /** 腿8（I-1 红腿）：唯一键缺失（存量库未升级）⇒ mustSkip=false，必须放行执行建键。 */
    @Test
    public void acPartnerUpgradeRunsWhenUniqueKeyMissingOnLiveTable() throws Exception {
        String sql = new AcPartnerSchema().getUpgradeScripts().get(1);
        assertFalse(new Probe().shouldSkip(metaDataWithIndex("uk_app_key", false), sql));
    }

    // ------------------------- 共用 -------------------------

    /**
     * 版本纪律：升级腿从 1 起且连续无跳号——跳号会让中间版本永久漏执行
     * （handleUpgradeDDL 按 targetVersion > currentVersion 顺序推进）。
     */
    private static void assertVersionsStartAtOneAndContiguous(String table,
                                                              LinkedHashMap<Integer, String> scripts) {
        assertFalse(table + " 必须注册至少一条升级腿", scripts.isEmpty());
        List<Integer> versions = scripts.keySet().stream().sorted().collect(Collectors.toList());
        assertEquals(table + " 首个升级版本必须是 1", Integer.valueOf(1), versions.get(0));
        for (int i = 1; i < versions.size(); i++) {
            assertEquals(table + " 升级版本必须连续（" + versions.get(i - 1) + " 后不得跳到 "
                            + versions.get(i) + "）",
                    Integer.valueOf(versions.get(i - 1) + 1), versions.get(i));
        }
    }

    /** stub metaData.getColumns：columnPresent 控制 rs.next()（单列升级腿足够） */
    private static DatabaseMetaData metaDataWithColumn(String column, boolean columnPresent)
            throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("core");
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getConnection()).thenReturn(connection);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(columnPresent);
        when(metaData.getColumns(any(), any(), eq("ac_menu"), eq(column))).thenReturn(rs);
        return metaData;
    }

    /**
     * stub metaData.getIndexInfo（indexExists 的取数口）：indexPresent 控制 rs.next()，
     * INDEX_NAME 回 uk_app_key（ac_partner v1 的 ADD UNIQUE KEY 腿足够）。
     */
    private static DatabaseMetaData metaDataWithIndex(String index, boolean indexPresent)
            throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("core");
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getConnection()).thenReturn(connection);
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(indexPresent, false);
        when(rs.getString("INDEX_NAME")).thenReturn(index);
        when(metaData.getIndexInfo(any(), any(), eq("ac_partner"), anyBoolean(), anyBoolean()))
                .thenReturn(rs);
        return metaData;
    }
}
