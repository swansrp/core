package com.bidr.parse;

import com.bidr.task.AsyncStageEnum;

/**
 * Title: ParseEngineStage
 * Description: 解析编排的五阶段词汇（{@code @AsyncTaskType.stageEnum} 指到它喂进度文案）：
 * INTAKE（受理/断点）→ CONVERT（office 转 PDF）→ TRANSCRIBE（外部引擎/直通/内置三态取字）→
 * PERSIST（页图渲染＋产物快照交付）→ ENRICH（注册腿增强）→ DONE。
 *
 * <p>🔴 阶段不可裁（R-10）：没有 skip-stage 开关；某阶段没内容做＝自然空转
 * （CONVERT 非白名单直通、ENRICH 不注册腿即空转）。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
public enum ParseEngineStage implements AsyncStageEnum {

    /** 受理：解析 payload、算源 sha、验 D-11 裁剪键、断点秒级命中 */
    INTAKE("INTAKE", "受理", 1),

    /** office 转 PDF（格式归一：office 类才有页码；非 office 直通，失败按原件解析兜底） */
    CONVERT("CONVERT", "格式转换", 2),

    /** 取字：直通/外部引擎异步/内置直读三态（等待＝pending 退避，不占线程不占租约） */
    TRANSCRIBE("TRANSCRIBE", "解析文档", 3),

    /** 产物落库收口：页图渲染（D-11 可裁）＋ ARTIFACTS 快照交付 */
    PERSIST("PERSIST", "产物落库", 4),

    /** 增强腿（业务注册；不注册＝空转） */
    ENRICH("ENRICH", "增强处理", 5),

    /** 完成 */
    DONE("DONE", "完成", 6);

    private final String value;

    private final String label;

    private final int order;

    ParseEngineStage(String value, String label, int order) {
        this.value = value;
        this.label = label;
        this.order = order;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
