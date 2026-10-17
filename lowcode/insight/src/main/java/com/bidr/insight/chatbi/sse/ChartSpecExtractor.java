package com.bidr.insight.chatbi.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Title: ChartSpecExtractor
 * Description: chart-spec 代码块提取器——从模型全文中提取 ```chart-spec JSON 代码块，
 * 校验合法性并回写剔除代码块后的正文。DAG extract 结点使用本工具提取 chart-spec。
 *
 * @author Sharp
 * @since 2026/8/15
 */
@Slf4j
public final class ChartSpecExtractor {

    private static final String SPEC_FENCE = "```chart-spec";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ChartSpecExtractor() {
    }

    /**
     * 从全文中提取 ```chart-spec 代码块内的 JSON 并做格式校验；
     * {@code cleaned} 回写剔除代码块后的正文
     *
     * @return 合法 JSON 时返回字符串，否则返回 null
     */
    public static String extractSpecJson(String fullContent, StringBuilder cleaned) {
        if (!StringUtils.hasText(fullContent)) {
            cleaned.append(fullContent == null ? "" : fullContent);
            return null;
        }
        int start = fullContent.indexOf(SPEC_FENCE);
        if (start < 0) {
            cleaned.append(fullContent);
            return null;
        }
        int fenceLineEnd = fullContent.indexOf('\n', start);
        int jsonStart = fenceLineEnd < 0 ? start + SPEC_FENCE.length() : fenceLineEnd + 1;
        int end = fullContent.indexOf("```", jsonStart);
        if (end < 0) {
            cleaned.append(fullContent);
            return null;
        }
        String json = fullContent.substring(jsonStart, end).trim();
        cleaned.append(fullContent, 0, start)
                .append(fullContent.substring(Math.min(end + 3, fullContent.length())));
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            OBJECT_MAPPER.readTree(json);
            return json;
        } catch (Exception e) {
            log.warn("chart-spec JSON 校验失败: {}", e.getMessage());
            return null;
        }
    }
}
