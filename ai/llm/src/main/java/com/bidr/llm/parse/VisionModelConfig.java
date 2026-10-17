package com.bidr.llm.parse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 多模态（视觉）模型配置：调用方在调用 {@link FileMarkdownService} 时显式传入。
 * <p>
 * 形状与现有聊天模型配置一致（baseUrl / apiKey / modelName / timeoutSeconds），
 * 兼容一切 OpenAI 协议的视觉模型（qwen-vl 系列等）。未显式传入时由
 * {@link FileMarkdownService} 按 ModelConfigProvider（数据库 sys_config 优先）解析。
 * </p>
 *
 * @author Sharp
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionModelConfig {

    /**
     * OpenAI 兼容端点（通常含 /v1）
     */
    private String baseUrl;

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 模型名，须支持图片输入（如 qwen-vl-max）
     */
    private String modelName;

    /**
     * 调用超时（秒），图片转录耗时较长，建议不低于 120
     */
    @Builder.Default
    private long timeoutSeconds = 180L;

    /**
     * 配置是否可用：地址、密钥、模型名三者齐备
     */
    public boolean isUsable() {
        return hasText(baseUrl) && hasText(apiKey) && hasText(modelName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
