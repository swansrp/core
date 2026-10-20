package com.bidr.task;

/**
 * 阶段方法的三态返回（M16 棒 2）：🔴 取代现网的 {@code boolean}——那次 {@code false} 同时表示
 * 「推进一步」和「没推进」，是现网最容易读错的一行；从此「没做完」与「推进一步」是两种写法。
 *
 * <ul>
 *   <li>{@link #to(String)}：推进到目标阶段后结束本轮（推进与落库由切面做）；
 *       {@code to(当前阶段)} 合法（提交完把当前档再写一遍的现网形状），{@code to(order 更小)} 切面当场抛；</li>
 *   <li>{@link #pending(long)}：本轮暂缓、原地不动，退避指定毫秒后再来（如外部引擎还在跑、队列积压）；</li>
 *   <li>{@link #done()}：整个任务完成，切面收口 SUCCESS 并通知观察者。</li>
 * </ul>
 *
 * <p>异常不属三态：直接抛出，由切面按 {@link RetryAware} 分诊（可重试＝FAILED＋退避、
 * 预算耗尽或不可重试＝DEAD）。</p>
 *
 * @author sharp
 */
public final class StageOutcome {

    /** 三态种类 */
    public enum Kind {

        /** 推进到目标阶段（原地或前进） */
        ADVANCE,

        /** 本轮暂缓：原地不动，退避后再来 */
        PENDING,

        /** 整任务完成 */
        DONE
    }

    private final Kind kind;

    /** ADVANCE 的目标阶段标识 */
    private final String targetStage;

    /** PENDING 的退避毫秒 */
    private final long backoffMs;

    private StageOutcome(Kind kind, String targetStage, long backoffMs) {
        this.kind = kind;
        this.targetStage = targetStage;
        this.backoffMs = backoffMs;
    }

    /** 推进到目标阶段（阶段标识须在本类型阶段清单内，order 只许原地或前进） */
    public static StageOutcome to(String stageValue) {
        return new StageOutcome(Kind.ADVANCE, stageValue, 0L);
    }

    /** 推进到目标阶段（枚举便捷口，取其 {@link AsyncStageEnum#getValue()}） */
    public static StageOutcome to(AsyncStageEnum stage) {
        return new StageOutcome(Kind.ADVANCE, stage.getValue(), 0L);
    }

    /** 本轮暂缓：原地不动，退避 {@code backoffMs} 毫秒后再来 */
    public static StageOutcome pending(long backoffMs) {
        return new StageOutcome(Kind.PENDING, null, backoffMs);
    }

    /** 整任务完成（切面置 SUCCESS 并通知观察者） */
    public static StageOutcome done() {
        return new StageOutcome(Kind.DONE, null, 0L);
    }

    public Kind getKind() {
        return kind;
    }

    /** ADVANCE 的目标阶段标识（其余两态为 null） */
    public String getTargetStage() {
        return targetStage;
    }

    /** PENDING 的退避毫秒（其余两态为 0） */
    public long getBackoffMs() {
        return backoffMs;
    }

    @Override
    public String toString() {
        return "StageOutcome{" + kind + (targetStage != null ? ", to=" + targetStage : "")
                + (kind == Kind.PENDING ? ", backoffMs=" + backoffMs : "") + '}';
    }
}
