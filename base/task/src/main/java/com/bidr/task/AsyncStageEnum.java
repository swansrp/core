package com.bidr.task;

/**
 * 阶段文案枚举口（M16 棒 2）：业务侧现成的阶段枚举实现本口，即可被
 * {@link AsyncTaskType#stageEnum()} 指到、复用 label 文案喂进度提示。
 *
 * <p>进度百分比仍按 {@link AsyncStage#order()} 推导（枚举的 order 只出文案，
 * 与注解 order 冲突时以注解为准）。</p>
 *
 * @author sharp
 */
public interface AsyncStageEnum {

    /** 阶段标识（与 {@link AsyncStage#value()} 同词表） */
    String getValue();

    /** 展示名（前端进度提示直接用） */
    String getLabel();

    /** 顺序（从 1 起） */
    int getOrder();
}
