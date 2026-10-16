package com.bidr.authorization.holder;

import java.util.List;

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
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantContext {

    private static final ThreadLocal<List<Long>> TENANT_IDS_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> PLATFORM_HOLDER = new ThreadLocal<>();

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
     * 原始持有值（可能为 null）；一般消费方用 {@link #getTenantIds()}。
     */
    public static List<Long> get() {
        return TENANT_IDS_HOLDER.get();
    }

    /**
     * 清理两个 ThreadLocal，务必在请求结束钩子调用，防线程池串档。
     */
    public static void remove() {
        TENANT_IDS_HOLDER.remove();
        PLATFORM_HOLDER.remove();
    }
}
