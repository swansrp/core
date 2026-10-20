package com.bidr.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 异步任务类型声明（类级，M16 棒 2 注解通道）：打在流程类上，声明「任务类型＋重试预算＋租约时长＋阶段文案枚举」。
 *
 * <p>阶段清单由 {@link AsyncStage} 方法注解导出，本注解只带类型级参数——替代现网每个任务类型
 * 手写一份 {@code TaskDefinition}（阶段清单＋maxRetry）的形状。带本注解的类会被
 * {@link TaskFlowRegistry} 在启动期收集并校验（阶段 order 必须从 1 连续，断号/重复即启动失败）。</p>
 *
 * <p>未打本注解的类零影响（纪律 §4.2.4-⑤）：不注册、不生成任何任务底盘行为。</p>
 *
 * @author sharp
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsyncTaskType {

    /** 任务类型（落 {@code sa_async_task.task_type}，如 PARSE / CLASSIFY，业务自定一词） */
    String value();

    /** 重试预算：失败次数超过它即置 DEAD（不再自动重试，等人工介入）；缺省 3 次 */
    int maxRetry() default 3;

    /**
     * 租约时长（毫秒）：抢占后给执行实例的独占窗口，超时视为实例失联可被重新抢占。
     * 缺省 0＝跟 yml {@code base.task.lease-ms}（模块自带缺省 900000）。
     * 租约只在阶段跑完后才续，故它必须大于「最慢的单个阶段」而不是轮询间隔。
     */
    long leaseMs() default 0L;

    /**
     * 阶段文案枚举（可选）：指到实现了 {@link AsyncStageEnum} 的现成枚举类，
     * 复用其 label 文案喂进度提示；进度百分比仍按 {@link AsyncStage#order()} 推导，不用枚举顺序。
     */
    Class<? extends AsyncStageEnum> stageEnum() default AsyncStageEnum.class;
}
