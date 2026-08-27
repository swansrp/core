package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.entity.InsightAgentProposal;
import com.bidr.insight.smartquery.dao.repository.InsightAgentProposalService;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ProposalService
 * Description: 问数资产变更提案公共服务：从 SmartQueryMaintainService 拆出落库与审批闭环，
 * 供旧维护链（一次性 /ask）与自主维护 agent（AssetProposalTools）共用。
 * 提案仍落 InsightAgentProposal（status=0 待审），合并进草稿后经既有发布+刷新生效，
 * 审批端点与前端页面零改动
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {

    private final InsightAgentProposalService proposalDaoService;
    private final SmartAgentMetaService smartAgentMetaService;
    private final AgentAssetCacheService agentAssetCacheService;

    private final ObjectMapper om = new ObjectMapper();

    /** 拆 asset_additions 为单项提案入库（status=0 待审），返回条数 */
    public int saveProposals(String agentCode, String batchNo, String question, String sqJson,
                             JsonNode additions, JsonNode reasons, SemanticLayer layer) {
        int count = 0;
        for (JsonNode m : additions.path("metrics")) {
            String name = m.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                continue;
            }
            insertProposal(agentCode, batchNo, question, sqJson, "metrics", name,
                    layer.metricMap().containsKey(name) ? "update" : "add", m, reasons);
            count++;
        }
        for (JsonNode d : additions.path("dimensions")) {
            String name = d.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                continue;
            }
            insertProposal(agentCode, batchNo, question, sqJson, "dimensions", name,
                    layer.dimensionMap().containsKey(name) ? "update" : "add", d, reasons);
            count++;
        }
        for (JsonNode rel : additions.path("relations")) {
            String name = rel.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                continue;
            }
            boolean exists = layer.relations().stream().anyMatch(r -> name.equals(r.getName()));
            insertProposal(agentCode, batchNo, question, sqJson, "relations", name,
                    exists ? "update" : "add", rel, reasons);
            count++;
        }
        JsonNode domainsNode = normalizeDomains(additions.path("value_domains").path("domains"));
        Iterator<Map.Entry<String, JsonNode>> vd = domainsNode.fields();
        while (vd.hasNext()) {
            Map.Entry<String, JsonNode> e = vd.next();
            insertProposal(agentCode, batchNo, question, sqJson, "value-domains", e.getKey(),
                    layer.domains().containsKey(e.getKey()) ? "update" : "add", e.getValue(), reasons);
            count++;
        }
        for (JsonNode c : additions.path("concepts")) {
            String name = c.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                continue;
            }
            insertProposal(agentCode, batchNo, question, sqJson, "concepts", name,
                    layer.conceptNames().contains(name) ? "update" : "add", c, reasons);
            count++;
        }
        for (JsonNode f : additions.path("sensitive_fields")) {
            String key = f.path("entity").asText("") + "." + f.path("field").asText("");
            insertProposal(agentCode, batchNo, question, sqJson, "sensitive-fields", key,
                    "add", f, reasons);
            count++;
        }
        return count;
    }

    /**
     * 单项提案落库（自主维护 agent 的 AssetProposalTools 逐项调用）：
     * itemKey/op 由调用方判定（工具侧持语义层判断 add/update），此处只负责落库与校验
     *
     * @return 提案 id
     */
    public int saveOne(String agentCode, String batchNo, String question, String sqJson,
                       String assetType, String itemKey, String op, String contentJson, String reason) {
        InsightAgentProposal p = new InsightAgentProposal();
        p.setAgentCode(agentCode);
        p.setBatchNo(batchNo);
        p.setQuestionText(question);
        p.setSemanticQuery(sqJson);
        p.setAssetType(assetType);
        p.setItemKey(itemKey);
        p.setOp(op);
        p.setContent(contentJson);
        p.setReason(reason == null ? "" : reason);
        p.setStatus("0");
        proposalDaoService.insert(p);
        return p.getId() == null ? 0 : p.getId();
    }

    /** 审批列表（状态可选） */
    public List<InsightAgentProposal> listProposals(String agentCode, String status) {
        if (FuncUtil.isEmpty(agentCode)) {
            throw new NoticeException("agentCode 不能为空");
        }
        return proposalDaoService.listByAgent(agentCode, status);
    }

    /** 各 Agent 待审提案数（管理页行内徽标 + 未处理提示） */
    public Map<String, Long> pendingCounts() {
        return proposalDaoService.pendingCounts();
    }

    /** 驳回：仅待审提案可驳回 */
    public int reject(List<Integer> ids) {
        return updateStatus(ids, "2", "驳回");
    }

    /**
     * 合并：待审提案按 item_key upsert 进对应 Agent 的草稿资产（saveAssetDraft），
     * 合并后仍为草稿态，需管理员发布+刷新缓存后运行期生效
     */
    public int merge(List<Integer> ids) {
        List<InsightAgentProposal> proposals = loadPending(ids, "合并");
        String agentCode = proposals.get(0).getAgentCode();
        // 按资产类型分组 upsert 进草稿
        Map<String, List<InsightAgentProposal>> byType = new LinkedHashMap<>();
        for (InsightAgentProposal p : proposals) {
            byType.computeIfAbsent(p.getAssetType(), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<String, List<InsightAgentProposal>> entry : byType.entrySet()) {
            String mergedContent = applyToDraft(agentCode, entry.getKey(), entry.getValue());
            smartAgentMetaService.saveAssetDraft(agentCode, entry.getKey(), mergedContent);
        }
        for (InsightAgentProposal p : proposals) {
            p.setStatus("1");
            proposalDaoService.updateById(p);
        }
        log.info("Agent '{}' 合并 {} 项变更建议进草稿（{}）", agentCode, proposals.size(),
                String.join(",", byType.keySet()));
        return proposals.size();
    }

    // ────────────────────────── 内部实现 ──────────────────────────

    private void insertProposal(String agentCode, String batchNo, String question, String sqJson,
                                String assetType, String itemKey, String op, JsonNode content,
                                JsonNode reasons) {
        InsightAgentProposal p = new InsightAgentProposal();
        p.setAgentCode(agentCode);
        p.setBatchNo(batchNo);
        p.setQuestionText(question);
        p.setSemanticQuery(sqJson);
        p.setAssetType(assetType);
        p.setItemKey(itemKey);
        p.setOp(op);
        p.setContent(writeJson(content));
        p.setReason(reasons.path(itemKey).asText(""));
        p.setStatus("0");
        proposalDaoService.insert(p);
    }

    /** 读提案草稿基线（DB 记录内容 → 已发布缓存 → 缺省空结构），逐项 upsert 后返回新 JSON */
    private String applyToDraft(String agentCode, String assetType, List<InsightAgentProposal> items) {
        String baseContent = null;
        InsightAgentAsset rec = smartAgentMetaService.getAsset(agentCode, assetType);
        if (rec != null && FuncUtil.isNotEmpty(rec.getContent())) {
            baseContent = rec.getContent();
        } else {
            String pub = agentAssetCacheService.assetsFor(agentCode).get(assetType + ".json");
            if (pub != null) {
                baseContent = pub;
            }
        }
        try {
            if ("value-domains".equals(assetType)) {
                ObjectNode root = readTreeOr(baseContent, om.createObjectNode());
                if (!root.isObject()) {
                    root = om.createObjectNode();
                }
                ObjectNode domains = root.has("domains") && root.get("domains").isObject()
                        ? (ObjectNode) root.get("domains") : root.putObject("domains");
                for (InsightAgentProposal p : items) {
                    domains.set(p.getItemKey(), om.readTree(p.getContent()));
                }
                return om.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            }
            if ("concepts".equals(assetType) || "sensitive-fields".equals(assetType)) {
                String field = "concepts".equals(assetType) ? "concepts" : "fields";
                String defaultJson = "{\"schema_version\":\"1.0\",\"" + field + "\":[]}";
                ObjectNode root = readTreeOr(baseContent, readTreeOr(defaultJson, om.createObjectNode()));
                ArrayNode arr = root.has(field) && root.get(field).isArray()
                        ? (ArrayNode) root.get(field) : root.putArray(field);
                for (InsightAgentProposal p : items) {
                    upsertByName(arr, om.readTree(p.getContent()), p);
                }
                return om.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            }
            // metrics / dimensions / relations：根即数组
            ArrayNode arr = readTreeOr(baseContent, om.createArrayNode());
            for (InsightAgentProposal p : items) {
                upsertByName(arr, om.readTree(p.getContent()), p);
            }
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(arr);
        } catch (NoticeException e) {
            throw e;
        } catch (Exception e) {
            throw new NoticeException("资产 '" + assetType + "' 合并失败: " + e.getMessage());
        }
    }

    /** 按 item_key 在数组中定位：命中替换、未命中追加（sensitive-fields 按 entity.field 匹配） */
    private void upsertByName(ArrayNode arr, JsonNode item, InsightAgentProposal p) {
        String keyField = "sensitive-fields".equals(p.getAssetType()) ? "field" : "name";
        for (int i = 0; i < arr.size(); i++) {
            JsonNode existed = arr.get(i);
            boolean hit = "sensitive-fields".equals(p.getAssetType())
                    ? (existed.path("entity").asText("") + "." + existed.path("field").asText(""))
                            .equals(p.getItemKey())
                    : p.getItemKey().equals(existed.path(keyField).asText(""));
            if (hit) {
                arr.set(i, item);
                return;
            }
        }
        arr.add(item);
    }

    /** 加载待审提案（状态校验 + 同 Agent 校验） */
    private List<InsightAgentProposal> loadPending(List<Integer> ids, String action) {
        if (ids == null || ids.isEmpty()) {
            throw new NoticeException("未选择任何建议");
        }
        List<InsightAgentProposal> proposals = new ArrayList<>();
        for (Integer id : ids) {
            InsightAgentProposal p = proposalDaoService.selectById(id);
            if (p == null) {
                throw new NoticeException("建议 #" + id + " 不存在");
            }
            if (!"0".equals(p.getStatus())) {
                throw new NoticeException("建议 #" + id + " 已处理，不能" + action);
            }
            proposals.add(p);
        }
        long agents = proposals.stream().map(InsightAgentProposal::getAgentCode).distinct().count();
        if (agents > 1) {
            throw new NoticeException("一次只能" + action + "同一 Agent 的建议");
        }
        return proposals;
    }

    private int updateStatus(List<Integer> ids, String newStatus, String action) {
        List<InsightAgentProposal> proposals = loadPending(ids, action);
        for (InsightAgentProposal p : proposals) {
            p.setStatus(newStatus);
            proposalDaoService.updateById(p);
        }
        return proposals.size();
    }

    // ────────────────────────── 码值域归一（与维护链同口径副本） ──────────────────────────

    /** 码值域 map 逐项归一（缺省返回原节点） */
    private JsonNode normalizeDomains(JsonNode domains) {
        if (domains == null || !domains.isObject()) {
            return domains == null ? om.createObjectNode() : domains;
        }
        ObjectNode res = om.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> it = domains.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            res.set(e.getKey(), normalizeDomain(e.getValue()));
        }
        return res;
    }

    /** LLM 常把码值域写成 "code:label" 字符串数组（目录简化格式）：归一为 ValueDomainDef 对象结构 */
    private JsonNode normalizeDomain(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return node;
        }
        if (node.isObject()) {
            JsonNode vals = node.get("values");
            if (vals != null && vals.isArray()) {
                ArrayNode nv = om.createArrayNode();
                for (JsonNode v : vals) {
                    nv.add(normalizeDomainValue(v));
                }
                ((ObjectNode) node).set("values", nv);
            }
            return node;
        }
        if (node.isArray()) {
            ObjectNode obj = om.createObjectNode();
            obj.put("stored_as", "code");
            ArrayNode vals = obj.putArray("values");
            for (JsonNode v : node) {
                vals.add(normalizeDomainValue(v));
            }
            return obj;
        }
        return node;
    }

    /** 单个码值归一："code:label" 字符串 → {code,label} 对象 */
    private JsonNode normalizeDomainValue(JsonNode v) {
        if (v.isTextual()) {
            String s = v.asText();
            ObjectNode o = om.createObjectNode();
            int idx = s.indexOf(':');
            if (idx > 0) {
                o.put("code", s.substring(0, idx).trim());
                o.put("label", s.substring(idx + 1).trim());
            } else {
                o.put("code", s.trim());
                o.put("label", s.trim());
            }
            return o;
        }
        return v;
    }

    // ────────────────────────── 小工具 ──────────────────────────

    private String writeJson(Object node) {
        try {
            return om.writeValueAsString(node);
        } catch (Exception e) {
            throw new NoticeException("JSON 序列化失败: " + e.getMessage());
        }
    }

    /** JSON 解析容错：空/非法/节点类型与兜底不匹配 → 兜底节点 */
    private <T extends JsonNode> T readTreeOr(String content, T fallback) {
        if (FuncUtil.isEmpty(content)) {
            return fallback;
        }
        try {
            JsonNode node = om.readTree(content);
            if (fallback != null && node.getNodeType() != fallback.getNodeType()) {
                return fallback;
            }
            @SuppressWarnings("unchecked")
            T typed = (T) node;
            return typed;
        } catch (Exception e) {
            return fallback;
        }
    }
}
