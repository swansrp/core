package com.bidr.insight.smartquery.meta;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.repository.InsightAgentAssetService;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Title: CertifiedDraftMerger
 * Description: 资产认证合并（从 SmartAgentMetaService 拆出）：类型差异仅集中在此两处——
 * LLM 保存路径的认证合并（concepts 带 hierarchy 壳、metrics 按表替换、其余数组直并）
 * 与手动保存路径的认证默认化（仅语义三类自动盖章、concepts 带壳，其余数组）。
 * 不做继承体系：发布/校验/草稿读写对八类资产全同构，差异面只有这两个操作点
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertifiedDraftMerger {

    /** 支持认证的资产：骨架三类（重建时已认证项保留）+ 人工三类（LLM 重生时已认证项保留）；
     *  敏感字段纯人工声明不参与自动重建/LLM 生成，无需认证标记 */
    public static final Set<String> CERTIFIABLE_TYPES = new HashSet<>(Arrays.asList(
            "entities", "dimensions", "value-domains", "metrics", "relations", "concepts"));

    /** 手动保存自动盖章的资产：仅语义三类（LLM 重生保护依赖认证标记，手动编辑即重新认证）；
     *  骨架三类（实体/维度/码值域）不自动盖章——人工填的不默认算认证，
     *  须页面逐条显式点认证，认证的才进模板（沉淀才有意义） */
    public static final Set<String> AUTOSTAMP_TYPES = new HashSet<>(Arrays.asList(
            "metrics", "relations", "concepts"));

    private final InsightAgentAssetService insightAgentAssetService;

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * LLM 保存路径认证合并：已认证现存项原样保留（重生不删），未认证现存项由本次 LLM 提交清空；
     * 来项统一盖 certified=false，与已认证现存项同名（name）者丢弃（认证优先）。
     * metrics 按表合并（本次提交出现的表整体替换、表内认证项例外）；relations/concepts 全量覆盖语义。
     * 返回合并后根节点（metrics/relations 为数组、concepts 为 {concepts:[...]} 对象）；
     * 类型不支持认证或形状不符返回 null，调用方回落原覆盖
     */
    public JsonNode mergeLlmDraftWithCertified(String agentCode, String assetType, JsonNode incoming) {
        if (!CERTIFIABLE_TYPES.contains(assetType) || incoming == null) {
            return null;
        }
        boolean concepts = "concepts".equals(assetType);
        JsonNode inItems = concepts ? incoming.path("concepts") : incoming;
        if (!inItems.isArray()) {
            return null;
        }
        ArrayNode inArr = (ArrayNode) inItems;
        // 现存草稿条目（concepts 解外层）；解析失败回落覆盖
        ArrayNode oldArr = null;
        // 现存 concepts 的 hierarchy（来项未带分组时保留：骨架启发式草稿/人工成果不被 LLM 空数组冲掉）
        JsonNode oldHierarchy = null;
        InsightAgentAsset existed = insightAgentAssetService.selectOne(new QueryWrapper<InsightAgentAsset>()
                .eq("agent_code", agentCode).eq("asset_type", assetType));
        if (existed != null && FuncUtil.isNotEmpty(existed.getContent())) {
            try {
                JsonNode oldRoot = om.readTree(existed.getContent());
                JsonNode oldItems = concepts ? oldRoot.path("concepts") : oldRoot;
                if (oldItems.isArray()) {
                    oldArr = (ArrayNode) oldItems;
                }
                if (concepts) {
                    oldHierarchy = oldRoot.path("hierarchy");
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 现有 '{}' 草稿解析失败，按覆盖执行认证语义: {}", agentCode, assetType, e.getMessage());
            }
        }
        // metrics 按表替换：本次提交出现的表为替换范围（认证项例外），其余表原样保留
        Set<String> touchedTables = null;
        if ("metrics".equals(assetType)) {
            touchedTables = new HashSet<>();
            for (JsonNode m : inArr) {
                touchedTables.add(m.path("source_table").asText("").trim().toLowerCase());
            }
        }
        Set<String> certifiedKeys = new HashSet<>();
        ArrayNode merged = om.createArrayNode();
        if (oldArr != null) {
            for (JsonNode old : oldArr) {
                if (old.path("certified").asBoolean(false)) {
                    certifiedKeys.add(old.path("name").asText(""));
                    merged.add(old);
                } else if (touchedTables != null
                        && !touchedTables.contains(old.path("source_table").asText("").trim().toLowerCase())) {
                    merged.add(old);
                }
            }
        }
        for (JsonNode in : inArr) {
            if (certifiedKeys.contains(in.path("name").asText(""))) {
                continue;
            }
            if (in.isObject()) {
                ObjectNode stamped = in.deepCopy();
                stamped.put("certified", false);
                merged.add(stamped);
            } else {
                merged.add(in);
            }
        }
        if (!concepts) {
            return merged;
        }
        // hierarchy 恒保留现存：分级目录是实体列级归类的派生视图，LLM 来项赋组不落库（单源不旁路）
        return ConceptsSupport.rebuildRoot(oldHierarchy, merged);
    }

    /**
     * 手动保存路径认证默认化：语义三类条目无 certified 字段时补认证（手动编辑=重新认证），
     * 显式声明值原样保留（表单开关/JSON 编辑语义不变）；骨架三类不盖章（认证须逐条显式点击）；
     * 类型不支持自动盖章或解析失败返回原文
     */
    public String stampManualCertified(String assetType, String content) {
        if (!AUTOSTAMP_TYPES.contains(assetType) || FuncUtil.isEmpty(content)) {
            return content;
        }
        try {
            JsonNode root = om.readTree(content);
            JsonNode items = "concepts".equals(assetType) ? root.path("concepts") : root;
            if (!items.isArray()) {
                return content;
            }
            for (JsonNode item : items) {
                if (item.isObject() && !item.has("certified")) {
                    ((ObjectNode) item).put("certified", true);
                }
            }
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return content;
        }
    }
}
