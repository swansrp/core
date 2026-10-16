package com.bidr.authorization.config;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.select.PlainSelect;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Title: TenantGateConfiguration
 * Description: 租户数据闸装配（批乙棒2 建立，修1 按 M-23 改造闸载体）：
 * 用 {@link BeanPostProcessor} 把容器里的 {@link MybatisPlusInterceptor} bean 重建为
 * 「租户闸 inner 最前 ＋ 原链（Pagination/DynamicTableName）照抄」，不改动共享框架
 * {@code kernel/config/db/MybatisPlusConfig}。注册形态＝ {@code @Configuration} 落在
 * {@code com.bidr.authorization.config}，由 {@code BaseApplication} 的
 * {@code @ComponentScan("com.bidr")} 扫到（与同包 async/log 等既有 config 类同款，
 * core 各宿主应用启动类均继承 BaseApplication，无需 spring.factories）。
 * <p>🔴 M-23（review R1 证伪回写）：MP 3.5.2 {@code DataPermissionInterceptor} 传给 handler 的
 * 第二参是 {@code MappedStatement.getId()}（字节码链 beforeQuery→parserSingle(sql, ms.getId())
 * →processSelect→setWhere(PlainSelect,(String)obj)→getSqlSegment(where,obj) 已 javap 复核），
 * 按"表名"判锚点会永不命中＝闸静默空转。载体修正＝{@link TenantGateInnerInterceptor} 覆写
 * {@code setWhere(PlainSelect, String)}，锚点判定与 EXISTS 拼写经
 * {@link TenantDataGate#resolveAnchor(PlainSelect)} 基于 FROM/JOIN 解析出的<b>真实表引用</b>
 * 执行（别名优先做列限定）；handler 的 {@code getSqlSegment} 退化为纯函数供离线单测直调。</p>
 * <p>文本预判收紧：词边界 {@code \bac_user\b} 且只对 FROM/JOIN 段匹配（防 ac_user_role/
 * ac_user_dept 族空转解析、防解析炸面）；启动期多语句（含分号）不碰；交解析器前压连续空行
 * （JSqlParser 4.6 对 MP 动态 SQL 相邻空行必炸，照先例 collapseBlankLines）；
 * 解析/改写期任何 RuntimeException（含嵌套 UNION 支的 ClassCastException，修3）保守旁路<b>不改写</b>——绝不把老项目查询炸挂。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
@Configuration
public class TenantGateConfiguration {

    /** 把租户数据闸插到框架 MybatisPlusInterceptor 链的最前（数据权限须在分页/动态表名之前改写 WHERE） */
    @Bean
    public static BeanPostProcessor tenantGateInjector() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof MybatisPlusInterceptor)) {
                    return bean;
                }
                MybatisPlusInterceptor origin = (MybatisPlusInterceptor) bean;
                MybatisPlusInterceptor rebuilt = new MybatisPlusInterceptor();
                rebuilt.addInnerInterceptor(new TenantGateInnerInterceptor(new TenantDataGate()));
                List<InnerInterceptor> originInterceptors = origin.getInterceptors();
                for (InnerInterceptor inner : originInterceptors) {
                    rebuilt.addInnerInterceptor(inner);
                }
                return rebuilt;
            }
        };
    }

    /**
     * 闸载体（M-23 修正后）：beforeQuery 只做文本预判＋空行压缩＋旁路短路，改写本体在
     * 覆写的 {@code setWhere} 里基于真实表引用执行。
     * public＋public static：判定函数供 architecture 包守卫测试（TenantGateConventionTest）直调。
     */
    public static class TenantGateInnerInterceptor extends DataPermissionInterceptor {

        /**
         * 文本预判形态：FROM/JOIN 之后紧跟（可带库名前缀/反引号的）ac_user 词边界命中才考虑改写。
         * 词边界 \b 使 ac_user_role/ac_user_dept 族天然不命中（下划线是 \w，无边界）；
         * 只匹配 FROM/JOIN 段，防字符串字面量里的 "ac_user" 触发空转解析。
         */
        private static final Pattern FROM_JOIN_ANCHOR = Pattern.compile(
                "(?i)\\b(?:from|join)\\s+[`\"]?(?:[A-Za-z_][A-Za-z0-9_]*[`\"]?\\s*\\.\\s*)?[`\"]?ac_user\\b");

        public TenantGateInnerInterceptor(TenantDataGate handler) {
            super(handler);
        }

        /** SQL 文本预判：null/含分号多语句直接不碰；FROM/JOIN 段词边界命中 ac_user 才考虑改写 */
        public static boolean shouldRewrite(String sql) {
            if (sql == null || sql.contains(";")) {
                return false;
            }
            return FROM_JOIN_ANCHOR.matcher(sql).find();
        }

        /** 纯空白变换：值一律走 ? 占位符，不触碰字面量（照先例，JSqlParser 4.6 空行兼容处理） */
        public static String collapseBlankLines(String sql) {
            return sql != null && sql.contains("\n\n") ? sql.replaceAll("\n+", "\n") : sql;
        }

        @Override
        public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                                ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
            String sql = boundSql.getSql();
            // 预判不命中 ⇒ 原文零抖动；闸不活跃（无档/空集/平台档）⇒ 同样跳过（旁路终裁口径）
            if (!shouldRewrite(sql) || !TenantDataGate.gateActive()) {
                return;
            }
            // 挂了 @InterceptorIgnore 且 dataPermission="true" 的 mapper 方法在此短路
            // （portal 专用取数口，M-24；守卫针钉全模块该注解命中数＝1）
            if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
                return;
            }
            PluginUtils.mpBoundSql(boundSql).sql(collapseBlankLines(sql));
            try {
                super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
            } catch (RuntimeException gateFailure) {
                // 保守旁路（修3，R-A R2）：不止解析失败——JsqlParserSupport 只包 JSQLParserException，
                // 嵌套括号 UNION 首支是 SetOperationList，父链 processSelect 分支 cast PlainSelect 抛
                // ClassCastException（reviewer javap＋4.6 探针实证），MybatisPlusException 接不住即炸穿
                // "绝不把老项目查询炸挂"契约 ⇒ RuntimeException 一把接住（fail-close 方向是炸、非漏）。
                // 残留：StackOverflowError 等 Error 超出合理防线，不接。
                // 根因不可丢（R-fix3 Important）：org.apache.ibatis.logging.Log 无 warn(String,Throwable)
                // 重载 ⇒ 异常拼进消息，闸静默旁路时能靠日志直接判出是 CCE 还是畸形 SQL。
                logger.warn("TenantGate: JSqlParser failed, gate bypassed (sql kept unwrapped). ms="
                        + ms.getId() + " cause=" + gateFailure);
            }
        }

        /**
         * 改写本体（M-23 载体修正点）：第二参 mapKey 实为 {@code MappedStatement.getId()}
         * （方法名形态），<b>不得</b>用作锚点判据——锚点判定与 EXISTS 拼写一律基于
         * {@link TenantDataGate#resolveAnchor(PlainSelect)} 解析出的真实表引用；
         * 非锚点/解析不到 ⇒ 不改写（保守旁路）。
         */
        @Override
        protected void setWhere(PlainSelect plainSelect, String mapKey) {
            TenantDataGate.AnchorRef anchor = TenantDataGate.resolveAnchor(plainSelect);
            if (anchor == null) {
                return;
            }
            Expression original = plainSelect.getWhere();
            Expression rewritten = TenantDataGate.appendGate(original, anchor.tableName, anchor.columnQualifier);
            if (rewritten != original) {
                plainSelect.setWhere(rewritten);
            }
        }
    }
}
