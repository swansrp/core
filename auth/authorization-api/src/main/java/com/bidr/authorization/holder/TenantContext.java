package com.bidr.authorization.holder;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;

import java.util.List;
import java.util.function.Supplier;

/**
 * Title: TenantContext
 * Description: 租户上下文 ThreadLocal 持有器（形态照 {@link AccountContext}）。
 * <p>棒1 只建 holder 与其单测；填充/清理时机由棒2 数据闸接线（拦截器/切面），勿在本类之外另立门户。</p>
 * <p>🔴 getTenantIds() 口径（写死，棒2 消费方以此为准）：
 * <ul>
 * <li>未 set（无上下文）⇒ {@code null}＝旁路形态——老项目、老 token、免登链路都落这里，
 * 数据闸据此放行，绝不抛错；</li>
 * <li>set 了 null ⇒ 同上返回 {@code null}（口径统一，调用方无需区分"没 set"与"set 了空档"）；</li>
 * <li>set 了空列表 ⇒ 返回空列表＝用户无租户归属；🔴 数据闸口径（用户弹卡终裁）＝
 * <b>空集与 null 同为旁路（全量可见，兼容优先）</b>，消费方不得据空集判"拒绝"。</li>
 * </ul></p>
 * <p>isPlatform()：平台档标记位，未 set 或从未标记 ⇒ false；由棒2 在构建上下文时经
 * {@code set(tenantIds, true)} 或 {@code markPlatform()} 显式置位。</p>
 *
 * <p><b>2026-10-19 扩展（多租户行级隔离，TenantLineIsolationConfiguration 消费）</b>：
 * <ul>
 * <li>「当前租户」槽 {@link #setCurrentTenantId(Long)}：登录链（AuthLogin.buildContext）
 * 从 token 的 TENANT_ID 键填充；口径——租户集为空/null 的用户（系统管理级）本槽保持 null；
 * 租户集非空但未选当前租户 ⇒ {@link #requireCurrentTenantId()} 抛（fail-closed）。</li>
 * <li>行级隔离开关 {@link #runWithoutIsolation(Runnable)}：内部操作段（定时任务/对账等
 * 无请求场景）免拼 {@code tenant_id}；自有 ThreadLocal，不进 extraData（异步线程只读不写）。</li>
 * <li>{@link #remove()} 一并清理全部槽位（防线程池串档），调用方不变。</li>
 * </ul></p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantContext {

    private static final ThreadLocal<List<Long>> TENANT_IDS_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_HOLDER = new ThreadLocal<>();
    /** 当前租户（租户集内被选定的那个）；null＝未选（系统管理级用户/老 token/未启用项目） */
    private static final ThreadLocal<Long> CURRENT_TENANT_HOLDER = new ThreadLocal<>();
    /** 行级隔离开关（仅 runWithoutIsolation 包裹段内为 ON），嵌套恢复外层值 */
    private static final ThreadLocal<Boolean> ISOLATION_OFF = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(List<Long> tenantIds) {
        TENANT_IDS_HOLDER.set(tenantIds);
    }

    public static void set(List<Long> tenantIds, boolean platform) {
        TENANT_IDS_HOLDER.set(tenantIds);
        PLATFORM_HOLDER.set(platform);
    }

    /**
     * 当前租户集；无上下文/无档 ⇒ null（旁路形态），口径见类注释。
     */
    public static List<Long> getTenantIds() {
        return TENANT_IDS_HOLDER.get();
    }

    /**
     * 平台档标记；未 set 或从未标记 ⇒ false。
     */
    public static boolean isPlatform() {
        Boolean platform = PLATFORM_HOLDER.get();
        return platform != null && platform;
    }

    /**
     * 显式置位平台档（不改租户集）。
     */
    public static void markPlatform() {
        PLATFORM_HOLDER.set(Boolean.TRUE);
    }

    /**
     * 设置当前租户（登录链 AuthLogin.buildContext 填充；租户切换端点更新走 token 重签，
     * 本方法只管请求线程态）。传 null＝清除（系统管理级用户/未选态）。
     */
    public static void setCurrentTenantId(Long tenantId) {
        CURRENT_TENANT_HOLDER.set(tenantId);
    }

    /**
     * 当前租户；null＝未选（可能＝系统管理级用户，也可能＝租户用户未选）——
     * 二者区分交给消费方（行级隔离 handler 按租户集判），本类不猜。
     */
    public static Long currentTenantId() {
        return CURRENT_TENANT_HOLDER.get();
    }

    /**
     * 当前租户，缺失即抛（隔离表 SQL 的 fail-closed 落点）。
     * <p>注意调用时机：{@code TenantLineHandler.getTenantId()} 只在"非忽略表"上被调，
     * 因此本方法触发即代表——用户有租户归属（租户集非空，否则 ignoreTable 已放行）
     * 但 token 里没有当前租户（老 token / 未完成选择）⇒ 链路或操作未完成，宁可语句失败
     * 也不裸跑。</p>
     * <p>异常类型＝{@code ServiceException}（2026-10-20 用户拍板）：本条是<b>用户可触发</b>的拒绝
     * （切个租户就能解），框架口径只有 ServiceException 的 message 能进前端 details；
     * 开发期契约守卫与 SQL 解析 fail-closed 不在此族，仍抛 {@code IllegalStateException}。</p>
     */
    public static Long requireCurrentTenantId() {
        Long tenantId = CURRENT_TENANT_HOLDER.get();
        if (tenantId == null) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "请先选择租户：当前账号归属租户但请求未携带当前租户"
                    + "（token 缺 TENANT_ID，请重新登录或调用租户切换端点）");
        }
        return tenantId;
    }

    /**
     * 关闭行级隔离执行一段内部操作（定时任务/对账/跨租户治理等；异常路径也保证恢复）。
     * 支持嵌套：内层恢复外层原值而非直接清除。
     */
    public static void runWithoutIsolation(Runnable action) {
        callWithoutIsolation(() -> {
            action.run();
            return null;
        });
    }

    /** 同 {@link #runWithoutIsolation(Runnable)}，带返回值 */
    public static <T> T callWithoutIsolation(Supplier<T> action) {
        Boolean previous = ISOLATION_OFF.get();
        ISOLATION_OFF.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                ISOLATION_OFF.remove();
            } else {
                ISOLATION_OFF.set(previous);
            }
        }
    }

    /** 行级隔离开关是否处于关闭段（TenantLine handler 消费；包内可见即可，测试亦用） */
    public static boolean isIsolationOff() {
        return Boolean.TRUE.equals(ISOLATION_OFF.get());
    }

    /**
     * 原始持有值（可能为 null）；一般消费方用 {@link #getTenantIds()}。
     */
    public static List<Long> get() {
        return TENANT_IDS_HOLDER.get();
    }

    /**
     * 清理全部 ThreadLocal，务必在请求结束钩子调用，防线程池串档。
     */
    public static void remove() {
        TENANT_IDS_HOLDER.remove();
        PLATFORM_HOLDER.remove();
        CURRENT_TENANT_HOLDER.remove();
        ISOLATION_OFF.remove();
    }
}
