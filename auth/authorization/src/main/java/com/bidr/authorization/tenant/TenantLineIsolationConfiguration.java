package com.bidr.authorization.tenant;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.config.db.MybatisPlusInterceptorCustomizer;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Title: TenantLineIsolationConfiguration
 * Description: 多租户行级隔离装配（2026-10-19 下沉 core，参照 ERP 试点
 * {@code TenantIsolationConfiguration} 已验证形态，两处有意差异见下）。
 *
 * <p><b>装配纪律（D21 同款安全口径）</b>：{@code @ConditionalOnProperty(
 * prefix="my.tenant", name="line-enabled", havingValue="true")}——<b>默认不装配</b>，
 * 已上线项目零影响；启用方 yml 显式 {@code my.tenant.line-enabled: true}。</p>
 *
 * <p><b>隔离目标=标记接口驱动（与 ERP 前缀减法有意不同）</b>：实体实现
 * {@link TenantIsolated} ⇒ 其表进改写集（各功能模块自声明，无需中央登记；
 * 不带标记的表零影响）。隔离表名集从 MyBatis-Plus {@code TableInfoHelper}
 * 注册表懒构建（Mapper 全部初始化后 SQL 才会执行，懒构建时机安全；
 * 缓存随注册总数变化自动重建）。</p>
 *
 * <p><b>租户语义三档（用户 2026-10-19 拍板）</b>：①用户不属于任何租户
 * （{@code TenantContext.getTenantIds()} 为 null/空）＝系统管理级，不拦截；
 * ②属于租户但未选当前租户＝fail-closed，隔离表语句报"请先选择租户"；
 * ③平台档（{@code AuthAdmin} → {@code markPlatform}）豁免。另有内部操作段
 * {@code TenantContext.runWithoutIsolation} 与语句级
 * {@code @InterceptorIgnore(tenantLine="true")}（MP 原生）两个显式豁免通道。</p>
 *
 * <p><b>与 ERP 形态的其余要点照搬</b>：JSqlParser 4.6 连续空行炸 →
 * {@link #collapseBlankLines} 纯空白变换；非隔离面语句文本预判快速放行（不进解析器）；
 * 隔离面语句解析失败 fail-closed 直接抛（无 WARN 回退腿——静默放行即跨租户读写通道）；
 * SQL 层 INSERT 恒不自动填 {@code tenant_id}（{@code ignoreInsert} 照搬——本项目 2026-10-20
 * 试过"缺列即注入"，被 MP 3.5.2 的 VALUES 形态炸成 ClassCastException，理由已实测钉死）。</p>
 *
 * <p><b>写侧落点（2026-10-20 口径变更）</b>：{@code tenant_id} 的补齐与跨租户校验由
 * {@link TenantIsolationFillHandler} 在实体填充链负责——取代原先"业务每处手写
 * {@code setTenantId(requireCurrentTenantId())}"的纪律。前提是隔离实体把 {@code tenantId}
 * 声明成 {@code @TableField(fill = FieldFill.INSERT)}（普通列的拼列早于填充链，缺声明则填了
 * 也进不了 SQL），该前提由 {@link #assertTenantColumnFillDeclared} 首用 fail-fast 守住。
 * 裁定原文＝<b>不指定租户＝不能建数据</b>（写闸只认"有无当前租户"，平台档/系统管理级都不例外——
 * 用户 2026-10-20 三轮撤回「平台管理员可切任一有效租户」，平台档没有特殊建数据通道；
 * 读侧豁免不变）。</p>
 *
 * <p><b>异常族（2026-10-20 用户拍板）</b>：用户可触发的拒绝（未选租户建数据／跨租户写入／
 * 读侧"请先选择租户"）一律 {@code ServiceException}——框架口径只有它的 message 进得了前端
 * details；开发期契约守卫与隔离面解析失败保持 {@code IllegalStateException}（机制故障，
 * 不该伪装成用户的操作不对）。</p>
 *
 * <p><b>链位保障</b>：经 kernel {@link MybatisPlusInterceptorCustomizer} 扩展点注入，
 * 物理位于链 [0]（先于分页/动态表名）；不再用 BeanPostProcessor 重建链
 * （kernel 已留扩展点，避免与既有 TenantDataGate 的链重建相互覆盖）。</p>
 *
 * <p><b>缺口纪律（ERP A36/A37，写路径用户自查）</b>：MP 3.5.2 租户改写对
 * {@code UPDATE t1 JOIN t2} 只拼主表侧、{@code INSERT}（含 {@code ..SELECT}）整体不进改写面——
 * 隔离表跨表写一律拆两步（先 SELECT 出主键再按主键单表写），勿在本拦截器上赌。</p>
 *
 * @author Sharp
 * @since 2026-10-19
 */
@Configuration
@ConditionalOnProperty(prefix = "my.tenant", name = "line-enabled", havingValue = "true")
public class TenantLineIsolationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TenantLineIsolationConfiguration.class);

    /**
     * 经 kernel 扩展点把租户行级隔离器插到拦截器链最前（先于分页/动态表名）。
     * 本 bean 仅在 {@code my.tenant.line-enabled=true} 时存在——未启用项目链不变。
     */
    @Bean
    public MybatisPlusInterceptorCustomizer tenantLineInterceptorCustomizer() {
        log.info("[tenant] 多租户行级隔离已启用（my.tenant.line-enabled=true，TenantIsolated 标记驱动）");
        return interceptor -> interceptor.addInnerInterceptor(new IsolatedTenantLineInterceptor());
    }

    /**
     * 写侧配套：{@code tenant_id} 的补齐与跨租户校验交给填充链，业务不必逐处手写赋值。
     * 同条件下才注册——框架 {@code MetaObjectHandlerManager}（@Primary）按 bean 列表聚合，
     * 未启用项目不产生该 bean，填充链一步不变。
     */
    @Bean
    public MetaObjectHandler tenantIsolationFillHandler() {
        return new TenantIsolationFillHandler();
    }

    // ==================== 隔离表名集（TenantIsolated 标记驱动） ====================

    /** 已缓存的隔离表名（裸表名小写）；随 TableInfo 注册总数变化自动重建 */
    private static volatile Set<String> cachedIsolatedTables = Collections.emptySet();
    private static volatile int cachedTableInfoSize = -1;

    /**
     * 当前隔离表名集：从 MP TableInfo 注册表收集"实体实现 {@link TenantIsolated}"的表名。
     * 裸表名小写口径（{@code TableInfo.getTableName()} 不含库名前缀；防御性再剥一次见
     * {@link #bareName}）。注册总数未变时直接复用缓存（每语句一次的读路径代价可忽略）。
     * <p>构建时顺带跑 {@link #assertIsolatedEntityValid}（fail-fast：标记实体缺
     * {@code tenantId} 字段＝链路事故，首用即红，绝不静默漏隔离）与
     * {@link #assertTenantColumnFillDeclared}（缺 {@code fill} 声明＝填充链补的值进不了 SQL）。</p>
     */
    static Set<String> isolatedTables() {
        List<TableInfo> tableInfos = TableInfoHelper.getTableInfos();
        int size = tableInfos.size();
        if (size != cachedTableInfoSize) {
            Set<String> tables = new HashSet<>();
            for (TableInfo info : tableInfos) {
                Class<?> entityType = info.getEntityType();
                if (entityType != null && TenantIsolated.class.isAssignableFrom(entityType)) {
                    assertIsolatedEntityValid(entityType, propertiesOf(info));
                    assertTenantColumnFillDeclared(entityType);
                    tables.add(bareName(info.getTableName()));
                }
            }
            cachedIsolatedTables = Collections.unmodifiableSet(tables);
            cachedTableInfoSize = size;
        }
        return cachedIsolatedTables;
    }

    /** TableInfo 的属性名清单（含主键外字段）；离线测试走合成清单，不依赖注册表 */
    private static List<String> propertiesOf(TableInfo info) {
        List<String> props = new ArrayList<>();
        if (info.getKeyProperty() != null) {
            props.add(info.getKeyProperty());
        }
        info.getFieldList().forEach(field -> props.add(field.getProperty()));
        return props;
    }

    /**
     * 守卫（项目硬纪律，判据必须证明自己活着）：实现 {@link TenantIsolated} 的实体
     * 必须有 {@code tenantId} 属性（⇒ {@code tenant_id} 列），否则拦截器拼列即
     * Unknown column。缺列直接抛（首用 fail-fast），消息带实体名与修复指引。
     */
    static void assertIsolatedEntityValid(Class<?> entityType, List<String> properties) {
        for (String property : properties) {
            if ("tenantId".equals(property)) {
                return;
            }
        }
        throw new IllegalStateException("[tenant] 实体 " + entityType.getName()
                + " 实现了 TenantIsolated 但没有 tenantId 属性（tenant_id 列）——"
                + "拦截器拼列必然 Unknown column，请补列或摘除标记（fail-fast，勿静默漏隔离）");
    }

    /**
     * 守卫（2026-10-20 实测教训）：{@code tenantId} 必须声明
     * {@code @TableField(fill = FieldFill.INSERT)}（或 {@code INSERT_UPDATE}）。
     * <p>没有该声明时 MP 把该列当普通列，走 {@code <if test="tenantId != null">} 动态拼列，
     * 而列清单在 BoundSql 生成期就定型、早于实体填充链——{@link TenantIsolationFillHandler}
     * 补进实体的值追不上列清单，漏填时由库侧报 {@code Field 'tenant_id' doesn't have a default
     * value}；带该声明时列恒出现在 INSERT 里，绑定发生在填充之后，框架补位才真的落库。
     * SQL 层自动注入那条路已在同一轮实测中排除（见
     * {@link IsolatedTenantLineHandler#ignoreInsert}）。首用即红，不靠人记。</p>
     */
    static void assertTenantColumnFillDeclared(Class<?> entityType) {
        for (Class<?> cursor = entityType; cursor != Object.class && cursor != null;
             cursor = cursor.getSuperclass()) {
            for (java.lang.reflect.Field field : cursor.getDeclaredFields()) {
                if (!"tenantId".equals(field.getName())) {
                    continue;
                }
                TableField annotation = field.getAnnotation(TableField.class);
                FieldFill fill = annotation == null ? FieldFill.DEFAULT : annotation.fill();
                if (fill != FieldFill.INSERT && fill != FieldFill.INSERT_UPDATE) {
                    throw new IllegalStateException("[tenant] 实体 " + entityType.getName()
                            + " 的 tenantId 必须声明 @TableField(fill = FieldFill.INSERT)，实际为 "
                            + fill + "——普通列的拼列早于实体填充链，缺该声明则框架补的 tenant_id "
                            + "进不了 INSERT（漏填只剩库侧 NOT NULL 报错）");
                }
                return;
            }
        }
        // 无 tenantId 字段的情况由 assertIsolatedEntityValid 负责，此处不重复抛
    }

    /** SQL 文本是否涉及隔离面：含任一隔离表名即 true（子串级判定，假阳由 ignoreTable 扣减） */
    static boolean targetsIsolatedTable(String sql) {
        if (sql == null) {
            return false;
        }
        Set<String> tables = isolatedTables();
        if (tables.isEmpty()) {
            return false;
        }
        String lower = sql.toLowerCase(Locale.ROOT);
        for (String table : tables) {
            if (lower.contains(table)) {
                return true;
            }
        }
        return false;
    }

    /** 防御性剥库名前缀与反引号：匹配键一律裸表名小写 */
    static String bareName(String tableName) {
        if (tableName == null) {
            return "";
        }
        String name = tableName.toLowerCase(Locale.ROOT).replace("`", "").trim();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return name;
    }

    // ==================== 拦截器（快速放行 + 4.6 空行保护 + fail-closed） ====================

    static class IsolatedTenantLineInterceptor extends TenantLineInnerInterceptor {

        private static final Logger log = LoggerFactory.getLogger(IsolatedTenantLineInterceptor.class);

        IsolatedTenantLineInterceptor() {
            super(new IsolatedTenantLineHandler());
        }

        /** 是否执行改写：涉及隔离面 且 非豁免态（平台档/空租户集/隔离开关段在 handler 侧再兜一层） */
        static boolean shouldRewrite(String sql) {
            return targetsIsolatedTable(sql) && !TenantContext.isIsolationOff();
        }

        @Override
        public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                                ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
            String sql = boundSql.getSql();
            if (!shouldRewrite(sql)) {
                return;
            }
            String collapsed = collapseBlankLines(sql);
            requireParseableInIsolationScope(collapsed);
            PluginUtils.mpBoundSql(boundSql).sql(collapsed);
            super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        }

        @Override
        public void beforePrepare(StatementHandler statementHandler, Connection connection,
                                  Integer transactionTimeout) {
            BoundSql boundSql = PluginUtils.mpStatementHandler(statementHandler).boundSql();
            String sql = boundSql.getSql();
            if (!shouldRewrite(sql)) {
                return;
            }
            String collapsed = collapseBlankLines(sql);
            requireParseableInIsolationScope(collapsed);
            PluginUtils.mpBoundSql(boundSql).sql(collapsed);
            super.beforePrepare(statementHandler, connection, transactionTimeout);
        }

        /**
         * 压掉 MyBatis-Plus 动态 SQL 留下的连续空行（照搬 ERP/InkHub 已验证形态）。
         * JSqlParser 4.6 对"任意子句里连续 3 个换行"直接抛 ParseException——MP 动态 SQL
         * 被跳过的 null 字段会留空行，隔离表 INSERT/UPDATE 最常见写法必炸。
         * 纯空白变换（值一律 {@code ?} 占位符不进文本）；判定与替换兼容 CRLF
         * （只认 {@code \n\n} 会在 autocrlf=true 机器上静默失效），触发后统一归一 LF。
         */
        static String collapseBlankLines(String sql) {
            if (sql == null) {
                return null;
            }
            return sql.contains("\n\n") || sql.contains("\r\n\r\n")
                    ? sql.replaceAll("(\\r?\\n)+", "\n")
                    : sql;
        }

        /**
         * 解析失败收口（fail-closed，无 WARN 回退腿）：能走到这里的必然是涉及隔离面的
         * 非豁免语句——静默放行＝不带租户谓词跑隔离表语句＝跨租户读写通道，宁可炸语句。
         * <p>异常族＝机制故障，故留 {@code IllegalStateException}（用户解不了，
         * 换成业务异常会把我们的 bug 报成他的操作不对，见类注释异常族）。</p>
         */
        static void requireParseableInIsolationScope(String collapsed) {
            if (isParseable(collapsed)) {
                return;
            }
            throw new IllegalStateException("[tenant] 隔离面语句 JSqlParser 解析失败（真多语句/语法边界），"
                    + "当前非豁免态不允许不带租户谓词执行: " + abbreviate(collapsed));
        }

        static boolean isParseable(String sql) {
            try {
                CCJSqlParserUtil.parse(sql);
                return true;
            } catch (JSQLParserException | RuntimeException e) {
                return false;
            }
        }

        private static String abbreviate(String sql) {
            if (sql == null) {
                return "null";
            }
            int max = 120;
            return sql.length() <= max ? sql : sql.substring(0, max) + "...(len=" + sql.length() + ")";
        }
    }

    // ==================== handler（读侧语义三档；写侧补/拒见 TenantIsolationFillHandler） ====================

    static class IsolatedTenantLineHandler implements TenantLineHandler {

        /**
         * 该表是否跳过改写。豁免判定（顺序即语义）：
         * ① 平台档（AuthAdmin → markPlatform）——跨租户全量视图；
         * ② 内部操作段（TenantContext.runWithoutIsolation）；
         * ③ 用户不属于任何租户（租户集 null/空）＝系统管理级，不拦截（用户 2026-10-19 拍板；
         *    与框架 TenantDataGate"空集旁路"既有口径一致）；
         * ④ 表不在隔离集（未实现 TenantIsolated）。
         * 其余（租户集非空的普通用户）进入改写，当前租户缺失由 {@link #getTenantId()} 抛。
         */
        @Override
        public boolean ignoreTable(String tableName) {
            if (TenantContext.isPlatform() || TenantContext.isIsolationOff()) {
                return true;
            }
            List<Long> tenantIds = TenantContext.getTenantIds();
            if (tenantIds == null || tenantIds.isEmpty()) {
                return true;
            }
            return !isolatedTables().contains(bareName(tableName));
        }

        /**
         * 租户值取当前租户；缺失即抛（fail-closed："请先选择租户"，经
         * {@code TenantContext.requireCurrentTenantId()} 抛 {@code ServiceException}——
         * 用户可触发的拒绝，须进前端 details）。
         * 触发即代表：租户集非空（ignoreTable 已放行系统管理级）但 token 缺 TENANT_ID
         * ——老 token 重新登录，或操作未完成选择。
         */
        @Override
        public Expression getTenantId() {
            return new LongValue(TenantContext.requireCurrentTenantId());
        }

        @Override
        public String getTenantIdColumn() {
            return "tenant_id";
        }

        /**
         * SQL 层 INSERT 恒不自动填 {@code tenant_id}——本条不是风格选择，是 2026-10-20 实测：
         * 曾把本方法改成"列清单缺 tenant_id 才注入"，MP 生成的一条普通
         * {@code INSERT ... VALUES (?,...)} 直接炸
         * {@code ClassCastException: SetOperationList cannot be cast to PlainSelect}
         * （{@code TenantLineInnerInterceptor.processInsert} 见到 {@code insert.getSelect() != null}
         * 就交给 {@code processInsertSelect}，而 4.6 把 VALUES 形态也建成 Select，转手 cast 成
         * PlainSelect；{@code ProgTenantIsolationIT#insertWithoutTenantIdIsFilledWithCurrentTenant}
         * 留档）。MP 3.5.2 返回 true 即整条跳过改写，因此写路径的值必须来自实体。
         * <p>补齐落在实体填充链（{@link TenantIsolationFillHandler}），前提是隔离实体把
         * {@code tenantId} 声明成 {@code @TableField(fill = FieldFill.INSERT)}——普通列走
         * {@code <if test="tenantId != null">} 动态拼列，而列清单在 BoundSql 生成期就定型、
         * 早于填充链，缺了该声明填进去的值追不上列清单（同一次 IT 复现：库侧报
         * {@code Field 'tenant_id' doesn't have a default value}）。声明由
         * {@link TenantLineIsolationConfiguration#assertTenantColumnFillDeclared} 首用 fail-fast 守住。</p>
         */
        @Override
        public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
            return true;
        }
    }
}
