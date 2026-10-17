package com.bidr.insight.smartquery.meta;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.dao.repository.InsightAgentAssetService;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Title: SkeletonMergeSupport
 * Description: 骨架重建合并（从 SmartAgentMetaService 拆出）：已确认实体的字段级携入、
 * 数组/码值域形态的认证合并、合并结果的敏感清理——骨架前置与生成落盘共用的
 * 「人工成果不丢」口径，三类骨架资产（entities/dimensions/value-domains）重建时统一走这里
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkeletonMergeSupport {

    private final InsightAgentAssetService insightAgentAssetService;

    private final ObjectMapper om = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 字段级合并（旧数据兼容核心）：现存草稿中已确认实体的同名重建项，把人工结论携进来——
     *  表级键/分区/确认态直接沿用旧值；列级人工编辑过（edited）的列沿用旧 role/unit/granularity；
     *  未编辑列取新骨架值但保留旧 value_domain（防人工/采样登记的值域丢失）；
     *  旧稿列已不存在于新骨架时自然丢弃（列被删不残留）。未确认实体不携入（重建全量覆盖，旧行为） */
    public void carryConfirmedFromOld(String agentCode, List<EntityDef> rebuilt) {
        InsightAgentAsset old = insightAgentAssetService.selectOne(new QueryWrapper<InsightAgentAsset>()
                .eq("agent_code", agentCode).eq("asset_type", "entities"));
        if (old == null || FuncUtil.isEmpty(old.getContent())) {
            return;
        }
        EntityDef[] oldArr;
        try {
            oldArr = om.readValue(old.getContent(), EntityDef[].class);
        } catch (Exception e) {
            log.warn("Agent '{}' 现有 entities 草稿解析失败，字段级合并跳过: {}", agentCode, e.getMessage());
            return;
        }
        carryConfirmedFields(rebuilt, oldArr);
    }

    /** 字段级合并（静态可测）：现存草稿中已确认实体的同名重建项，把人工结论携进来——
     *  表级键/分区/确认态直接沿用旧值；列级人工编辑过（edited）的列沿用旧 role/unit/granularity/dim_group/multi_value；
     *  未编辑列取新骨架值但保留旧 value_domain/dim_group/multi_value（防人工登记的成果丢失）；
     *  物理表新增列随新骨架进入，旧稿列已不存在时自然丢弃（删列不残留）；
     *  未确认实体不携入（重建全量覆盖） */
    public static void carryConfirmedFields(List<EntityDef> rebuilt, EntityDef[] oldArr) {
        Map<String, EntityDef> oldByName = Arrays.stream(oldArr)
                .filter(e -> e.getName() != null)
                .collect(Collectors.toMap(EntityDef::getName, Function.identity(), (a, b) -> a));
        for (EntityDef ne : rebuilt) {
            EntityDef oe = oldByName.get(ne.getName());
            if (oe == null || !Boolean.TRUE.equals(oe.getConfirmed())) {
                continue;
            }
            ne.setConfirmed(true);
            // 认证经验随旧稿/模板携入（认证过的沿用免再认证，沉淀才有意义）
            if (Boolean.TRUE.equals(oe.getCertified())) {
                ne.setCertified(true);
            }
            if (oe.getPrimaryKey() != null && !oe.getPrimaryKey().isEmpty()) {
                ne.setPrimaryKey(oe.getPrimaryKey());
            }
            if (FuncUtil.isNotEmpty(oe.getPartitionColumn())) {
                ne.setPartitionColumn(oe.getPartitionColumn());
            }
            if (oe.getFields() == null || ne.getFields() == null) {
                continue;
            }
            Map<String, EntityDef.EntityFieldDef> oldFields = oe.getFields().stream()
                    .filter(f -> f.getName() != null)
                    .collect(Collectors.toMap(EntityDef.EntityFieldDef::getName, Function.identity(), (a, b) -> a));
            for (EntityDef.EntityFieldDef nf : ne.getFields()) {
                EntityDef.EntityFieldDef of = oldFields.get(nf.getName());
                if (of == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(of.getEdited())) {
                    nf.setRole(of.getRole());
                    nf.setUnit(of.getUnit());
                    nf.setGranularity(of.getGranularity());
                    nf.setDimGroup(of.getDimGroup());
                    nf.setMultiValue(of.getMultiValue());
                    nf.setDisabled(of.getDisabled());
                    // 显示名人工修正属结论（维度显示名派生单源），重建不得回落列备注；空值不携入（清空回默认）
                    if (FuncUtil.isNotEmpty(of.getDisplayName())) {
                        nf.setDisplayName(of.getDisplayName());
                    }
                    nf.setEdited(true);
                }
                // 单位裁决经验随旧稿携入（裁决即经验：同表重建/模板套用不再重复提疑点）
                if (Boolean.TRUE.equals(of.getUnitVerified())) {
                    nf.setUnitVerified(true);
                }
                if (nf.getValueDomain() == null && of.getValueDomain() != null) {
                    nf.setValueDomain(of.getValueDomain());
                }
                if (nf.getDimGroup() == null && of.getDimGroup() != null) {
                    nf.setDimGroup(of.getDimGroup());
                }
                // 多值列标记属人工结论：骨架启发式不产该值，旧稿有值即携入（防勾选丢失）
                if (of.getMultiValue() != null) {
                    nf.setMultiValue(of.getMultiValue());
                }
                // 禁用列标记同属人工结论（同 multiValue 口径）：旧稿有值即携入，防重建丢失
                if (of.getDisabled() != null) {
                    nf.setDisabled(of.getDisabled());
                }
            }
        }
    }

    /** 骨架合并（数组形态）：现存草稿中已认证项或本次重建未涉及项原样保留（人工成果不丢），
     *  其余同名项由本次重建替换；旧稿解析失败按全量重建回落。
     *  注意：已确认实体的保护不走这里（否则整体保留会让字段级合并失效），
     *  由 carryConfirmedFromOld 在重建项上字段级携入人工结论 */
    public <T> List<T> mergeByName(String agentCode, String assetType, List<T> rebuilt,
                                   Class<T[]> arrType, Function<T, String> nameOf, Function<T, Boolean> certOf) {
        Set<String> rebuiltNames = rebuilt.stream().map(nameOf).collect(Collectors.toSet());
        LinkedHashMap<String, T> byName = new LinkedHashMap<>();
        InsightAgentAsset old = insightAgentAssetService.selectOne(new QueryWrapper<InsightAgentAsset>()
                .eq("agent_code", agentCode).eq("asset_type", assetType));
        if (old != null && FuncUtil.isNotEmpty(old.getContent())) {
            try {
                for (T item : om.readValue(old.getContent(), arrType)) {
                    if (Boolean.TRUE.equals(certOf.apply(item)) || !rebuiltNames.contains(nameOf.apply(item))) {
                        byName.put(nameOf.apply(item), item);
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 现有 '{}' 草稿解析失败，按全量重建执行: {}", agentCode, assetType, e.getMessage());
            }
        }
        for (T item : rebuilt) {
            byName.putIfAbsent(nameOf.apply(item), item);
        }
        return new ArrayList<>(byName.values());
    }

    /** 骨架合并（码值域 map 形态）：口径同 mergeByName（认证域与本次重建未涉及域保留） */
    public Map<String, ValueDomainDef> mergeDomains(String agentCode, Map<String, ValueDomainDef> rebuilt) {
        LinkedHashMap<String, ValueDomainDef> merged = new LinkedHashMap<>();
        Map<String, ValueDomainDef> oldAll = new LinkedHashMap<>();
        InsightAgentAsset old = insightAgentAssetService.selectOne(new QueryWrapper<InsightAgentAsset>()
                .eq("agent_code", agentCode).eq("asset_type", "value-domains"));
        if (old != null && FuncUtil.isNotEmpty(old.getContent())) {
            try {
                JsonNode doms = om.readTree(old.getContent()).path("domains");
                Iterator<Map.Entry<String, JsonNode>> it = doms.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    ValueDomainDef d = om.treeToValue(entry.getValue(), ValueDomainDef.class);
                    oldAll.put(entry.getKey(), d);
                    if (Boolean.TRUE.equals(d.getCertified()) || !rebuilt.containsKey(entry.getKey())) {
                        merged.put(entry.getKey(), d);
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 现有 'value-domains' 草稿解析失败，按全量重建执行: {}", agentCode, e.getMessage());
            }
        }
        for (Map.Entry<String, ValueDomainDef> e : rebuilt.entrySet()) {
            // 重建域替换旧域时携入人工裁决的忽略码清单（裁决即经验，防重建后同码疑点反复重提）；
            // 取全量旧域（未认证旧域被替换不进 merged，不能从 merged 取）
            ValueDomainDef oldDomain = oldAll.get(e.getKey());
            if (oldDomain != null && FuncUtil.isNotEmpty(oldDomain.getIgnoredCodes())) {
                e.getValue().setIgnoredCodes(oldDomain.getIgnoredCodes());
            }
            merged.putIfAbsent(e.getKey(), e.getValue());
        }
        return merged;
    }

    /** 合并结果敏感清理（口径同生成服务侧）：剔除覆盖敏感列的码值域，
     *  同步解除实体字段上的 value_domain 引用（指向已删域，否则发布校验报码值域缺失）；
     *  覆盖保留下来的旧认证域，避免敏感取值随草稿发布外泄 */
    public void purgeSensitive(List<EntityDef> entities, Map<String, ValueDomainDef> domains,
                               Set<String> sensitiveKeys) {
        if (sensitiveKeys == null || sensitiveKeys.isEmpty()) {
            return;
        }
        int removed = 0;
        Iterator<Map.Entry<String, ValueDomainDef>> it = domains.entrySet().iterator();
        while (it.hasNext()) {
            ValueDomainDef d = it.next().getValue();
            if (sensitiveKeys.contains((d.getEntity() + "." + d.getField()).toLowerCase())) {
                it.remove();
                removed++;
            }
        }
        for (EntityDef e : entities) {
            if (e.getFields() == null) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (f.getValueDomain() != null
                        && sensitiveKeys.contains((e.getName() + "." + f.getName()).toLowerCase())) {
                    f.setValueDomain(null);
                }
            }
        }
        if (removed > 0) {
            log.info("落草稿合并后清理覆盖敏感列的码值域 {} 个", removed);
        }
    }
}
