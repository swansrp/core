package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.service.AgentExploreTools;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: AgentExploreToolsConnTest
 * Description: 探索工具连接借还语义用例（2026-08-23 断连事故的回归锚）：
 * 连接经 {@link AgentExploreTools.ConnSupplier} 每次工具执行时借、用完即还（close=归还池）——
 * ① 每次调用各借各还，不跨调用复用；② 供给器抛 SQLException（死池）→ 工具收口 JSON error，
 * 下一轮借新连接即恢复（原事故：任务期独占单连接闲置 628s 被服务端掐线，后续 19 轮全部
 * Connection is closed——错误粘住整个会话）；③ 供给器返回 null → 可读错误文案不裸抛 NPE；
 * ④ 只读守卫在借连接之前完成（拒绝语句不消耗借还）。
 * 纯 JDK 动态代理伪造 JDBC 四接口（Connection/PreparedStatement/ResultSet/ResultSetMetaData），
 * 不依赖 mockito 与外部数据源（同 SemanticCatalogFactsTest 范式）
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class AgentExploreToolsConnTest {

    private static SemanticLayer layer;

    @BeforeClass
    public static void setup() {
        Map<String, String> assets = new LinkedHashMap<>();
        assets.put("entities.json", "[{\"name\":\"ht\",\"display_name\":\"合同\",\"table\":\"db1.ht_contract\","
                + "\"fields\":[{\"name\":\"id\",\"type\":\"Int\",\"display_name\":\"ID\"},"
                + "{\"name\":\"lead_dept\",\"type\":\"String\",\"display_name\":\"牵头部门\",\"value_domain\":\"dept_dom\"},"
                + "{\"name\":\"sign_date\",\"type\":\"Date\",\"display_name\":\"签订日期\"}]}]");
        layer = SemanticLayer.fromContent(assets);
    }

    /** 每次工具执行各借一条连接、各还一条（两次 sample_rows → 借 2 还 2，无跨调用复用字段） */
    @Test
    public void borrowPerCallAndClosePerCall() {
        AtomicInteger borrowed = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        AgentExploreTools tools = new AgentExploreTools(() -> {
            borrowed.incrementAndGet();
            return fakeConn(closed);
        }, "t", layer.entities(), null, null, null);
        Assert.assertTrue(tools.sampleRows("db1.ht_contract", 1).contains("\"rows\""));
        Assert.assertTrue(tools.sampleRows("db1.ht_contract", 1).contains("\"rows\""));
        Assert.assertEquals("每次执行应各借一条连接", 2, borrowed.get());
        Assert.assertEquals("每条借出都应归还（close）", 2, closed.get());
    }

    /** 死供给一轮 → JSON error；下一轮借新连接即恢复（错误不粘住会话——事故回归锚） */
    @Test
    public void deadSupplierFailsOnceThenRecovers() {
        AtomicInteger calls = new AtomicInteger();
        AgentExploreTools tools = new AgentExploreTools(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new SQLException("Communications link failure");
            }
            return fakeConn(new AtomicInteger());
        }, "t", layer.entities(), null, null, null);
        String fail = tools.runSql("SELECT db1.ht_contract.id FROM db1.ht_contract");
        Assert.assertTrue("死连接应收口 JSON error: " + fail,
                fail.contains("\"error\"") && fail.contains("Communications link failure"));
        String ok = tools.runSql("SELECT db1.ht_contract.id FROM db1.ht_contract");
        Assert.assertTrue("下一轮借新连接应恢复: " + ok, ok.contains("\"rows\""));
    }

    /** 供给器返回 null → 可读文案收口，不裸抛 NPE */
    @Test
    public void nullSupplierReadableError() {
        AgentExploreTools tools = new AgentExploreTools(() -> null, "t", layer.entities(), null, null, null);
        String r = tools.sampleRows("db1.ht_contract", 1);
        Assert.assertTrue("应含可读错误文案: " + r, r.contains("error") && r.contains("数据源连接不可用"));
    }

    /** 只读守卫在借连接之前完成（写语句被拒时不消耗借还） */
    @Test
    public void writeRejectedBeforeBorrow() {
        AtomicInteger borrowed = new AtomicInteger();
        AgentExploreTools tools = new AgentExploreTools(() -> {
            borrowed.incrementAndGet();
            return fakeConn(new AtomicInteger());
        }, "t", layer.entities(), null, null, null);
        String r = tools.runSql("DELETE FROM db1.ht_contract");
        Assert.assertTrue("写语句应被守卫拒绝: " + r, r.contains("\"error\""));
        Assert.assertEquals("守卫拒绝不应借连接", 0, borrowed.get());
    }

    // ────────────────────────── 伪造 JDBC（仅实现工具执行路径触达的方法） ──────────────────────────

    private static Connection fakeConn(AtomicInteger closed) {
        return (Connection) Proxy.newProxyInstance(AgentExploreToolsConnTest.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "prepareStatement": return fakePs();
                        case "close": closed.incrementAndGet(); return null;
                        case "isClosed": return false;
                        case "toString": return "fakeConn";
                        case "hashCode": return System.identityHashCode(p);
                        case "equals": return p == a[0];
                        default: return null;
                    }
                });
    }

    private static PreparedStatement fakePs() {
        return (PreparedStatement) Proxy.newProxyInstance(AgentExploreToolsConnTest.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "executeQuery": return fakeRs();
                        case "close": return null;
                        case "toString": return "fakePs";
                        case "hashCode": return System.identityHashCode(p);
                        case "equals": return p == a[0];
                        default: return null;
                    }
                });
    }

    private static ResultSet fakeRs() {
        boolean[] hasNext = {true};
        return (ResultSet) Proxy.newProxyInstance(AgentExploreToolsConnTest.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getMetaData": return fakeMd();
                        case "next": boolean r = hasNext[0]; hasNext[0] = false; return r;
                        case "getObject": return "val";
                        case "getString": return "val";
                        case "close": return null;
                        case "wasNull": return false;
                        case "toString": return "fakeRs";
                        case "hashCode": return System.identityHashCode(p);
                        case "equals": return p == a[0];
                        default: return null;
                    }
                });
    }

    private static ResultSetMetaData fakeMd() {
        return (ResultSetMetaData) Proxy.newProxyInstance(AgentExploreToolsConnTest.class.getClassLoader(),
                new Class<?>[]{ResultSetMetaData.class}, (p, m, a) -> {
                    switch (m.getName()) {
                        case "getColumnCount": return 1;
                        case "getColumnLabel": return "v";
                        case "getColumnName": return "v";
                        default: return null;
                    }
                });
    }
}
