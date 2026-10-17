package com.bidr.authorization.architecture;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.bidr.authorization.config.TenantDataGate;
import com.bidr.authorization.config.TenantGateConfiguration;
import com.bidr.authorization.dao.mapper.AcUserDao;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.config.db.TenantGateSuppressor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: TenantGateConventionTest
 * Description: 租户数据闸（批乙修1，M-23 载体修正后）离线守卫——纯 JUnit4 不起 Spring、不连库。
 * <p>A 组（handler 纯函数腿，走 {@code getSqlSegment} 直调，钉判序四态与机制核）：
 * <ul>
 * <li>腿 1 空 TenantContext ⇒ 同实例返回（老项目零感知机制核·null 态）；</li>
 * <li>腿 2 空集合档 ⇒ 同实例返回（用户弹卡终裁：空集与 null 同旁路、全量可见）；</li>
 * <li>腿 3 清单外表 ⇒ 同实例返回，含负对照针 ac_user_tenant；库名前缀 bidr.ac_user 必须命中；
 * 🔴 新增 Blocker 回归针：喂 {@code MappedStatement.getId()} 的"方法名形态"
 * （…AcUserDao.selectById）不得误命中锚点——修1 之前的载体错就错在该参数不是表名（M-23）；</li>
 * <li>腿 4 平台档 ⇒ 原样返回；</li>
 * <li>腿 5 正针：有档非空＋ac_user ⇒ 结果串含 EXISTS 与 ac_user_tenant 与 IN 值；</li>
 * <li>腿 6 文本预判（收紧为 FROM/JOIN 段词边界 \bac_user\b）与空行压缩。</li>
 * </ul>
 * <p>B 组（🔴 真实 interceptor 链整腿，M-23 验收形态）：mock {@link MappedStatement}/
 * {@link BoundSql} 直接调 {@code beforeQuery}，断言改写后的终串——
 * 腿 7 ac_user 查询终串含 EXISTS（ms id 喂方法名形态仍必须命中，证明判据来自解析出的真实表）；
 * 腿 8 ac_user_role/ac_user_dept 族不改写；腿 9 带别名 SQL 用别名列限定（解除旧限制的证据）；
 * 腿 10 无档/平台档旁路＋负对照；腿 11 {@code @InterceptorIgnore(dataPermission)} 旁路口
 * 真实链生效（M-24 的离线等价腿）＋负对照；腿 12 解析失败保守旁路不抛穿；
 * 腿 13 resolveAnchor 单元形态（库名限定/反引号/子查询 FROM）；
 * 腿 17 kernel 侧翻译读旁路位（{@code @BindRepo} 族展示值批查）在真实链生效＋帧外负对照。</p>
 * <p>C 组：腿 14 全模块 {@code @InterceptorIgnore} 命中数＝1 且住在 AcUserDao 的点名方法、
 * service 消费该口（M-24 窄口纪律，可 grep 针自证）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantGateConventionTest {

    private final TenantDataGate gate = new TenantDataGate();

    @Before
    public void setUp() {
        TenantContext.remove();
    }

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    private static Expression where(String sql) throws Exception {
        return CCJSqlParserUtil.parseCondExpression(sql);
    }

    // ==================== A 组：handler 纯函数腿 ====================

    /** 腿 1：空 TenantContext ⇒ 锚点表也原样返回，且必须是同一实例（老项目零感知机制核·null 态） */
    @Test
    public void emptyTenantContextReturnsSameWhereInstance() throws Exception {
        Expression original = where("status = '1'");
        TenantContext.remove();
        Assert.assertSame("无档必须原样返回同一实例，闸在 null 态不得改写",
                original, gate.getSqlSegment(original, "ac_user"));
    }

    /** 腿 2：空集合档 ⇒ 同实例返回（用户弹卡终裁：空集/无档＝旁路全量可见，不许改成恒假） */
    @Test
    public void emptyTenantIdsListAlsoBypasses() throws Exception {
        Expression original = where("status = '1'");
        TenantContext.set(Collections.<Long>emptyList());
        Assert.assertSame("空集合与 null 同旁路（终裁口径），不得筛成空结果",
                original, gate.getSqlSegment(original, "ac_user"));
    }

    /** 腿 3：清单外表原样返回；负对照针 ac_user_tenant；bidr.ac_user 前缀命中；
     *  🔴 Blocker 回归针：MP 传入的方法名形态（MappedStatement.getId()）不得误命中锚点 */
    @Test
    public void nonAnchorTablesReturnSameInstanceAndMsIdMethodFormIsNotAnchor() throws Exception {
        TenantContext.set(Arrays.asList(1L, 2L));
        Expression original = where("name = 'x'");
        Assert.assertSame("ac_role 不在锚点清单", original, gate.getSqlSegment(original, "ac_role"));
        Assert.assertSame("ac_user_tenant 自身不得被当锚点（contains/endsWith 误吞针）",
                original, gate.getSqlSegment(original, "ac_user_tenant"));
        Assert.assertSame("bidr.ac_user_tenant 前缀形态同样不得命中",
                original, gate.getSqlSegment(original, "bidr.ac_user_tenant"));
        Assert.assertTrue("bidr.ac_user 库名前缀必须兼容命中锚点",
                TenantDataGate.isAnchorTable("bidr.ac_user"));
        Assert.assertFalse("相邻词表 xxx_ac_user 不得被分段等值判定误吞",
                TenantDataGate.isAnchorTable("archive.ac_user_backup"));
        // M-23 证伪的单元面固化：第二参是 ms id（方法名形态），按表名判锚点必须判不中——
        // 运行时锚点判定唯一合法来源是 B 组解析出的真实表
        Assert.assertFalse("方法名形态的 MappedStatement.getId() 不得被当表名误命中",
                TenantDataGate.isAnchorTable("com.bidr.authorization.dao.mapper.AcUserDao.selectById"));
    }

    /** 腿 4：平台档放行——isPlatform=true 时锚点表也原样（AuthAdmin 通过路径的置位在此消费） */
    @Test
    public void platformTierReturnsWhereUnchanged() throws Exception {
        Expression original = where("status = '1'");
        TenantContext.set(Arrays.asList(1L), true);
        Assert.assertSame("平台档必须原样返回同一实例", original, gate.getSqlSegment(original, "ac_user"));
        // 负对照：只 set 档位不标平台 ⇒ 同一入口必须改判（证明平台档分支不是恒真旁路）
        TenantContext.set(Arrays.asList(1L), false);
        Assert.assertNotSame("非平台档同输入必须改写，平台档分支必须是真分支",
                original, gate.getSqlSegment(original, "ac_user"));
    }

    /** 腿 5：正针——有档非空＋ac_user ⇒ 结果含 EXISTS 与 ac_user_tenant 与 IN 值；无 where 时直接给闸表达式 */
    @Test
    public void tenantTierAppendsExistsGateOnAnchorTable() throws Exception {
        TenantContext.set(Arrays.asList(7L, 9L));
        Expression original = where("status = '1'");
        Expression rewritten = gate.getSqlSegment(original, "ac_user");
        Assert.assertNotSame("有档非空必须产出新表达式而非原实例", original, rewritten);
        String sql = rewritten.toString();
        Assert.assertTrue("改写串必须含 EXISTS，实际: " + sql, sql.contains("EXISTS"));
        Assert.assertTrue("改写串必须含关系表 ac_user_tenant，实际: " + sql, sql.contains("ac_user_tenant"));
        Assert.assertTrue("IN 值必须来自 TenantContext，实际: " + sql, sql.contains("7, 9"));
        Assert.assertTrue("必须与既有 where 用 AND 合并，实际: " + sql, sql.toUpperCase().contains("STATUS = '1'"));
        // 无 where 形态：直接返回闸本体
        Expression gateOnly = gate.getSqlSegment(null, "ac_user");
        Assert.assertNotNull(gateOnly);
        Assert.assertTrue(gateOnly.toString().contains("EXISTS"));
        // 负对照针自证：同表同 where，但无档 ⇒ 不得出现 EXISTS（证明正针非恒真）
        TenantContext.remove();
        Assert.assertFalse("负对照失效：无档时结果不应含 EXISTS",
                gate.getSqlSegment(original, "ac_user").toString().contains("EXISTS"));
    }

    /** 腿 6a：文本预判收紧——FROM/JOIN 段词边界命中才进改写候选；族表与字面量不碰；多语句/null 不碰 */
    @Test
    public void textPrecheckIsWordBoundedAndFromJoinScoped() {
        Assert.assertFalse("null SQL 不碰", TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(null));
        Assert.assertFalse("含分号多语句（启动种子 SQL）必须预判跳过",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(
                        "INSERT INTO ac_user (id) VALUES (1); INSERT INTO ac_role (id) VALUES (2);"));
        Assert.assertFalse("无关 SQL 不碰",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite("SELECT 1 FROM dual"));
        Assert.assertTrue("FROM ac_user 必须放行（大写兼容）",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite("SELECT * FROM bidr.AC_USER"));
        Assert.assertTrue("FROM `db`.`ac_user` 反引号形态放行",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(
                        "SELECT `id` FROM `bidr`.`ac_user`"));
        // 🔴 族表防空调针：词边界（下划线是 \w）使 ac_user_role/ac_user_dept 天然不命中
        Assert.assertFalse("ac_user_role 族表不得触发预判（防空转解析/解析炸面）",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(
                        "SELECT user_id, role_id FROM ac_user_role WHERE role_id = 1"));
        Assert.assertFalse("ac_user_dept 族表不得触发预判（JOIN 形态）",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(
                        "SELECT d.dept_id FROM ac_dept d JOIN ac_user_dept ud ON ud.user_id = d.id"));
        // 只对 FROM/JOIN 段匹配：字符串字面量里的 ac_user 不触发
        Assert.assertFalse("非 FROM/JOIN 段的 ac_user 字面量不得触发预判",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(
                        "SELECT note FROM sys_note WHERE note = 'ac_user'"));
        // JOIN ac_user 必须命中（真实表路径在 setWhere 里判）
        Assert.assertTrue("JOIN ac_user 形态放行",
                TenantGateConfiguration.TenantGateInnerInterceptor.shouldRewrite(
                        "SELECT d.id FROM ac_dept d JOIN ac_user u ON u.dept_id = d.id"));
    }

    /** 腿 6b：空行压缩——collapse 函数判据（连续三换行 ⇒ 单换行）＋压缩后 JSqlParser 4.6 可解析，且是纯空白变换 */
    @Test
    public void blankLineCollapseKeepsSqlParsableAndWhitespaceOnly() throws Exception {
        String raw = "SELECT id,\n\n\nname\n\n\nFROM ac_user\nWHERE status = '1'";
        String collapsed = TenantGateConfiguration.TenantGateInnerInterceptor.collapseBlankLines(raw);
        Assert.assertFalse("压缩后不得残留连续空行", collapsed.contains("\n\n"));
        Assert.assertEquals("collapse 必须是纯空白变换（去空白后文本逐字相同）",
                raw.replaceAll("\\s+", ""), collapsed.replaceAll("\\s+", ""));
        // 压缩后交 JSqlParser 4.6 解析必须成功（未压缩形态在 4.6 下必抛，见 collapseBlankLines 注释）
        CCJSqlParserUtil.parse(collapsed);
        // 无空行的正常 SQL 原样返回（幂等，不破坏既有文本）
        String plain = "SELECT * FROM ac_user WHERE id = 1";
        Assert.assertEquals(plain, TenantGateConfiguration.TenantGateInnerInterceptor.collapseBlankLines(plain));
    }

    // ==================== B 组：真实 interceptor 链整腿（M-23 验收形态） ====================

    private static final String DAO_ID_PREFIX = "com.bidr.authorization.dao.mapper.AcUserDao.";

    /** 走 MP 同款真实对象构造（不起 Spring）：BoundSql 可变 sql，MappedStatement 的 id 是方法名形态 */
    private static BoundSql runChain(String msId, String sql) throws Exception {
        Configuration configuration = new Configuration();
        BoundSql boundSql = new BoundSql(configuration, sql, new ArrayList<>(), null);
        MappedStatement ms = new MappedStatement.Builder(configuration, msId,
                parameterObject -> boundSql, SqlCommandType.SELECT).build();
        new TenantGateConfiguration.TenantGateInnerInterceptor(new TenantDataGate())
                .beforeQuery(null, ms, null, null, null, boundSql);
        return boundSql;
    }

    /** 腿 7（Blocker 整链回归针）：ms id 喂 selectPage 方法名形态，ac_user 查询终串仍必须含 EXISTS——
     *  证明锚点判定来自解析出的真实表而非第二参 */
    @Test
    public void realChainRewritesAcUserQueryIgnoringMsIdParameter() throws Exception {
        TenantContext.set(Arrays.asList(7L, 9L));
        String out = runChain(DAO_ID_PREFIX + "selectPage",
                "SELECT user_id, name FROM ac_user WHERE valid = ?").getSql();
        Assert.assertTrue("真实链终串必须含 EXISTS，实际: " + out, out.contains("EXISTS"));
        Assert.assertTrue("真实链终串必须含关系表，实际: " + out, out.contains("ac_user_tenant"));
        Assert.assertTrue("IN 值必须来自 TenantContext，实际: " + out, out.contains("7, 9"));
        Assert.assertTrue("无别名时用表名限定列，实际: " + out, out.contains("aut.user_id = ac_user.user_id"));
    }

    /** 腿 8：族表整链不改写（预判词边界生效后连解析都不进，文本一字不动） */
    @Test
    public void realChainLeavesFamilyTablesUntouched() throws Exception {
        TenantContext.set(Arrays.asList(1L, 2L));
        String roleSql = "SELECT user_id, role_id FROM ac_user_role WHERE role_id = ?";
        Assert.assertEquals("ac_user_role 不得被改写（文本必须逐字不动）",
                roleSql, runChain("com.bidr.authorization.dao.mapper.AcUserRoleDao.selectList", roleSql).getSql());
        String deptSql = "SELECT user_id, dept_id FROM ac_user_dept WHERE user_id = ?";
        Assert.assertEquals("ac_user_dept 不得被改写",
                deptSql, runChain("com.bidr.authorization.dao.mapper.AcUserDeptDao.selectList", deptSql).getSql());
        // 正对照：同 fixture 换 ac_user 表必须改写（证明上面两针不是恒"不动"）
        String out = runChain("com.bidr.authorization.dao.mapper.AcUserDao.selectList",
                "SELECT user_id FROM ac_user WHERE dept_id = ?").getSql();
        Assert.assertTrue("正对照失效：ac_user 同链未改写", out.contains("EXISTS"));
    }

    /** 腿 9：带别名 SQL 用别名列限定（M-23 解除旧限制的正面证据），且不得再出现原表名限定 */
    @Test
    public void realChainQualifiesExistsWithAlias() throws Exception {
        TenantContext.set(Arrays.asList(3L));
        String out = runChain(DAO_ID_PREFIX + "selectJoinList",
                "SELECT u.user_id FROM ac_user u WHERE u.valid = ?").getSql();
        Assert.assertTrue("别名限定必须落进 EXISTS，实际: " + out, out.contains("aut.user_id = u.user_id"));
        Assert.assertFalse("有别名时不得用原表名限定列（MySQL 会报错的旧限制形态）: " + out,
                out.contains("aut.user_id = ac_user.user_id"));
    }

    /** 腿 10：无档/平台档整链旁路——文本逐字不动（连压缩抖动都不发生，比"改写后原样"更强） */
    @Test
    public void realChainBypassesWithoutContextOrOnPlatformTier() throws Exception {
        String sql = "SELECT user_id FROM ac_user WHERE valid = ?";
        TenantContext.remove();
        Assert.assertEquals("无档必须原文不动", sql, runChain(DAO_ID_PREFIX + "selectList", sql).getSql());
        TenantContext.set(Arrays.asList(1L), true);
        Assert.assertEquals("平台档必须原文不动", sql, runChain(DAO_ID_PREFIX + "selectList", sql).getSql());
        TenantContext.set(Collections.<Long>emptyList());
        Assert.assertEquals("空集档与 null 同旁路（终裁）", sql, runChain(DAO_ID_PREFIX + "selectList", sql).getSql());
        // 负对照：同 sql 同 id 换非空有档 ⇒ 必须改写（证明上面三针不是恒"不动"）
        // 两参 set 显式清平台档（单参 set 不重置平台标记＝holder 既有语义）
        TenantContext.set(Arrays.asList(1L), false);
        Assert.assertTrue("负对照失效：有档同输入未改写",
                runChain(DAO_ID_PREFIX + "selectList", sql).getSql().contains("EXISTS"));
    }

    /**
     * 腿 11（M-24 集成等价腿）：{@code @InterceptorIgnore(dataPermission="true")} 的 mapper 方法
     * 走 MP 同款注册形态后，真实链必须旁路——addMember 的目标查在否/guard 的 operator 解析据此
     * 不再被闸改写（"单租户管理员加新人必成"的离线判据）；负对照＝同 SQL 喂普通方法 id 必须改写。
     */
    @Test
    public void realChainRespectsInterceptorIgnoredMapperMethod() throws Exception {
        Method ignored = AcUserDao.class.getMethod("selectTenantPortalUser", Long.class, String.class);
        // MP 解析 mapper 语句时的同款注册调用（接口无类级注解 ⇒ cache 传 null，方法级注解直接进全局表）
        InterceptorIgnoreHelper.initSqlParserInfoCache(null,
                "com.bidr.authorization.dao.mapper.AcUserDao", ignored);
        Assert.assertTrue("注册后 mustIgnore 判定必须生效（否则本腿空转）",
                InterceptorIgnoreHelper.willIgnoreDataPermission(
                        "com.bidr.authorization.dao.mapper.AcUserDao.selectTenantPortalUser"));
        TenantContext.set(Arrays.asList(1L));
        String sql = "SELECT * FROM ac_user WHERE user_id = ? LIMIT 1";
        Assert.assertEquals("旁路口不得被改写", sql,
                runChain("com.bidr.authorization.dao.mapper.AcUserDao.selectTenantPortalUser", sql).getSql());
        // 负对照：同 SQL 喂未标旁路的 id ⇒ 必须改写
        Assert.assertTrue("负对照失效：未旁路的 id 同 SQL 未改写",
                runChain("com.bidr.authorization.dao.mapper.AcUserDao.selectById", sql).getSql().contains("EXISTS"));
    }

    /** 腿 12：解析失败保守旁路——不抛穿 beforeQuery，SQL 保持不改写（老项目查询绝不炸挂） */
    @Test
    public void parseFailureBypassesConservatively() throws Exception {
        TenantContext.set(Arrays.asList(1L));
        String broken = "SELECT * FROM ac_user WHERE ((((((";
        String out = runChain(DAO_ID_PREFIX + "selectList", broken).getSql();
        Assert.assertFalse("解析失败不得产出改写串（必须保守旁路）: " + out, out.contains("EXISTS"));
    }

    /**
     * 腿 15（修3，R-A R2 Important 回归针）：嵌套括号 UNION 首支＝SetOperationList，
     * 父链 processSelect 分支 cast PlainSelect 抛 ClassCastException——RuntimeException catch 面必须
     * 接住不炸穿；同时固化"改写不下钻 UNION 支"的边界结论（reviewer 字节码证实 MP 会对每分支
     * 回调 setWhere，PlainSelect 支带不出闸外；本腿钉的是括号形态走保守旁路）。
     */
    @Test
    public void nestedUnionBranchBypassesWithoutThrowing() throws Exception {
        TenantContext.set(Arrays.asList(1L));
        String unionSql = "(SELECT 1 FROM ac_user UNION SELECT 2 FROM ac_user) UNION (SELECT 3 FROM ac_role)";
        String out = runChain(DAO_ID_PREFIX + "selectUnion", unionSql).getSql();
        Assert.assertFalse("CCE 属保守旁路面，不得产出改写串: " + out, out.contains("EXISTS"));
    }

    /** 腿 16（修3，R-A 轴二测试缺口①）：JOIN 锚定整链腿——主 FROM 非锚点、JOIN ac_user u ⇒
     *  真实链终串必须含 EXISTS 且以别名 u 限定列（旧形态只有 resolveAnchor 单元腿 13，无整链证据） */
    @Test
    public void realChainGatesJoinAnchoredTable() throws Exception {
        TenantContext.set(Arrays.asList(5L));
        String out = runChain(DAO_ID_PREFIX + "selectDeptJoin",
                "SELECT d.id FROM ac_dept d JOIN ac_user u ON u.dept_id = d.id WHERE d.valid = ?").getSql();
        Assert.assertTrue("JOIN 锚定整链必须挂闸，实际: " + out, out.contains("EXISTS"));
        Assert.assertTrue("别名限定必须落进 EXISTS，实际: " + out, out.contains("aut.user_id = u.user_id"));
    }

    /**
     * 腿 17（翻译读旁路，BindRepo 族）：有档非空但取数落在 {@link TenantGateSuppressor} 的调用帧内
     * ⇒ 真实链文本逐字不动（{@code gateActive()} 短路，连解析都不进）；
     * 负对照＝同 SQL 同 ms id 在帧外必须挂闸——证明旁路位是帧级开关，不是恒真。
     */
    @Test
    public void realChainBypassesInsideTranslationReadFrame() throws Exception {
        TenantContext.set(Arrays.asList(1L));
        Assert.assertTrue("帧外基线：有档非空闸必须活跃", TenantDataGate.gateActive());
        String sql = "SELECT user_id, name FROM ac_user WHERE customer_number IN (?)";
        String inFrame = TenantGateSuppressor.runSuppressed(() -> {
            Assert.assertFalse("帧内 gateActive 必须判不活跃", TenantDataGate.gateActive());
            try {
                return runChain(DAO_ID_PREFIX + "selectList", sql).getSql();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Assert.assertEquals("翻译读帧内不得改写", sql, inFrame);
        Assert.assertFalse("帧退出后旁路位必须已恢复", TenantGateSuppressor.isSuppressed());
        Assert.assertTrue("负对照失效：帧外同 SQL 未改写",
                runChain(DAO_ID_PREFIX + "selectList", sql).getSql().contains("EXISTS"));
    }

    /** 腿 13：resolveAnchor 单元形态——库名限定/反引号/别名/子查询 FROM 不命中 */
    @Test
    public void resolveAnchorHandlesQualifiedQuotedAndSubselectForms() throws Exception {
        Assert.assertEquals("反引号+库名限定必须剥引号后命中",
                "bidr.ac_user", anchorOf("SELECT id FROM `bidr`.`ac_user`").tableName);
        Assert.assertEquals("无别名时限定符＝全限定表名",
                "bidr.ac_user", anchorOf("SELECT id FROM `bidr`.`ac_user`").columnQualifier);
        Assert.assertEquals("别名优先做限定符",
                "t", anchorOf("SELECT id FROM ac_user AS t").columnQualifier);
        Assert.assertNull("FROM 子查询形态不改写（MP DataPermission 载体不下钻，保守旁路）",
                TenantDataGate.resolveAnchor(plainSelectOf("SELECT x FROM (SELECT id AS x FROM ac_user) s")));
        // JOIN 形态：主 FROM 非锚点、JOIN ac_user ⇒ 命中 JOIN 表
        Assert.assertEquals("JOIN 段 ac_user 必须被解析命中",
                "u", anchorOf("SELECT d.id FROM ac_dept d JOIN ac_user u ON u.dept_id = d.id").columnQualifier);
    }

    // ==================== C 组：M-24 窄口 grep 针 ====================

    /** 计数判定函数（针自证用）：返回 content 中 needle 出现次数 */
    private static int countOccurrences(String content, String needle) {
        int count = 0;
        int idx = content.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = content.indexOf(needle, idx + needle.length());
        }
        return count;
    }

    /** 腿 14：全模块 main 源码 {@code @InterceptorIgnore} 命中数必须＝1，且住在 AcUserDao 的点名方法；
     *  AcTenantPortalService 必须消费该旁路口（防"注解删了但注释还吹"的漂移） */
    @Test
    public void interceptorIgnoreIsExactlyOneNamedPortalMethod() throws IOException {
        Path moduleRoot = locateAuthorizationModule();
        Path mainSrc = moduleRoot.resolve("src/main/java");
        List<Path> annotatedFiles;
        try (java.util.stream.Stream<Path> stream = Files.walk(mainSrc)) {
            annotatedFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return new String(Files.readAllBytes(p), StandardCharsets.UTF_8)
                                    .contains("@InterceptorIgnore");
                        } catch (IOException e) {
                            throw new IllegalStateException("读取源码失败: " + p, e);
                        }
                    })
                    .collect(Collectors.toList());
        }
        int total = 0;
        for (Path file : annotatedFiles) {
            total += countOccurrences(new String(Files.readAllBytes(file), StandardCharsets.UTF_8),
                    "@InterceptorIgnore(dataPermission");
        }
        Assert.assertEquals("@InterceptorIgnore(dataPermission 全模块命中数必须＝点名 1 个方法（M-24 窄口纪律），实际命中文件: "
                        + annotatedFiles, 1, total);
        String daoSource = new String(Files.readAllBytes(
                        mainSrc.resolve("com/bidr/authorization/dao/mapper/AcUserDao.java")), StandardCharsets.UTF_8);
        Assert.assertTrue("旁路口 selectTenantPortalUser 必须住在 AcUserDao",
                daoSource.contains("selectTenantPortalUser"));
        String serviceSource = new String(Files.readAllBytes(moduleRoot.resolve(
                        "src/main/java/com/bidr/authorization/service/tenant/AcTenantPortalService.java")),
                StandardCharsets.UTF_8);
        Assert.assertEquals("AcTenantPortalService 必须消费旁路口（三处取数：guard operator 解析＋addMember 目标查在否＋listMembers 展示列，修3 R-C C-1）",
                3, countOccurrences(serviceSource, "selectTenantPortalUser("));
        // 判定函数自证：计数函数喂合成样本必须数得出（防恒 0 空转）
        Assert.assertEquals("计数函数失效", 2,
                countOccurrences("@InterceptorIgnore x @InterceptorIgnore", "@InterceptorIgnore"));
        Assert.assertEquals("计数函数负对照失效", 0,
                countOccurrences("nothing here", "@InterceptorIgnore"));
    }

    // ==================== 工具 ====================

    private static TenantDataGate.AnchorRef anchorOf(String sql) throws Exception {
        TenantDataGate.AnchorRef anchor = TenantDataGate.resolveAnchor(plainSelectOf(sql));
        Assert.assertNotNull("resolveAnchor 必须命中: " + sql, anchor);
        return anchor;
    }

    private static PlainSelect plainSelectOf(String sql) throws Exception {
        Statement statement = CCJSqlParserUtil.parse(sql);
        Assert.assertTrue("夹具必须是 SELECT: " + sql, statement instanceof Select);
        net.sf.jsqlparser.statement.select.SelectBody body = ((Select) statement).getSelectBody();
        Assert.assertTrue("夹具主块必须是 PlainSelect: " + body.getClass(), body instanceof PlainSelect);
        return (PlainSelect) body;
    }

    /** authorization 模块根定位（判据同 AuthAdminGateConventionTest：目录名＋pom＋src/main/java，定位失败直接报红） */
    private static Path locateAuthorizationModule() {
        Path start = Paths.get(System.getProperty("basedir",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if ("authorization".equals(dir.getFileName().toString())
                    && Files.isRegularFile(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
        }
        throw new IllegalStateException("无法从 " + start + " 定位 core/authorization 模块根（拒绝空扫描假绿）");
    }
}
