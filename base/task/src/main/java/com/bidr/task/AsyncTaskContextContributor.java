package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;

/**
 * 阶段执行包裹段的业务上下文贡献者 SPI（M16 棒 2）：core 不认识任何业务形态（R-1），
 * 「从 {@code biz_ref}＋{@code create_by} 重建业务/租户上下文」这半截由业务侧实现本口挂进来。
 *
 * <p>成对契约：{@link #bind(SaAsyncTask)} 在阶段方法（与终态观察者链）之前调、
 * {@link #unbind(SaAsyncTask, Object)} 在整条链之后调（含异常路径），{@code handle} 透传给
 * 恢复侧。实现须自备「存旧值→设新值→恢复」的卫生姿势，不得裸清线程上下文
 * （详见接入说明 §⑤线程与上下文纪律）。</p>
 *
 * <p>实现注册为 Spring Bean 即被 {@link TaskContextBridge} 收集（可多个，按序成对执行）。</p>
 *
 * @author sharp
 */
public interface AsyncTaskContextContributor {

    /**
     * 重建业务上下文（如按 {@code biz_ref} 还原租户参照、关闭行级隔离包裹段）。
     *
     * @param task 即将执行阶段的任务行
     * @return 出口恢复句柄（透传给 {@link #unbind}；无可恢复内容返回 null 即可）
     */
    Object bind(SaAsyncTask task);

    /**
     * 恢复（整条链之后调，异常路径也保证执行；实现自行兜异常，不得向任务链抛）。
     *
     * @param task   任务行
     * @param handle {@link #bind} 的返回值
     */
    void unbind(SaAsyncTask task, Object handle);
}
