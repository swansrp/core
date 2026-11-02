package com.bidr.authorization.tenant;

/**
 * Title: TenantIsolated
 * Description: 多租户行级隔离标记接口。业务实体实现本接口 ⇒ 其表进入
 * 租户行级改写集（{@code TenantLineInnerInterceptor} 对该表自动拼
 * {@code tenant_id} 条件，见 {@link TenantLineIsolationConfiguration}）。
 *
 * <p><b>隔离目标=标记接口驱动</b>（2026-10-19 用户拍板，取代按表前缀判定）：
 * 各功能模块实体自声明，无需中央登记表；启动/懒加载期从 MyBatis-Plus
 * {@code TableInfoHelper} 注册表收集"实现本接口的实体"构建隔离表名集。</p>
 *
 * <p><b>实体约定（两处守卫 fail-fast 钉住）</b>：实现本接口的实体<b>必须</b>有
 * {@code tenant_id} 列（Long），否则拦截器拼列即 Unknown column；且该列<b>必须</b>声明成
 * {@code @TableField(value = "tenant_id", fill = FieldFill.INSERT)}——写侧的
 * {@code tenant_id} 由框架填充链负责（漏填补当前租户、带别租户值或未选租户当场拒，
 * 见 {@link TenantIsolationFillHandler}），而 SQL 层不注入、普通列的拼列又早于填充链，
 * 缺该声明时框架补的值追不上列清单，漏填只剩库侧 {@code NOT NULL} 报错；结构性唯一索引一律
 * {@code tenant_id} 打头。ddl-gen 已按本约定渲染。</p>
 *
 * <p><b>租户语义三档</b>（未标注豁免的表不受影响）：用户不属于任何租户＝
 * 系统管理级，不拦截；属于租户但当前租户未选＝fail-closed（语句报
 * "请先选择租户"）；平台档（{@code AuthAdmin} → {@code markPlatform}）豁免。
 * 语句级豁免用 MyBatis-Plus 原生 {@code @InterceptorIgnore(tenantLine = "true")}。</p>
 *
 * @author Sharp
 * @since 2026-10-19
 */
public interface TenantIsolated {
}
