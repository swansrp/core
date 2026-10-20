package com.bidr.authorization.tenant.job;

/**
 * Title: TenantScopedJob
 * Description: 租户级定时任务的业务接口（2026-10-20 下沉 core）：业务只实现本接口，
 * 框架（{@link TenantJobSchedulingConfiguration}＋{@link TenantJobScheduler}）在启动期
 * 收集容器内全部实现 bean，按各自 {@link #cron()} 在时间轮（{@link TimingWheel}）上
 * 定点调度——每个触发点对 {@link ActiveTenantSource} 枚举出的<b>每个启用租户</b>执行一次
 * {@link #execute(Long)}（执行时 core {@code TenantContext} 已置为该租户）。
 *
 * <p><b>执行语义（框架承诺面）</b>：
 * <ul>
 *   <li><b>单租户失败不拖垮其余租户</b>：某个租户的 {@link #execute(Long)} 抛异常，
 *       框架记 ERROR（带 jobKey 与 tenantId）后继续下一个租户——定时任务的价值在覆盖率；
 *   <li><b>重叠守卫</b>：上一轮还没跑完又到下一触发点时，本轮<b>整轮跳过</b>（记 WARN），
 *       不排队堆积（下一触发点照常）；
 *   <li><b>上下文</b>：执行期间 core {@code TenantContext}＝当前租户（租户集＝单元素＋
 *       当前租户），行级隔离读改写/写闸填充链据此生效；执行外不留任何租户档；
 *   <li><b>线程</b>：跑在框架 worker 池（非 tick 线程、非 Web 请求线程）——需要请求态
 *       上下文的逻辑不得假设 Web 环境在场。</li>
 * </ul>
 *
 * <p><b>装配纪律</b>：本接口的收集面＝Spring 容器（按 {@code @Order} 稳定排序）；
 * 启用闸＝{@code my.tenant.job-wheel.enabled=true}（缺省 false＝零装配，旧行为逐字节一致）。
 * 实现类放哪个模块就只在哪个模块的壳里被收集——多壳项目（admin/staff/merchant）里
 * "只该跑一份"的任务必须放在单一壳的装配面，放公共模块会被各壳各跑一遍。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
public interface TenantScopedJob {

    /**
     * 任务唯一标识（日志/重叠守卫键）；缺省＝实现类简单类名。多壳项目须保证单壳内唯一。
     */
    default String jobKey() {
        return getClass().getSimpleName();
    }

    /**
     * 触发时刻表达式：<b>6 段 Spring cron</b>（秒 分 时 日 月 周，如
     * {@code "0 0 2 * * *"}＝每天 02:00:00）。启动期解析，解析失败＝装配事故
     * fail-fast（宁可不启动，不收一个永远不跑的任务）。
     */
    String cron();

    /**
     * 是否启用（启动期判定；false＝不登记上轮）。缺省 true——停用一个任务
     * 覆写本方法即可，不必摘实现类。
     */
    default boolean enabled() {
        return true;
    }

    /**
     * 对单个启用租户执行的动作。执行时 core {@code TenantContext} 已置为该租户；
     * 抛异常＝该租户失败（框架记 ERROR 后继续下一个租户），不得靠抛异常做流程控制。
     *
     * @param tenantId 启用租户 id（正源由 {@link ActiveTenantSource} 实现决定）
     */
    void execute(Long tenantId);
}
