package com.bidr.authorization.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 入口级跨租户读豁免声明（0.4b 于业务侧引入，2026-10-24 豁免治理迁 core——ADR 0001 裁定 5 终裁）。
 *
 * <p>标在<b>方法或类</b>上（方法优先、类兜底）：{@code AuthInterceptor} 鉴权链据此置平台档
 * （{@link com.bidr.authorization.holder.TenantContext#markPlatform()}），行级隔离器对隔离表
 * 不拼 {@code tenant_id}（读侧豁免）；并经 {@link TenantExemptIdentityFill} SPI 回调业务侧
 * 升格身份面。注解驱动＝不在拦截器里维护路径清单（InkHub 已验证的纪律）。</p>
 *
 * <p><b>读写边界（ADR 0001 裁定 1，防"标了就能写"的误用）</b>：本注解<b>只豁免读侧</b>——
 * 读隔离由行级隔离器认平台档短路；写闸（{@code TenantIsolationFillHandler}）只认
 * {@link com.bidr.authorization.holder.TenantContext#runWithoutIsolation(Runnable)} 包裹段，
 * 不认平台档。本端点内写隔离表仍需显式 {@code runWithoutIsolation} 包裹（与注册链 bug
 * 同款约束）：有当前租户时写闸 autofill 本就会自动补值，无需豁免；无租户场景本就该显式
 * 声明，宁可语句失败也不裸写。</p>
 *
 * <p><b>三形态分工</b>（豁免通道全景与选用规则见 ADR 0001）：
 * <ul>
 *   <li>本注解＝<b>入口级</b>：HTTP 端点声明"我是跨租户读面"，鉴权后生效
 *       （跑在鉴权验证之后，未认证请求不触发——与 0.4b 业务侧原消费实现同口径）；</li>
 *   <li>{@code runWithoutIsolation}＝<b>代码段级</b>："这一段内部代码不经隔离器"，
 *       定时任务/对账/治理写等无请求或写侧场景的唯一显式通道；</li>
 *   <li>{@code @Auth(com.bidr.authorization.annotation.auth.AuthAdmin)}＝<b>权限＋豁免绑定</b>：
 *       仅平台管理员可进，进即平台档。</li>
 * </ul></p>
 *
 * <p><b>与 AuthAdmin 分工（ADR 0001 裁定 5）</b>：互补不互替——AuthAdmin 把豁免绑死在
 * "平台管理员"权限档上；本注解把豁免<b>独立于权限档</b>（不限定调用者身份，端点自己声明
 * "我是跨租户面"）。适用场景＝"非平台管理员需要跨租户视图"的端点（如 staff 跨店对账）；
 * 本系统当前零真实使用，为该类首个端点出现时预留。<b>设计史</b>：0.4b 注解先于 AuthAdmin
 * 平台档置位存在（2026-10-07 命名拍板），AuthAdmin 占用"平台视图端点"生态位后曾裁定删除，
 * 2026-10-24 同日终裁翻案——生态位并不重叠（豁免独立于权限档 vs 权限＋豁免绑定），迁移 core 保留。</p>
 *
 * @author coder (棒1, 租户豁免治理 2026-10-24)
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface CrossTenant {
}
