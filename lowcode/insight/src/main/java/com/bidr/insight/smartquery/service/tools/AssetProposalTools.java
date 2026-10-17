package com.bidr.insight.smartquery.service.tools;

import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.service.ProposalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Title: AssetProposalTools
 * Description: 自主维护 agent 的资产提案工具（langchain4j function calling）：
 * propose_asset 逐项落待审提案（InsightAgentProposal status=0，类型白名单 + JSON 结构守卫，
 * 本批次内 (type,itemKey) 去重防重复提）、list_proposals 查本 Agent 待审清单（已有提案不重复提）。
 * 提案经既有审批页合并进草稿（发布+刷新生效），运行期资产零污染
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class AssetProposalTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 资产类型白名单（与提案表 asset_type 口径一致） */
    private static final Set<String> TYPES = new HashSet<>(java.util.Arrays.asList(
            "metrics", "dimensions", "relations", "value-domains", "concepts", "sensitive-fields", "row-policies"));
    /** 单批次提案数上限（防 LLM 刷提案） */
    private static final int MAX_PROPOSALS_PER_BATCH = 50;
    /** 理由文本最大长度（超长截断入库） */
    private static final int REASON_MAX_LEN = 500;

    private final ProposalService proposalService;
    private final String agentCode;
    private final String batchNo;
    private final String question;
    /** 触发本次维护的 semantic_query 原文（提案记录追溯用） */
    private final String sqJson;
    /** 当前生效语义层（含临时建议资产时为叠加层），判 add/update */
    private final SemanticLayer layer;
    private final Consumer<String> logSink;
    private final BooleanSupplier stopChecker;

    /** 本批次已提案 (type|itemKey)，重复提案直接拒绝 */
    private final Set<String> proposedKeys = new HashSet<>();

    public AssetProposalTools(ProposalService proposalService, String agentCode, String batchNo,
                              String question, String sqJson, SemanticLayer layer,
                              Consumer<String> logSink, BooleanSupplier stopChecker) {
        this.proposalService = proposalService;
        this.agentCode = agentCode;
        this.batchNo = batchNo;
        this.question = question;
        this.sqJson = sqJson;
        this.layer = layer;
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

    @Tool("提交一项资产变更建议（落待审提案，管理员审批合并后才生效）："
            + "type 取 metrics/dimensions/relations/value-domains/concepts/sensitive-fields/row-policies；"
            + "content_json 为单项资产对象（前四类须含 name，value-domains/sensitive-fields 须含 entity 与 field，"
            + "row-policies 须含 table/column/op/value，value 支持登录态模板 ${user.customerNumber}）；"
            + "reason 说明为什么需要这项资产")
    public String proposeAsset(@P("资产类型：metrics/dimensions/relations/value-domains/concepts/sensitive-fields/row-policies") String type,
                               @P("单项资产 JSON 对象（按资产协议结构）") String contentJson,
                               @P("建议理由（一句话）") String reason) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        String t = type == null ? "" : type.trim();
        if (!TYPES.contains(t)) {
            return "{\"ok\":false,\"error\":\"非法资产类型 '" + t + "'，允许：" + TYPES + "\"}";
        }
        JsonNode content;
        try {
            content = OM.readTree(contentJson);
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"content_json 不是合法 JSON: " + e.getMessage() + "\"}";
        }
        if (content == null || !content.isObject()) {
            return "{\"ok\":false,\"error\":\"content_json 必须是 JSON 对象\"}";
        }
        // itemKey 提取与结构守卫（按类型）
        String itemKey;
        if ("value-domains".equals(t) || "sensitive-fields".equals(t)) {
            String entity = content.path("entity").asText("");
            String field = content.path("field").asText("");
            if (entity.isEmpty() || field.isEmpty()) {
                return "{\"ok\":false,\"error\":\"" + t + " 资产必须包含非空 entity 与 field 字段\"}";
            }
            itemKey = entity + "." + field;
        } else if ("row-policies".equals(t)) {
            String table = content.path("table").asText("");
            String column = content.path("column").asText("");
            String op = content.path("op").asText("");
            if (table.isEmpty() || column.isEmpty() || op.isEmpty()) {
                return "{\"ok\":false,\"error\":\"row-policies 资产必须包含非空 table、column、op 字段\"}";
            }
            itemKey = table + "." + column;
        } else {
            itemKey = content.path("name").asText("");
            if (itemKey.isEmpty()) {
                return "{\"ok\":false,\"error\":\"" + t + " 资产必须包含非空 name 字段\"}";
            }
        }
        if (proposedKeys.size() >= MAX_PROPOSALS_PER_BATCH) {
            return "{\"ok\":false,\"error\":\"本批次提案已达上限 " + MAX_PROPOSALS_PER_BATCH + " 项，停止提交\"}";
        }
        if (!proposedKeys.add(t + "|" + itemKey)) {
            return "{\"ok\":false,\"error\":\"本批次已提交过 '" + t + ":" + itemKey + "'，请勿重复提交\"}";
        }
        String op = resolveOp(t, itemKey);
        String safeContent = content.toString();
        String safeReason = reason == null ? "" : (reason.length() > REASON_MAX_LEN
                ? reason.substring(0, REASON_MAX_LEN) : reason);
        try {
            int id = proposalService.saveOne(agentCode, batchNo, question, sqJson, t, itemKey, op,
                    safeContent, safeReason);
            report("propose_asset：" + t + " '" + itemKey + "'（" + op + "，提案 #" + id + "）");
            ObjectNode out = OM.createObjectNode();
            out.put("ok", true);
            out.put("proposalId", id);
            out.put("type", t);
            out.put("key", itemKey);
            out.put("op", op);
            return out.toString();
        } catch (Exception e) {
            log.warn("Agent '{}' 提案落库失败: {}", agentCode, e.getMessage());
            return "{\"ok\":false,\"error\":\"提案落库失败: " + e.getMessage() + "\"}";
        }
    }

    @Tool("查看本 Agent 当前待审提案清单（先查再提，已有提案不重复提交）")
    public String listProposals() {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        report("list_proposals：查询待审提案…");
        List<InsightAgentProposal> pending = proposalService.listProposals(agentCode, "0");
        ObjectNode out = OM.createObjectNode();
        out.put("count", pending.size());
        ArrayNode arr = out.putArray("proposals");
        for (InsightAgentProposal p : pending) {
            ObjectNode item = arr.addObject();
            item.put("id", p.getId());
            item.put("type", p.getAssetType());
            item.put("key", p.getItemKey());
            item.put("op", p.getOp());
            String reason = p.getReason() == null ? "" : p.getReason();
            item.put("reason", reason.length() > 100 ? reason.substring(0, 100) + "..." : reason);
        }
        return out.toString();
    }

    /** 按当前语义层判 add/update（sensitive-fields 恒 add） */
    private String resolveOp(String type, String itemKey) {
        boolean exists;
        switch (type) {
            case "metrics":
                exists = layer.metricMap().containsKey(itemKey);
                break;
            case "dimensions":
                exists = layer.dimensionMap().containsKey(itemKey);
                break;
            case "relations":
                exists = layer.relations().stream().anyMatch(r -> itemKey.equals(r.getName()));
                break;
            case "value-domains":
                exists = layer.domains().containsKey(itemKey);
                break;
            case "concepts":
                exists = layer.conceptNames().contains(itemKey);
                break;
            default:
                return "add";
        }
        return exists ? "update" : "add";
    }
}
