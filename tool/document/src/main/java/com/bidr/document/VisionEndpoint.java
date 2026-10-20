package com.bidr.document;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.net.Proxy;

/**
 * Title: VisionEndpoint
 * Description: 多模态（视觉）模型端点配置——内置视觉腿（扫描页/图片转录）的调用参数。
 * 形状自 {@code com.bidr.llm.parse.VisionModelConfig} 下沉（baseUrl/apiKey/modelName/timeoutSeconds，
 * 缺省值不变），多一个可空 {@code proxy}（llm 侧适配器按 {@code llm.proxy.*} 口径注入）。
 * 配置解析顺序：调用时显式传入 → {@link VisionConfigProvider}（llm 侧默认适配器＝
 * ModelConfigProvider purpose=VISION，数据库系统参数优先）→ 都取不到抛带操作指引的异常。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionEndpoint {

    /** OpenAI 兼容端点（通常含 /v1） */
    private String baseUrl;

    /** API 密钥 */
    private String apiKey;

    /** 模型名，须支持图片输入（如 qwen-vl-max） */
    private String modelName;

    /** 调用超时（秒），图片转录耗时较长，建议不低于 120 */
    @Builder.Default
    private long timeoutSeconds = 180L;

    /** 多模态调用 HTTP 代理：null＝直连 */
    private Proxy proxy;

    /**
     * 配置是否可用：<b>地址＋模型名</b>齐备即可（M16 修复轮 A12）。
     * {@code apiKey} 不作必要条件——本机 / 内网部署的 OpenAI 兼容端点（vLLM、Ollama、
     * 自建网关）常不收密钥，要求密钥等于把这类现场判成「没配视觉腿」。
     * 真需要密钥的端点会在调用时回 401，那是有信息的失败，比静默降级成「未配置」好排查。
     */
    public boolean isUsable() {
        return hasText(baseUrl) && hasText(modelName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
