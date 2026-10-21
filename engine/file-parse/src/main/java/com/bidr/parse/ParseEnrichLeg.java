package com.bidr.parse;

import com.bidr.task.RetryAware;

/**
 * Title: ParseEnrichLeg
 * Description: 解析编排的增强腿槽（引擎专用 SPI，persist 后增强）：注册实现即把
 * 引擎流程挂上自己的增强阶段（如 InkHub 的图注腿——视觉模型逐图写图注进正文；
 * 提示词是运营资产留业务，"core 定机制＋业务给实现"先例）。不注册＝阶段空转。
 *
 * <p>契约：腿自持 IO（读产物、写产物都经 {@link ParseEnrichContext} 给的键），异常如实抛——
 * 默认可重试（临时故障），重跑结果不变的永久失败让异常实现 {@link RetryAware} 返回 false。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
public interface ParseEnrichLeg {

    /** 腿名（观测留痕与快照 {@code enrichLegs} 字段用） */
    String legName();

    /** 执行增强（读写产物经上下文里的键；幂等自保证——重试会再跑一遍） */
    void enrich(ParseEnrichContext context);
}
