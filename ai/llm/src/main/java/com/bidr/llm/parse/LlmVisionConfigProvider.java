package com.bidr.llm.parse;

import com.bidr.document.VisionConfigProvider;
import com.bidr.document.VisionEndpoint;
import com.bidr.llm.provider.ModelConfigProvider;

import java.net.Proxy;

/**
 * Title: LlmVisionConfigProvider
 * Description: <b>视觉配置口适配器</b>（M16 棒 3）：把 llm 的 {@link ModelConfigProvider}
 * （purpose={@link FileMarkdownService#PURPOSE_VISION}，DbAware 实现数据库系统参数优先、
 * {@code llm.vision.*} yaml 回落）适配给 tool/document 内置视觉腿的
 * {@link VisionConfigProvider}——core 的转录能力与 llm 的模型配置机制由此解耦
 * （tool/document 零 sys_config，R-9）。取不到返回 null（门面据此抛带操作指引的异常）。
 * 多模态调用代理随 {@code llm.proxy.*}（与文本模型同源）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class LlmVisionConfigProvider implements VisionConfigProvider {

    private final ModelConfigProvider delegate;

    private final Proxy proxy;

    public LlmVisionConfigProvider(ModelConfigProvider delegate, Proxy proxy) {
        this.delegate = delegate;
        this.proxy = proxy;
    }

    @Override
    public VisionEndpoint resolve() {
        if (delegate == null) {
            return null;
        }
        String baseUrl = delegate.getBaseUrl(FileMarkdownService.PURPOSE_VISION);
        String modelName = delegate.getModelName(FileMarkdownService.PURPOSE_VISION);
        if (!hasText(baseUrl) || !hasText(modelName)) {
            return null;
        }
        return VisionEndpoint.builder()
                .baseUrl(baseUrl)
                .apiKey(delegate.getApiKey(FileMarkdownService.PURPOSE_VISION, null))
                .modelName(modelName)
                .timeoutSeconds(delegate.getTimeoutSeconds(FileMarkdownService.PURPOSE_VISION))
                .proxy(proxy)
                .build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
