package com.bidr.document;

/**
 * Title: VisionTranscriber
 * Description: <b>多模态（视觉）转录口</b>——扫描页/图片交 OpenAI 协议多模态模型转成 Markdown
 * 的能力端点（{@link BuiltInDispatcher}/{@code PdfTextExtractor} 的内置视觉腿）。
 * <p>
 * 2026-11-07 C′ 迁移（lc4j 1.x 全家 Java17 字节码，本模块留 JDK8 故零 langchain4j 依赖）：
 * 原 {@code builtin.VisionTranscriber} 实现体（VISION_PROMPT／resolve 优先级／OpenAiChatModel
 * 调用）整体迁 {@code ai/llm} 的 {@code com.bidr.llm.document.LlmVisionTranscriber}，本包只留
 * 接口与本模块占位实现（{@code builtin.NoopVisionTranscriber}，被调即抛操作指引——
 * 🔴 不许静默出空 md）。端点解析顺序不变：调用时显式传入 →
 * {@link VisionConfigProvider}（llm 侧默认适配器）→ 均不可用抛带操作指引的
 * {@link IllegalStateException}（该优先级逻辑在真实现体内）。
 *
 * @author Sharp
 * @since 2026/11/7
 */
public interface VisionTranscriber {

    /**
     * 图片交多模态模型转录为 Markdown。
     *
     * @param imageBytes 图片字节（渲染出的 PNG 或原图）
     * @param mimeType   图片 MIME 类型
     * @param explicit   调用方显式传入的端点配置（可空）
     * @return Markdown 正文
     */
    String transcribe(byte[] imageBytes, String mimeType, VisionEndpoint explicit);
}
