package com.bidr.document.builtin;

import com.bidr.document.VisionEndpoint;
import com.bidr.document.VisionTranscriber;

/**
 * Title: NoopVisionTranscriber
 * Description: 多模态转录<b>占位实现</b>（lc4j 1.x 全家 Java17 字节码，本模块留 JDK8 故
 * 零 langchain4j 依赖——真实现体在 {@code ai/llm} 的
 * {@code com.bidr.llm.document.LlmVisionTranscriber}，llm 在场时经
 * {@code @ConditionalOnMissingBean} 让位机制装配真身）。被调即抛带操作指引的
 * {@link IllegalStateException}——🔴 不许静默出空 md（指引文案＝历史 {@code resolve()}
 * 原文一字不动）。
 *
 * @author Sharp
 * @since 2026/11/7
 */
public class NoopVisionTranscriber implements VisionTranscriber {

    @Override
    public String transcribe(byte[] imageBytes, String mimeType, VisionEndpoint explicit) {
        throw new IllegalStateException("扫描件/图片解析需要多模态（视觉）模型：请调用时传入视觉模型配置，"
                + "或经 VisionConfigProvider 提供多模态模型端点（llm 侧默认适配器＝在系统参数管理页配置"
                + "「多模态模型服务地址/多模态模型」，或配置应用 llm.vision.* 项）");
    }
}
