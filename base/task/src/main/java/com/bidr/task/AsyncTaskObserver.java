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

    /**
     * 任务执行期发生<b>真实阶段迁移</b>时回调（FIRST→SECOND 这类推进）。
     *
     * <p>🔴 只在「真迁移」发，三处静默要一起记住：{@code to(当前阶段)} 的原地推进<b>不发</b>
     * （外部引擎腿轮询的常态形状——每轮把当前档再写一遍，跑了就等于每 tick 轰一次观察者）；
     * {@code pending} 退避<b>不发</b>（stage 原地不动只是本轮暂缓，不是迁移）；受理侧
     * {@code submit}/{@code requeue} 落首阶段走直写、不经记账口，天然不发。</p>
     *
     * <p>业务用例：「每到一个阶段做一件事」都挂这里——进入某阶段时给外部系统下单、
     * 发过程性通知、落阶段审计。多贡献链逐件 try-catch——<b>观察者抛异常不改任务记账</b>
     * （与 {@link #onTerminal} 同一纪律）。调用发生在跑批重建上下文内，观察者与阶段方法
     * 共用同一份上下文；{@code task} 行此刻已带迁移后的 stage 与 RUNNING 态。</p>
     *
     * <p>两条终态边界同样<b>不发</b>阶段事件：非末阶段阶段体提前返回 {@code done()} 走
     * succeed 直写末阶段＋SUCCESS、{@code dead} 置 DEAD——两者只发 {@link #onTerminal}
     * （跳档收口不是逐阶段迁移，按阶段事件流重建进度的消费者少的是「进入末阶段」一跳）。
     * 另：上下文在位由当前唯一生产调用链（跑批线程桥内）保证——通知口是 public，未来若加
     * 桥外调用点，观察者不能假定重建上下文成立（写受隔离保护的表前自查）。</p>
     *
     * @param task      任务行（含 {@code biz_ref}，供定位业务行；此刻已带新 stage 与 RUNNING 态）
     * @param fromStage 迁移前阶段标识
     * @param toStage   迁移后阶段标识（与 {@code task.getStage()} 一致）
     */
    default void onStage(SaAsyncTask task, String fromStage, String toStage) {
    }
}
