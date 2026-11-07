package com.bidr.llm.document;

import com.bidr.document.VisionConfigProvider;
import com.bidr.document.VisionEndpoint;
import com.bidr.document.VisionTranscriber;
import com.bidr.llm.config.LlmDefaultAutoConfiguration;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Base64;

/**
 * Title: LlmVisionTranscriber
 * Description: <b>多模态（视觉）转录口真实现</b>——原 {@code tool/document} 内置腿
 * {@code builtin.VisionTranscriber} 的 lc4j 逻辑整体迁入（C′ 方案，2026-11-07）：图片交
 * OpenAI 协议多模态模型转录为 Markdown。配置解析优先级不变：显式传入 →
 * {@link VisionConfigProvider}（llm 侧适配器：数据库系统参数优先、{@code llm.vision.*} 回落），
 * 均不可用时抛带操作指引的 {@link IllegalStateException}（🔴 不许改成静默出空 md）。
 * <p>
 * lc4j 1.23 适配（迁入即翻新，与 {@code RefreshableChatModel} 同口径）：builder 无
 * {@code proxy(Proxy)}——代理经 JDK HttpClient（{@link JdkHttpClientBuilder}）挂
 * {@code httpClientBuilder}；{@code generate} 收敛为 {@link ChatModel#chat}。
 * document 侧只留接口＋占位实现（lc4j 1.x 全家 Java17 字节码，document 留 JDK8 零 lc4j 依赖）。
 *
 * @author Sharp
 * @since 2026/11/7
 */
@Slf4j
public class LlmVisionTranscriber implements VisionTranscriber {

    /** 多模态转录提示词：约束输出为 Markdown 且不增删内容（历史原句，一字不动） */
    private static final String VISION_PROMPT = "请把图片中的内容完整转录为 Markdown 格式："
            + "保留标题层级与列表结构，表格使用 Markdown 表格语法输出；"
            + "不要遗漏图片中的文字，也不要添加图片中不存在的内容；直接输出 Markdown 正文。";

    private final VisionConfigProvider configProvider;

    public LlmVisionTranscriber(VisionConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * 图片交多模态模型转录为 Markdown。
     *
     * @param imageBytes 图片字节（渲染出的 PNG 或原图）
     * @param mimeType   图片 MIME 类型
     * @param explicit   调用方显式传入的端点配置（可空）
     */
    @Override
    public String transcribe(byte[] imageBytes, String mimeType, VisionEndpoint explicit) {
        VisionEndpoint config = resolve(explicit);
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 180L))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false);
        // 1.23 起 builder 无 proxy(Proxy)：代理腿经 JDK HttpClient（与 RefreshableChatModel 同源）
        JdkHttpClientBuilder httpClient = LlmDefaultAutoConfiguration.buildLc4jHttpClientBuilder(config.getProxy());
        if (httpClient != null) {
            builder.httpClientBuilder(httpClient);
        }
        ChatModel model = builder.build();
        UserMessage message = UserMessage.from(
                TextContent.from(VISION_PROMPT),
                ImageContent.from(Base64.getEncoder().encodeToString(imageBytes), mimeType));
        log.info("多模态模型转录扫描内容：model={}, imageSize={}KB", config.getModelName(), imageBytes.length / 1024);
        ChatResponse response = model.chat(message);
        return response.aiMessage().text();
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
