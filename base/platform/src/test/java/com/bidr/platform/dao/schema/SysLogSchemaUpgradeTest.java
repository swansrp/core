package com.bidr.platform.dao.schema;

import org.junit.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Title: SysLogSchemaUpgradeTest
 * Description: sys_log v1（补 create_time 索引）这条升级腿的拼产物断言。
 * 验的是框架启动期真正走的那两个口——{@code getUpgradeScripts()} 注册的 SQL 形状，
 * 与 {@code MybatisPlusTableInitializerInf.shouldSkip} 对同一串的分词判定；
 * 判据落在"重复发版/运维已手工建过索引时必须跳过"，因为 shouldSkip 失配会退化成
 * Duplicate key name 报错（版本号不推进，每次启动都重跑）。
 * 真库执行（ALTER 是否建成）不属本类，属启动腿。
 *
 * @author Sharp
 * @since 2026/10/10
 */
public class SysLogSchemaUpgradeTest {

    private static final String TABLE = "sys_log";

    @Test
    public void upgradeRegistersVersionOneAddIndexScript() {
        SysLogSchema schema = new SysLogSchema();
        LinkedHashMap<Integer, String> scripts = schema.getUpgradeScripts();

        assertEquals("v1 是这张表的首个升级版本", 1, scripts.size());
        String sql = scripts.get(1);
        assertTrue("升级语句必须是 ADD INDEX 形态（shouldSkip 只认这个分词）: " + sql,
                sql.toUpperCase().contains("ADD INDEX"));
        assertTrue(sql.contains("`" + TABLE + "`"));
        assertTrue("索引名取列名，与同表其余 KEY 同口径: " + sql,
                sql.contains("ADD INDEX `create_time` (`create_time`)"));
    }

    @Test
    public void createDdlCarriesTheSameIndexForFreshInstalls() {
        // 新装库按 createDDL 建表就该带索引，否则同一版本的两套库形状不同、升级脚本对前者是空跳
        String create = new SysLogSchema().getCreateSql();
        assertTrue("createDDL 必须与 v1 升级后的形状一致: " + create,
                create.contains("KEY `create_time` (`create_time`)"));
    }

    @Test
    public void skipsWhenIndexAlreadyPresent() throws Exception {
        // 两种真实场景：重复发版（版本号与表结构不一致时重跑）、运维为不拖慢启动而提前手工建索引
        String sql = new SysLogSchema().getUpgradeScripts().get(1);
        assertTrue(new Probe().shouldSkip(metaDataWithIndexes("module_id", "create_time"), sql));
    }

    @Test
    public void runsWhenIndexMissingOnLiveTable() throws Exception {
        String sql = new SysLogSchema().getUpgradeScripts().get(1);
        assertFalse(new Probe().shouldSkip(metaDataWithIndexes("module_id", "request_ip"), sql));
    }

    @Test
    public void tableNameResolvesFromEntityAnnotation() {
        assertEquals(TABLE, new SysLogSchema().getTableName());
    }

    /** 只取 shouldSkip 这条默认方法，其余接口方法不碰 */
    private static class Probe implements com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf {
        @Override
        public String getTableName() {
            return TABLE;
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

    private static DatabaseMetaData metaDataWithIndexes(String... indexNames) throws Exception {
        Connection connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("inkhub");
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(metaData.getConnection()).thenReturn(connection);
        ResultSet rs = mock(ResultSet.class);
        // 前 n 次 next() 出 true（每行一个索引名），第 n+1 次收尾
        Boolean[] nexts = new Boolean[indexNames.length + 1];
        for (int i = 0; i < indexNames.length; i++) {
            nexts[i] = Boolean.TRUE;
        }
        nexts[indexNames.length] = Boolean.FALSE;
        when(rs.next()).thenReturn(nexts[0], Arrays.copyOfRange(nexts, 1, nexts.length));
        when(rs.getString("INDEX_NAME")).thenReturn(indexNames[0], Arrays.copyOfRange(indexNames, 1, indexNames.length));
        when(metaData.getIndexInfo(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(rs);
        return metaData;
    }
}
