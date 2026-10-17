package com.bidr.kernel.config.db;

import java.util.function.Supplier;

/**
 * Title: TenantGateSuppressor
 * Description: 租户数据闸的<b>调用帧级</b>旁路位。
 * <p>为什么住 kernel：写入方 {@code BindRepoHandler} 在 kernel，闸本体在 authorization
 * （authorization 依赖 kernel，方向不可逆）——让闸来读 kernel 的一个 ThreadLocal 是唯一不打架的形态。</p>
 * <p>语义边界＝只服务"引用值 → 展示值"的翻译读（{@code @BindRepo} 及其元注解族
 * {@code @BindUser}/{@code @BindDept}）：这类查询回填的是名称/工号，不是业务数据集，
 * 按归属租户筛会把跨租户引用（如平台运营建的单据 create_by）静默翻译成空白。
 * 业务读写一律照旧过闸；宿主项目自己的隔离层（如 ERP 的 {@code biz_} 行级拦截器）不消费本位。</p>
 * <p>🔴 只在 {@link #runSuppressed(Supplier)} 的调用帧内为真，finally 恢复前值：
 * 异步池线程不继承（{@code ContextCopyingTaskDecorator} 的拷贝面不含本位），
 * 不会出现"翻译读顺手给后台任务免了闸"。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
public class TenantGateSuppressor {

    private static final ThreadLocal<Boolean> SUPPRESSED = new ThreadLocal<>();

    private TenantGateSuppressor() {
    }

    /** 当前调用帧是否处于翻译读旁路（从未进过 {@link #runSuppressed} ⇒ false） */
    public static boolean isSuppressed() {
        return Boolean.TRUE.equals(SUPPRESSED.get());
    }

    /**
     * 在闸旁路态下执行一段取数，返回后恢复前值（嵌套安全；异常同样恢复）。
     */
    public static <T> T runSuppressed(Supplier<T> action) {
        Boolean previous = SUPPRESSED.get();
        SUPPRESSED.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                SUPPRESSED.remove();
            } else {
                SUPPRESSED.set(previous);
            }
        }
    }
}
