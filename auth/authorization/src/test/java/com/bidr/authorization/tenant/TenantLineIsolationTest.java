package com.bidr.authorization.tenant;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.exception.ServiceException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: TenantLineIsolationTest
 * Description: 多租户行级隔离（{@link TenantLineIsolationConfiguration}）离线单测——
 * 不起 Spring、不连库；隔离集用 {@code TableInfoHelper.initTableInfo} 静态注册探针实体
 * （先例：AcSsoRedirectServiceTest）。分层口径＝单测（真库 IT 归项目侧 PM1）。
 * <p>断言面：A 隔离集构建（标记接口驱动）/ B 文本预判 / C 4.6 空行保护 / D fail-closed /
 * E handler 语义三档与豁免 / F INSERT 恒不进 SQL 改写 ＋ F2 tenantId 的 fill 声明守卫
 * （2026-10-20 实测：自动注入炸、普通列填不进 SQL）/ G 当前租户解析。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-19
 */
public class TenantLineIsolationTest {

    /** 隔离面样例语句（探针表名） */
    private static final String ISOLATED_SELECT = "SELECT * FROM zzz_probe_isolated WHERE id = 1";
    private static final String FREE_SELECT = "SELECT * FROM zzz_probe_free WHERE id = 1";

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProbeIsolatedEntity.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                ProbeFreeEntity.class);
    }

    @Before
    public void setUp() {
        TenantContext.remove();
    }

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    // ==================== A 组：隔离集构建（标记接口驱动） ====================

    @Test
    public void a1_isolatedSet_containsMarkedEntityTable_andExcludesFree() {
        assertTrue("实现 TenantIsolated 的实体表必须进隔离集",
                TenantLineIsolationConfiguration.isolatedTables().contains("zzz_probe_isolated"));
        assertFalse("未实现标记的实体表不得进隔离集",
                TenantLineIsolationConfiguration.isolatedTables().contains("zzz_probe_free"));
    }

    @Test
    public void a2_bareName_stripsSchemaAndBackticks_andLowercases() {
        assertEquals("zzz_probe_isolated",
                TenantLineIsolationConfiguration.bareName("`epc_ai`.`ZZZ_Probe_Isolated`"));
        assertEquals("zzz_probe_isolated",
                TenantLineIsolationConfiguration.bareName("EPC_AI.ZZZ_PROBE_ISOLATED"));
        assertEquals("", TenantLineIsolationConfiguration.bareName(null));
    }

    // ==================== B 组：文本预判（快速放行） ====================

    @Test
    public void b1_targetsIsolatedTable_hitsBySubstring_caseInsensitive() {
        assertTrue(TenantLineIsolationConfiguration.targetsIsolatedTable(ISOLATED_SELECT));
        assertTrue("带库名前缀/反引号同样命中（子串级）",
                TenantLineIsolationConfiguration.targetsIsolatedTable(
                        "SELECT * FROM `epc_ai`.`ZZZ_PROBE_ISOLATED` t"));
        assertFalse(TenantLineIsolationConfiguration.targetsIsolatedTable(FREE_SELECT));
        assertFalse("null 安全", TenantLineIsolationConfiguration.targetsIsolatedTable(null));
    }

    @Test
    public void b2_shouldRewrite_offWhenIsolationOff() {
        TenantContext.set(Collections.singletonList(1L));
        assertTrue(TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.shouldRewrite(ISOLATED_SELECT));
        TenantContext.runWithoutIsolation(() -> assertFalse(
                "内部操作段整体免改写", TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor
                        .shouldRewrite(ISOLATED_SELECT)));
    }

    // ==================== C 组：JSqlParser 4.6 空行保护 ====================

    @Test
    public void c1_collapseBlankLines_compressesBlankLines_lfAndCrlf() {
        String lf = "INSERT INTO zzz_probe_isolated (tenant_id, name)\n\n\nVALUES (1, 'x')";
        assertNotSame("含连续空行必须重建字符串", lf,
                TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.collapseBlankLines(lf));
        assertFalse(TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.collapseBlankLines(lf)
                .contains("\n\n"));

        String crlf = "UPDATE zzz_probe_isolated SET name='x'\r\n\r\nWHERE id=1";
        assertFalse("CRLF 空行同样压缩（autocrlf=true 保护）",
                TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.collapseBlankLines(crlf)
                        .contains("\r\n\r\n"));
    }

    @Test
    public void c2_collapseBlankLines_keepsCleanSqlIdentity() {
        assertSame("无连续空行原样返回（同实例，零拷贝快路径）", ISOLATED_SELECT,
                TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.collapseBlankLines(ISOLATED_SELECT));
        assertNull(TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.collapseBlankLines(null));
    }

    // ==================== D 组：fail-closed（解析失败直接抛） ====================

    @Test
    public void d1_requireParseable_passesSingleStatement() {
        TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor
                .requireParseableInIsolationScope(ISOLATED_SELECT);
    }

    @Test
    public void d2_requireParseable_throwsOnMultiStatement() {
        try {
            TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor
                    .requireParseableInIsolationScope(ISOLATED_SELECT + "; DELETE FROM zzz_probe_isolated");
            fail("多语句解析失败必须抛（fail-closed，无 WARN 回退腿）");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("fail") || expected.getMessage().contains("解析失败"));
        }
    }

    @Test
    public void d3_isParseable_smoke() {
        assertTrue(TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.isParseable(ISOLATED_SELECT));
        assertFalse(TenantLineIsolationConfiguration.IsolatedTenantLineInterceptor.isParseable("NOT A SQL ;;;"));
    }

    // ==================== E 组：handler 语义三档与豁免 ====================

    private final TenantLineIsolationConfiguration.IsolatedTenantLineHandler handler =
            new TenantLineIsolationConfiguration.IsolatedTenantLineHandler();

    @Test
    public void e1_noTenantIds_isSystemLevel_ignoreEverything() {
        // 用户弹卡终裁口径：无上下文与空集同为旁路（系统管理级不拦截，2026-10-19 拍板沿用）
        assertTrue("无上下文（null 租户集）＝系统管理级", handler.ignoreTable("zzz_probe_isolated"));
        TenantContext.set(null);
        assertTrue("null 租户集＝旁路", handler.ignoreTable("zzz_probe_isolated"));
        TenantContext.set(Collections.emptyList());
        assertTrue("空租户集＝无归属用户＝旁路", handler.ignoreTable("zzz_probe_isolated"));
    }

    @Test
    public void e2_tenantUser_isolatedTable_notIgnored_freeTable_ignored() {
        TenantContext.set(Arrays.asList(1L, 2L));
        TenantContext.setCurrentTenantId(1L);
        assertFalse("租户用户 × 隔离表＝进改写", handler.ignoreTable("zzz_probe_isolated"));
        assertTrue("租户用户 × 非隔离表＝不受影响", handler.ignoreTable("zzz_probe_free"));
    }

    @Test
    public void e3_tenantUser_withoutCurrentTenant_getTenantIdThrows_selectTenantFirst() {
        TenantContext.set(Collections.singletonList(7L));
        // 未选当前租户：ignoreTable 仍放行到改写腿，由 getTenantId 抛（fail-closed 落点）
        assertFalse(handler.ignoreTable("zzz_probe_isolated"));
        try {
            handler.getTenantId();
            fail("租户集非空而未选当前租户必须抛「请先选择租户」");
        } catch (ServiceException expected) {
            assertTrue("提示语必须可行动：" + expected.getMessage(),
                    expected.getMessage().contains("请先选择租户"));
        }
    }

    @Test
    public void e4_platformTier_ignoresEverything() {
        TenantContext.set(Collections.singletonList(1L));
        TenantContext.markPlatform();
        assertTrue("平台档豁免（markPlatform）", handler.ignoreTable("zzz_probe_isolated"));
    }

    @Test
    public void e5_isolationOffSection_ignores_thenRestores() {
        TenantContext.set(Collections.singletonList(1L));
        TenantContext.setCurrentTenantId(1L);
        TenantContext.runWithoutIsolation(() ->
                assertTrue("内部段免改写", handler.ignoreTable("zzz_probe_isolated")));
        assertFalse("退出内部段恢复隔离", handler.ignoreTable("zzz_probe_isolated"));
    }

    @Test
    public void e6_runWithoutIsolation_nestedRestoresOuter() {
        TenantContext.runWithoutIsolation(() -> TenantContext.runWithoutIsolation(() -> { }));
        assertFalse("嵌套返回后不得把隔离开关残留为 ON", TenantContext.isIsolationOff());
    }

    @Test
    public void e7_runWithoutIsolation_restoresEvenOnException() {
        try {
            TenantContext.runWithoutIsolation(() -> {
                throw new RuntimeException("boom");
            });
            fail("异常必须穿透");
        } catch (RuntimeException ignored) {
            // 预期穿透
        }
        assertFalse("异常路径也必须恢复隔离开关", TenantContext.isIsolationOff());
    }

    @Test
    public void e8_getTenantId_returnsSelectedTenant() throws Exception {
        TenantContext.setCurrentTenantId(42L);
        assertEquals(42L, ((net.sf.jsqlparser.expression.LongValue) handler.getTenantId()).getValue());
    }

    // ==================== F 组：INSERT 不进 SQL 层改写（2026-10-20 实测排除自动注入） ====================

    private static List<Column> cols(String... names) {
        List<Column> list = new ArrayList<>();
        for (String name : names) {
            list.add(new Column(name));
        }
        return list;
    }

    @Test
    public void f1_ignoreInsert_alwaysTrue_becauseSqlInjectionIsBroken() {
        // 本项目 2026-10-20 试过"列清单缺 tenant_id 才注入"：MP 3.5.2 对普通 VALUES 形态也转手
        // processInsertSelect→cast PlainSelect→ClassCastException（ProgTenantIsolationIT 留档）。
        // 恒跳过＝写路径的值只能来自实体，配 f2 的 fill 声明守卫一起成立。
        assertTrue("缺列也跳过（不靠 SQL 注入）",
                handler.ignoreInsert(cols("code", "name"), "tenant_id"));
        assertTrue("带列同样跳过（值走 #{} 绑定）",
                handler.ignoreInsert(cols("code", "tenant_id"), "tenant_id"));
        assertTrue("无列清单形态不猜", handler.ignoreInsert(Collections.<Column>emptyList(), "tenant_id"));
        assertTrue("null 安全", handler.ignoreInsert(null, "tenant_id"));
    }

    // ==================== F2 组：tenantId 的 fill 声明守卫（补位能否落库的分水岭） ====================

    /** 有 tenantId 但没声明 fill＝填了也追不上已定型的列清单 */
    static class NoFillProbe implements TenantIsolated {
        private Long tenantId;
    }

    /** 声明成 UPDATE＝同样不可填 */
    static class WrongFillProbe implements TenantIsolated {
        @TableField(fill = FieldFill.UPDATE)
        private Long tenantId;
    }

    /** INSERT_UPDATE 可接受（列恒出现在 INSERT 里） */
    static class InsertUpdateProbe implements TenantIsolated {
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private Long tenantId;
    }

    /** 字段在父类：MP 的 TableInfo 同样收，守卫须按继承链找 */
    static class InheritedProbe extends InsertUpdateProbe {
    }

    /** 根本没有 tenantId 的标记实体：缺列那条腿（assertIsolatedEntityValid）负责，本守卫不得抢抛 */
    static class NoTenantFieldProbe implements TenantIsolated {
        private String name;
    }

    @Test
    public void f2_tenantFieldWithoutInsertFill_failsFastWithFixHint() {
        try {
            TenantLineIsolationConfiguration.assertTenantColumnFillDeclared(NoFillProbe.class);
            fail("无 fill 声明必须抛——否则框架补的 tenant_id 静默进不了 INSERT");
        } catch (IllegalStateException expected) {
            assertTrue("消息须给出修复方向：" + expected.getMessage(),
                    expected.getMessage().contains("FieldFill.INSERT"));
        }
        try {
            TenantLineIsolationConfiguration.assertTenantColumnFillDeclared(WrongFillProbe.class);
            fail("fill=UPDATE 同样不可填");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("UPDATE"));
        }
    }

    @Test
    public void f3_insertFillDeclaration_passes_includingInheritedAndInsertUpdate() {
        TenantLineIsolationConfiguration.assertTenantColumnFillDeclared(ProbeIsolatedEntity.class);
        TenantLineIsolationConfiguration.assertTenantColumnFillDeclared(InsertUpdateProbe.class);
        TenantLineIsolationConfiguration.assertTenantColumnFillDeclared(InheritedProbe.class);
    }

    @Test
    public void f4_guardStaysSilentWhenTenantFieldAbsent() {
        // 负向腿：本守卫只判声明；缺字段由 assertIsolatedEntityValid 抛，两道闸不重叠
        TenantLineIsolationConfiguration.assertTenantColumnFillDeclared(NoTenantFieldProbe.class);
    }

    // ==================== G 组：current tenant 解析（AuthLogin 收口复断） ====================

    @Test
    public void g1_parseCurrentTenantId_numberAndStringForms() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("TENANT_ID", 9L);
        assertEquals(Long.valueOf(9L), com.bidr.authorization.annotation.auth.AuthLogin.parseCurrentTenantId(map));
        map.put("TENANT_ID", "13");
        assertEquals(Long.valueOf(13L), com.bidr.authorization.annotation.auth.AuthLogin.parseCurrentTenantId(map));
        map.put("TENANT_ID", "abc");
        assertNull("脏值按缺键处理（fail-closed 由隔离面语句兜底）",
                com.bidr.authorization.annotation.auth.AuthLogin.parseCurrentTenantId(map));
        assertNull(com.bidr.authorization.annotation.auth.AuthLogin.parseCurrentTenantId(null));
        assertNull(com.bidr.authorization.annotation.auth.AuthLogin.parseCurrentTenantId(Collections.emptyMap()));
    }
}
