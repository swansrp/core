package com.bidr.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 异步任务阶段声明（方法级，M16 棒 2 注解通道）：打在流程类的阶段方法上。
 *
 * <p>阶段方法签名定死：{@code StageOutcome xxx(SaAsyncTask task, AsyncStageScope scope)}——
 * 参数一＝任务行（当前阶段＝本方法），参数二＝切面注入的阶段作用域（{@code snapshot}/{@code claimExternal}/
 * {@code externalId} 三个写口）。返回 {@link StageOutcome} 三态之一；跳阶段不设注解属性，
 * 由返回值 {@code StageOutcome.to(目标阶段)} 表达（线性 next 接不住「非 office 直接跳过转换」这类分支）。</p>
 *
 * <p>🔴 {@code order} 必须从 1 连续（守卫测试钉住）：进度百分比按 order 推导，断号即推导失真。
 * 阶段迁移只许原地或前进、禁回跳——{@code to(order 更小的阶段)} 切面当场抛。</p>
 *
 * @author sharp
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AsyncStage {

    /** 阶段标识（落 {@code sa_async_task.stage}） */
    String value();

    /** 序号：从 1 起、必须连续（1..N），断号/重复＝启动期直接失败 */
    int order();

    /**
     * 命中即不重试的异常族（补 {@link RetryAware} 之外的口径）：本阶段抛出这些类型
     * （含子类）时直接置 DEAD，不吃重试预算。
     */
    Class<? extends Throwable>[] noRetryOn() default {};
}
