package com.bidr.llm.execute;

import org.springframework.util.StringUtils;

/**
 * 模型输出清洗工具。
 * 用于移除模型误输出的 thinking 标签、英文推理前缀和多余空白行。
 *
 * @author Sharp
 */
public final class ModelOutputSanitizer {

    private ModelOutputSanitizer() {
    }

    /**
     * 清洗模型输出。
     *
     * @param text 模型输出
     * @return 清洗后的模型输出
     */
    public static String sanitize(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String sanitized = text;
        sanitized = sanitized.replaceAll("(?is)<think>.*?</think>", "");
        sanitized = sanitized.replaceAll("(?is)<thinking>.*?</thinking>", "");
        sanitized = sanitized.replaceAll("(?im)^here'?s a thinking process:?\\s*", "");
        sanitized = sanitized.replaceAll("(?im)^thought process:?\\s*", "");
        sanitized = sanitized.replaceAll("(?m)^[ \\t]*\\r?\\n", "");
        return sanitized.trim();
    }
}
