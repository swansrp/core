package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;

/**
 * 异步任务终态观察者（M16 棒 2）：🔴 终态契约，不是「成功契约」——
 * {@code SUCCESS／DEAD} 两档<b>必发</b>、{@code FAILED}（仍在重试预算内）<b>不发</b>。
 *
 * <p>为什么必须有失败档：业务失败态（业务表的 FAILED 列、通知、回程提示）原先写在执行侧
 * catch 里，迁到注解通道后必须有人接手，否则前端永远转「处理中」。为什么 FAILED 不发：
 * 可重试失败每轮都进 FAILED（退避再来），挂在 FAILED 上＝同一任务通知 N 次。</p>
 *
 * <p>口形＝{@code onTerminal(task, status)}；多贡献链逐件 try-catch——<b>观察者抛异常不改任务终态</b>
 * （机器判据腿钉住）。观察者与阶段方法共用同一份重建上下文（解绑排在整条链之后，否则链里写
 * 受隔离保护的表会落空）。{@code biz_ref} 随任务行带回，用于定位业务行。</p>
 *
 * @author sharp
 */
public interface AsyncTaskObserver {

    /**
     * 任务进入终态（SUCCESS 或 DEAD）时回调。
     *
     * @param task    任务行（含 {@code biz_ref}，供定位业务行；任务行此刻已带终态）
     * @param status  终态档（SUCCESS / DEAD）
     */
    void onTerminal(SaAsyncTask task, AsyncTaskStatus status);
}
