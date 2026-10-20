package com.bidr.document.builtin;

import com.bidr.document.VisionConfigProvider;
import com.bidr.document.VisionEndpoint;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Base64;

/**
 * Title: VisionTranscriber
 * Description: 内置视觉腿＝历史 {@code FileMarkdownService.visionTranscribe}/{@code resolveVisionConfig}
 * 逐字迁入：图片交 OpenAI 协议多模态模型转录为 Markdown。配置解析：显式传入 →
 * {@link VisionConfigProvider}（llm 侧适配器：数据库系统参数优先、{@code llm.vision.*} 回落），
 * 均不可用时抛带操作指引的 {@link IllegalStateException}（🔴 不许改成静默出空 md）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Slf4j
public class VisionTranscriber {

    /** 多模态转录提示词：约束输出为 Markdown 且不增删内容（历史原句，一字不动） */
    private static final String VISION_PROMPT = "请把图片中的内容完整转录为 Markdown 格式："
            + "保留标题层级与列表结构，表格使用 Markdown 表格语法输出；"
            + "不要遗漏图片中的文字，也不要添加图片中不存在的内容；直接输出 Markdown 正文。";

    private final VisionConfigProvider configProvider;

    public VisionTranscriber(VisionConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * 图片交多模态模型转录为 Markdown。
     *
     * @param imageBytes 图片字节（渲染出的 PNG 或原图）
     * @param mimeType   图片 MIME 类型
     * @param explicit   调用方显式传入的端点配置（可空）
     */
    public String transcribe(byte[] imageBytes, String mimeType, VisionEndpoint explicit) {
        VisionEndpoint config = resolve(explicit);
        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 180L))
                .maxRetries(1)
                .proxy(config.getProxy())
                .logRequests(false)
                .logResponses(false)
                .build();
        UserMessage message = UserMessage.from(
                TextContent.from(VISION_PROMPT),
                ImageContent.from(Base64.getEncoder().encodeToString(imageBytes), mimeType));
        log.info("多模态模型转录扫描内容：model={}, imageSize={}KB", config.getModelName(), imageBytes.length / 1024);
        Response<AiMessage> response = model.generate(message);
        return response.content().text();
    }

    /**
     * 解析多模态端点：显式传入优先；否则问 {@link VisionConfigProvider}；均不可用抛带操作指引的异常。
     */
    private VisionEndpoint resolve(VisionEndpoint explicit) {
        if (explicit != null && explicit.isUsable()) {
            return explicit;
        }
        if (configProvider != null) {
            VisionEndpoint resolved = configProvider.resolve();
            if (resolved != null && resolved.isUsable()) {
                return resolved;
            }
        }
        throw new IllegalStateException("扫描件/图片解析需要多模态（视觉）模型：请调用时传入视觉模型配置，"
                + "或经 VisionConfigProvider 提供多模态模型端点（llm 侧默认适配器＝在系统参数管理页配置"
                + "「多模态模型服务地址/多模态模型」，或配置应用 llm.vision.* 项）");
    }
}
