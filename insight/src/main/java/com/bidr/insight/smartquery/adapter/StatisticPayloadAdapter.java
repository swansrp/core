package com.bidr.insight.smartquery.adapter;

import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.GenResult.ColumnInfo;
import com.bidr.insight.smartquery.model.QueryRows;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: StatisticPayloadAdapter
 * Description: SQL 结果 → 前端 statistic payload 映射器（零改动对接 ChartCard）。
 * 产出形状与 advancedStatisticRequest 响应一致：
 * 标准嵌套 [{metricColumn, metric(码值原值, 供穿透), metricLabel(显示名), statistic,
 * children:[{metric(指标显示名), metricLabel, statistic}]}]；
 * 二维交叉 metricLabel 以 "&&" 连接；rankingBar 用扁平形状。
 * metric 存码值、metricLabel 存标签——正好复用值域翻译机制
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
@RequiredArgsConstructor
public class StatisticPayloadAdapter {

    private static final String DIM_SEP = "&&";

    private final SemanticLayerRegistry layers;

    /** 标准嵌套形状（bar/pie/line/二维交叉/metricsPie） */
    public List<Map<String, Object>> toStandardPayload(GenResult gen, QueryRows rows) {
        List<Integer> dimIdx = new ArrayList<>();
        List<Integer> metricIdx = new ArrayList<>();
        for (int i = 0; i < gen.getColumns().size(); i++) {
            ColumnInfo c = gen.getColumns().get(i);
            if ("dimension".equals(c.getKind())) {
                dimIdx.add(i);
            } else if ("metric".equals(c.getKind())) {
                metricIdx.add(i);
            }
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        if (dimIdx.isEmpty()) {
            // metricsPie/纯指标：单条聚合行
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("metricColumn", "");
            entry.put("metric", "");
            entry.put("metricLabel", "合计");
            List<Map<String, Object>> children = new ArrayList<>();
            for (int mi : metricIdx) {
                Object v = rows.getRows().isEmpty() ? null : rows.getRows().get(0).get(mi);
                if (children.isEmpty()) {
                    entry.put("statistic", v);
                }
                children.add(metricChild(gen, mi, v));
            }
            entry.put("children", children);
            payload.add(entry);
            return payload;
        }

        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (List<Object> row : rows.getRows()) {
            StringBuilder rawKey = new StringBuilder();
            StringBuilder labelKey = new StringBuilder();
            for (int k = 0; k < dimIdx.size(); k++) {
                if (k > 0) {
                    rawKey.append(DIM_SEP);
                    labelKey.append(DIM_SEP);
                }
                Object raw = row.get(dimIdx.get(k));
                rawKey.append(raw == null ? "" : String.valueOf(raw));
                String dimName = gen.getColumns().get(dimIdx.get(k)).getAlias();
                String label = layers.current().translateBack(dimName, raw);
                labelKey.append(label != null ? label : (raw == null ? "" : String.valueOf(raw)));
            }
            String key = rawKey.toString();
            Map<String, Object> entry = grouped.get(key);
            if (entry == null) {
                entry = new LinkedHashMap<>();
                entry.put("metricColumn", gen.getColumns().get(dimIdx.get(0)).getAlias());
                entry.put("metric", key);
                entry.put("metricLabel", labelKey.toString());
                entry.put("children", new ArrayList<Map<String, Object>>());
                grouped.put(key, entry);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) entry.get("children");
            for (int mi : metricIdx) {
                Object v = row.get(mi);
                if (children.isEmpty()) {
                    entry.put("statistic", v);
                }
                children.add(metricChild(gen, mi, v));
            }
        }
        payload.addAll(grouped.values());
        return payload;
    }

    /** 扁平形状（rankingBar：normalizeRankingResponse 归一化前的原始形态） */
    public List<Map<String, Object>> toRankingPayload(GenResult gen, QueryRows rows) {
        int dimIdx = -1;
        int metricIdx = -1;
        for (int i = 0; i < gen.getColumns().size(); i++) {
            ColumnInfo c = gen.getColumns().get(i);
            if ("dimension".equals(c.getKind()) && dimIdx < 0) {
                dimIdx = i;
            } else if ("metric".equals(c.getKind()) && metricIdx < 0) {
                metricIdx = i;
            }
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        if (dimIdx < 0 || metricIdx < 0) {
            return payload;
        }
        String dimName = gen.getColumns().get(dimIdx).getAlias();
        for (List<Object> row : rows.getRows()) {
            Object raw = row.get(dimIdx);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("metricColumn", dimName);
            entry.put("metric", raw == null ? "" : String.valueOf(raw));
            String label = layers.current().translateBack(dimName, raw);
            entry.put("metricLabel", label != null ? label : (raw == null ? "" : String.valueOf(raw)));
            entry.put("statistic", row.get(metricIdx));
            payload.add(entry);
        }
        return payload;
    }

    private Map<String, Object> metricChild(GenResult gen, int metricIdx, Object value) {
        String display = gen.getColumns().get(metricIdx).getDisplay();
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("metricColumn", gen.getColumns().get(metricIdx).getAlias());
        child.put("metric", display);
        child.put("metricLabel", display);
        child.put("statistic", value);
        return child;
    }
}
