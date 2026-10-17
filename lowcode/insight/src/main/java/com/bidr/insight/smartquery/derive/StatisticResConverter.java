package com.bidr.insight.smartquery.derive;

import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: StatisticResConverter
 * Description: statistic payload（List&lt;Map&gt;，StatisticPayloadAdapter 产物）→
 * List&lt;StatisticRes&gt; 强类型转换，与现有 statistic 接口逐字段同构。
 * statistic 走 StatisticRes 构造：null → BigDecimal.ZERO、空 label → "未知"
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Component
public class StatisticResConverter {

    public List<StatisticRes> convert(List<Map<String, Object>> payload) {
        List<StatisticRes> list = new ArrayList<>();
        if (payload == null) {
            return list;
        }
        for (Map<String, Object> entry : payload) {
            list.add(toRes(entry));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private StatisticRes toRes(Map<String, Object> entry) {
        StatisticRes res = new StatisticRes(
                asString(entry.get("metricColumn")),
                asString(entry.get("metric")),
                asString(entry.get("metricLabel")),
                entry.get("statistic"));
        Object children = entry.get("children");
        if (children instanceof List) {
            List<StatisticRes> cs = new ArrayList<>();
            for (Object c : (List<Object>) children) {
                if (c instanceof Map) {
                    cs.add(toRes((Map<String, Object>) c));
                }
            }
            res.setChildren(cs);
        }
        return res;
    }

    private String asString(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
