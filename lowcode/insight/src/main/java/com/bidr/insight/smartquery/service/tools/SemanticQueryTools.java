package com.bidr.insight.smartquery.service.tools;

import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SmartQueryResult;
import com.bidr.insight.smartquery.service.SmartQueryService;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Title: SemanticQueryTools
 * Description: 自主维护 agent 的语义查询工具（langchain4j function calling）：
 * build_query 组装+校验+SQL 预览（dryRun 不执行，校验错误回 LLM 自纠）、
 * execute_query 真执行取数（limit 性能守卫，默认 100 行、上限 500）。
 * agent 强制与入口一致（stampAgent，LLM 产物不可信）；出参紧凑 JSON，
 * 单元格值截断 100 字符控制上下文膨胀；stopChecker 非空时每次调用前检查停止信号
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class SemanticQueryTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** execute_query 默认返回行数 */
    private static final int DEFAULT_LIMIT = 100;
    /** execute_query 行数上限（性能守卫，防全表拖库） */
    private static final int MAX_LIMIT = 500;
    /** 单元格值最大长度（超长截断，控制上下文） */
    private static final int CELL_MAX_LEN = 100;

    private final SmartQueryService smartQueryService;
    private final String agentCode;
    /** 过程日志上报（可为 null） */
    private final Consumer<String> logSink;
    /** 停止检查（可为 null）：任务停止时工具拒绝执行 */
    private final BooleanSupplier stopChecker;

    public SemanticQueryTools(SmartQueryService smartQueryService, String agentCode,
                              Consumer<String> logSink, BooleanSupplier stopChecker) {
        this.smartQueryService = smartQueryService;
        this.agentCode = agentCode;
        this.logSink = logSink;
        this.stopChecker = stopChecker;
    }

    private void report(String msg) {
        if (logSink != null) {
            logSink.accept(msg);
        }
    }

    private String stopGuard() {
        if (stopChecker != null && stopChecker.getAsBoolean()) {
            report("停止请求已收到，工具拒绝执行（任务收口中）");
            return "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}";
        }
        return null;
    }

    /**
     * 强制 semantic_query.agent 与目标一致 + filters/having 条件数组归一为条件树
     * （与维护链 stampAgent 同口径；LLM 常把 filters 写成数组）
     */
    private String prepare(String semanticQueryJson) {
        JsonNode root;
        try {
            root = OM.readTree(semanticQueryJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("semantic_query 不是合法 JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("semantic_query 输入不是 JSON 对象");
        }
        ObjectNode obj = (ObjectNode) root;
        obj.put("agent", SemanticLayerRegistry.isDefault(agentCode) ? "default" : agentCode);
        normalizeFilterTree(obj, "filters");
        normalizeFilterTree(obj, "having");
        try {
            return OM.writeValueAsString(obj);
        } catch (Exception e) {
            throw new IllegalArgumentException("semantic_query 序列化失败: " + e.getMessage());
        }
    }

    /** LLM 常把 filters/having 写成条件数组：包成 AND 条件树（协议只收对象） */
    private void normalizeFilterTree(ObjectNode obj, String field) {
        JsonNode node = obj.get(field);
        if (node != null && node.isArray()) {
            if (node.size() == 0) {
                obj.remove(field);
            } else if (node.size() == 1) {
                obj.set(field, node.get(0));
            } else {
                ObjectNode wrap = OM.createObjectNode();
                wrap.put("operator", "AND");
                wrap.set("conditions", node);
                obj.set(field, wrap);
            }
        }
    }

    @Tool("组装并校验语义查询（不执行）：输入 semantic_query JSON，返回校验结果与 SQL 预览；"
            + "校验不通过时返回错误清单，请修正后重试（自纠循环）")
    public String buildQuery(@P("semantic_query JSON 对象（metric 或 list 查询协议）") String semanticQueryJson) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        report("build_query：校验语义查询…");
        String prepared;
        try {
            prepared = prepare(semanticQueryJson);
        } catch (IllegalArgumentException e) {
            return "{\"valid\":false,\"errors\":[" + quote(e.getMessage()) + "]}";
        }
        SmartQueryResult r = smartQueryService.dryRun(prepared);
        ObjectNode out = OM.createObjectNode();
        if (r.isValid()) {
            out.put("valid", true);
            out.put("sql", r.getSql());
            ArrayNode cols = out.putArray("columns");
            if (r.getColumns() != null) {
                for (GenResult.ColumnInfo c : r.getColumns()) {
                    cols.add(c.getAlias());
                }
            }
            if (r.getNotes() != null && !r.getNotes().isEmpty()) {
                ArrayNode notes = out.putArray("notes");
                r.getNotes().forEach(notes::add);
            }
            if (r.getWarnings() != null && !r.getWarnings().isEmpty()) {
                ArrayNode warns = out.putArray("warnings");
                r.getWarnings().forEach(w -> warns.add("[" + w.getField() + "] " + w.getMessage()));
            }
            report("build_query：校验通过，SQL 已生成");
        } else {
            out.put("valid", false);
            ArrayNode errs = out.putArray("errors");
            if (r.getErrors() != null) {
                for (com.bidr.insight.smartquery.model.ValidationResult.Issue issue : r.getErrors()) {
                    errs.add("[" + issue.getField() + "] " + issue.getMessage());
                }
            }
            if (errs.size() == 0 && r.getErrorMessage() != null) {
                errs.add(r.getErrorMessage());
            }
            report("build_query：校验未通过（" + errs.size() + " 项错误，回传自纠）");
        }
        return writeCompact(out);
    }

    @Tool("执行语义查询取真实数据：输入 semantic_query JSON，返回列清单与结果行（默认最多 100 行）；"
            + "先经 build_query 语义校验，引用不存在资产会被拒绝")
    public String executeQuery(@P("semantic_query JSON 对象（建议先用 build_query 校验通过）") String semanticQueryJson,
                               @P("返回行数上限，可空（默认 100，最大 500）") Integer limit) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        report("execute_query：执行语义查询（limit=" + (limit == null ? DEFAULT_LIMIT : limit) + "）…");
        String prepared;
        try {
            prepared = prepare(semanticQueryJson);
        } catch (IllegalArgumentException e) {
            return "{\"ok\":false,\"error\":" + quote(e.getMessage()) + "}";
        }
        // 性能守卫：limit 夹取进 [1, 500]，写进查询协议（SQL 生成器末位恒带 LIMIT）
        int rowLimit = limit == null ? DEFAULT_LIMIT : Math.max(1, Math.min(MAX_LIMIT, limit));
        try {
            JsonNode node = OM.readTree(prepared);
            if (node.isObject()) {
                ((ObjectNode) node).put("limit", rowLimit);
                prepared = OM.writeValueAsString(node);
            }
        } catch (Exception ignored) {
            // 上方刚解析成功，此处不会失败；兜底用原串执行
        }
        SmartQueryResult r = smartQueryService.run(prepared, true, null);
        if (!r.isValid()) {
            ObjectNode out = OM.createObjectNode();
            out.put("ok", false);
            ArrayNode errs = out.putArray("errors");
            if (r.getErrors() != null) {
                for (com.bidr.insight.smartquery.model.ValidationResult.Issue issue : r.getErrors()) {
                    errs.add("[" + issue.getField() + "] " + issue.getMessage());
                }
            }
            if (errs.size() == 0 && r.getErrorMessage() != null) {
                errs.add(r.getErrorMessage());
            }
            report("execute_query：校验未通过，错误回传");
            return writeCompact(out);
        }
        if (r.getRows() == null) {
            // 数据源未配置等执行期缺失：SmartQueryService 仅返 SQL 与说明
            ObjectNode out = OM.createObjectNode();
            out.put("ok", false);
            out.put("error", r.getErrorMessage() == null ? "查询未执行（未知原因）" : r.getErrorMessage());
            out.put("sql", r.getSql());
            return writeCompact(out);
        }
        ObjectNode out = OM.createObjectNode();
        out.put("ok", true);
        out.put("rowCount", r.getRows().size());
        out.put("truncated", r.getRows().size() >= rowLimit);
        ArrayNode cols = out.putArray("columns");
        if (r.getColumns() != null) {
            for (GenResult.ColumnInfo c : r.getColumns()) {
                cols.add(c.getAlias());
            }
        }
        ArrayNode rows = out.putArray("rows");
        int emitted = 0;
        for (List<Object> row : r.getRows()) {
            if (emitted++ >= rowLimit) {
                break;
            }
            ArrayNode arr = rows.addArray();
            for (Object cell : row) {
                arr.add(cellText(cell));
            }
        }
        out.put("sql", r.getSql());
        if (r.getNotes() != null && !r.getNotes().isEmpty()) {
            ArrayNode notes = out.putArray("notes");
            r.getNotes().forEach(notes::add);
        }
        report("execute_query：返回 " + r.getRows().size() + " 行");
        return writeCompact(out);
    }

    /** 单元格值文本化：null→空串，超长截断（控制上下文） */
    private String cellText(Object cell) {
        if (cell == null) {
            return "";
        }
        String s = String.valueOf(cell);
        return s.length() > CELL_MAX_LEN ? s.substring(0, CELL_MAX_LEN) + "..." : s;
    }

    private static String quote(String s) {
        try {
            return new ObjectMapper().writeValueAsString(s == null ? "" : s);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private String writeCompact(JsonNode node) {
        try {
            return OM.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("语义查询工具输出序列化失败", e);
            return "{\"error\":\"输出序列化失败\"}";
        }
    }
}
