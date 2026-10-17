package com.bidr.insight.smartquery.semantic;

import com.bidr.insight.smartquery.model.SemanticQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Title: SmartQueryParser
 * Description: semantic_query JSON → 协议模型。支持两种输入形态：
 * 裸 semantic_query 对象，或包裹在 {"semantic_query": {...}} 中（LLM Step 6 输出）。
 * 数值字段（limit/time.n/window.top_n）非整数时置 null，类型合法性由
 * SemanticQueryValidator 对照 raw 节点判定（与 Python isinstance 行为对齐）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
@RequiredArgsConstructor
public class SmartQueryParser {

    private static final ObjectMapper OM = new ObjectMapper();

    @Data
    public static class ParseResult {
        private SemanticQuery query;
        /** semantic_query 的原始节点（校验器数值类型检查用） */
        private JsonNode raw;
    }

    /** 入参为 JSON 字符串；非法 JSON 抛 IllegalArgumentException */
    public ParseResult parse(String json) {
        JsonNode root;
        try {
            root = OM.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("semantic_query 不是合法 JSON: " + e.getMessage(), e);
        }
        return parse(root);
    }

    public ParseResult parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("semantic_query 输入不是 JSON 对象");
        }
        JsonNode sqNode = root.has("semantic_query") && root.get("semantic_query").isObject()
                ? root.get("semantic_query") : root;
        ParseResult r = new ParseResult();
        r.setRaw(sqNode);
        try {
            r.setQuery(OM.treeToValue(sqNode, SemanticQuery.class));
        } catch (Exception e) {
            throw new IllegalArgumentException("semantic_query 结构解析失败: " + e.getMessage(), e);
        }
        return r;
    }
}
