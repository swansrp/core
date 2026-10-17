package com.bidr.authorization.config;

import com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler;
import com.bidr.authorization.holder.TenantContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Title: TenantDataGate
 * Description: 租户数据闸（core 侧，批乙修1 按 M-23 改造载体）——锚点判定与 EXISTS 拼写
 * 基于 <b>JSqlParser 解析出的真实表引用</b>执行（MP 3.5.2 DataPermissionInterceptor 传给
 * handler 的第二参实为 {@code MappedStatement.getId()}，不是表名——已证伪，运行时判据
 * 不得依赖该参数）。判序（顺序即语义，勿调换，入口 {@link #appendGate}）：
 * <ol>
 * <li>锚点清单外 ⇒ 原样返回 where（同一实例）——清单唯一成员 {@code ac_user}；
 * 表名按「最后一个点分段等值」匹配，兼容库名前缀（{@code bidr.ac_user} 命中）而不误吞
 * 后缀词表（{@code ac_user_tenant}/{@code xx_ac_user} 均不命中；contains/endsWith 都会误伤）；</li>
 * <li>{@link TenantContext} 无档（null）<b>或空集合</b> ⇒ 原样返回（用户弹卡终裁：空集/无档
 * ＝旁路全量可见，兼容优先——租户隔离只对"至少挂一个租户"的人生效；老项目/老 token/
 * 内部任务/单测零感知的机制核）；</li>
 * <li>平台档 {@link TenantContext#isPlatform()} ⇒ 原样返回（AuthAdmin 通过路径置位）；</li>
 * <li>有档且非空 ⇒ 拼 {@code EXISTS (SELECT 1 FROM ac_user_tenant aut WHERE aut.user_id =
 * <表引用>.user_id AND aut.tenant_id IN (…))} 与既有 where 用 AND 合并（无 where 直接返回新表达式）。
 * IN 值全部来自服务端 {@link TenantContext}（Long），非用户输入，无注入面。</li>
 * </ol>
 * <p>运行时真实表路径：{@link #resolveAnchor(PlainSelect)} 从 {@code getFromItem()/getJoins()}
 * 取第一个锚点 {@code Table}（剥反引号/双引号；<b>有别名用别名做 EXISTS 列限定</b>——原"主表引用
 * 直接用 MP 传入的表名、别名会炸"的限制随 M-23 载体改造解除），判定与拼写都基于该真实引用；
 * 主查询块之外（如 IN 子查询内部）MP 3.5.2 DataPermission 形态本身不下钻，属该载体的既有边界，
 * 框架侧 AcUser 查询走 MP Wrapper 为平铺 SQL，不触此边界。</p>
 * <p>纯对象可直接 new（不经 Spring）；{@link #getSqlSegment(Expression, String)} 保留纯函数形态
 * 供离线单测直调（表名形态），但运行时判据以 {@link TenantGateConfiguration.TenantGateInnerInterceptor}
 * 解析出的表为准。写面不在本闸职责：MP 3.5.2 DataPermissionInterceptor 只有 beforeQuery（javap 已证），
 * 写路径由批甲 AuthAdmin 注解闸兜底（M-16）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantDataGate implements DataPermissionHandler {

    /** 锚点清单唯一成员：用户-租户归属直接落在 ac_user 的查询上 */
    static final String ANCHOR_TABLE = "ac_user";

    /** 租户归属关系表（EXISTS 子查询的来源表，即 ac_user_tenant 六件套正源表） */
    static final String TENANT_REL_TABLE = "ac_user_tenant";

    /** 表引用/别名白名单：只允许标识符（可带库名前缀）形态（表名来自 SQL 解析，防御性校验） */
    private static final Pattern SAFE_TABLE_REF = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

    /** 解析出的锚点表引用：tableName＝真实表名（判锚点用），columnQualifier＝EXISTS 列限定符（别名优先） */
    public static final class AnchorRef {
        public final String tableName;
        public final String columnQualifier;

        AnchorRef(String tableName, String columnQualifier) {
            this.tableName = tableName;
            this.columnQualifier = columnQualifier;
        }
    }

    @Override
    public Expression getSqlSegment(Expression where, String tableRef) {
        // 纯函数形态（离线单测直调）：表名兼做列限定符；运行时路径请用 resolveAnchor + appendGate
        return appendGate(where, tableRef, tableRef);
    }

    /**
     * 闸判据本体（判序见类注释）。public static：供 {@code TenantGateInnerInterceptor}
     * 的真实表路径与 architecture 守卫测试共用，逻辑不依赖任何注入。
     */
    public static Expression appendGate(Expression where, String anchorTableName, String columnQualifier) {
        if (!isAnchorTable(anchorTableName)) {
            return where;
        }
        List<Long> tenantIds = TenantContext.getTenantIds();
        if (tenantIds == null || tenantIds.isEmpty()) {
            return where;
        }
        if (TenantContext.isPlatform()) {
            return where;
        }
        Expression gate = buildTenantExists(columnQualifier, tenantIds);
        if (gate == null) {
            return where;
        }
        return where == null ? gate : new AndExpression(where, gate);
    }

    /**
     * 闸是否处于"会改写"的活跃档（判序②③的短路形态）：无档/空集/平台档 ⇒ false——
     * {@code beforeQuery} 据此在解析之前就跳过，老项目连"压缩+重序列化"的文本抖动都不发生，
     * 是比"改写后原样"更强的零感知。仅短路，不新增语义。
     */
    public static boolean gateActive() {
        List<Long> tenantIds = TenantContext.getTenantIds();
        return tenantIds != null && !tenantIds.isEmpty() && !TenantContext.isPlatform();
    }

    /**
     * 从 PlainSelect 的真实表引用解析锚点表：先 {@code getFromItem()} 后 {@code getJoins()}，
     * 取第一个 {@code Table} 且「最后一个点分段等值 ac_user」的项；剥反引号/双引号；
     * 有别名 ⇒ 列限定符用别名，无别名 ⇒ 用（可带库名前缀的）表名本身。
     * 找不到（含 FROM 是子查询的形态）⇒ null＝不改写——保守旁路，绝不抛。
     * public static：供 InnerInterceptor 与守卫测试直调。
     */
    public static AnchorRef resolveAnchor(PlainSelect plainSelect) {
        if (plainSelect == null) {
            return null;
        }
        AnchorRef anchor = asAnchor(plainSelect.getFromItem());
        if (anchor != null) {
            return anchor;
        }
        List<Join> joins = plainSelect.getJoins();
        if (joins != null) {
            for (Join join : joins) {
                anchor = asAnchor(join.getRightItem());
                if (anchor != null) {
                    return anchor;
                }
            }
        }
        return null;
    }

    private static AnchorRef asAnchor(FromItem item) {
        if (!(item instanceof Table)) {
            return null;
        }
        Table table = (Table) item;
        // 4.6：getName() 只含表名段、库名在 getSchemaName()——用全限定形态做锚点判定与无别名限定符，
        // 保留「库名.表名」列限定能力（MySQL 允许 db.tbl.col 形态）
        String name = stripQuotes(table.getFullyQualifiedName());
        if (name == null || name.isEmpty()) {
            name = stripQuotes(table.getName());
        }
        if (!isAnchorTable(name)) {
            return null;
        }
        Alias alias = table.getAlias();
        String qualifier = alias != null ? stripQuotes(alias.getName()) : name;
        return new AnchorRef(name, qualifier);
    }

    /** 剥反引号/双引号（JSqlParser 4.6 把引用的标识符原样带引号存进 name）；public static 供守卫直调 */
    public static String stripQuotes(String identifier) {
        if (identifier == null) {
            return null;
        }
        return identifier.replace("`", "").replace("\"", "");
    }

    /** 锚点判定：最后一个点分段等值 ac_user（大小写不敏感）——兼容库名前缀（bidr.ac_user 命中），
     * 且不误吞相邻词表（ac_user_tenant 不命中）；不用 contains/endsWith（contains 会把 ac_user_tenant 当锚点）。
     * public static：供 architecture 包守卫测试直调 */
    public static boolean isAnchorTable(String tableName) {
        if (tableName == null) {
            return false;
        }
        String name = tableName.toLowerCase();
        int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(dot + 1)).equals(ANCHOR_TABLE);
    }

    /**
     * 拼租户 EXISTS 闸。列限定符不在白名单（畸形 SQL 解析产物）时返回 null＝不改写——
     * 宁可旁路也不产坏 SQL，属判据①的保守延伸。
     */
    static Expression buildTenantExists(String tableRef, List<Long> tenantIds) {
        // 限定符保留原始大小写（表别名引用不可随意折叠大小写）；只剥引号＋白名单校验
        String ref = stripQuotes(tableRef);
        if (ref == null || !SAFE_TABLE_REF.matcher(ref).matches()) {
            return null;
        }
        String inList = tenantIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String clause = "EXISTS (SELECT 1 FROM " + TENANT_REL_TABLE + " aut WHERE aut.user_id = "
                + ref + ".user_id AND aut.tenant_id IN (" + inList + "))";
        try {
            return CCJSqlParserUtil.parseCondExpression(clause);
        } catch (JSQLParserException e) {
            return null;
        }
    }
}
