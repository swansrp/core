package com.bidr.insight.smartquery.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * Title: OrderByItem
 * Description: semantic_query 排序项（order_by[] / window.order_by[] 共用），
 * field 为指标或维度英文名，direction 仅允许 asc/desc
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class OrderByItem {

    private String field;

    private String direction;

    /** 入口归一化小写：LLM 解析产出常带 SQL 习惯大写（DESC/ASC），
     *  校验器按小写枚举比对，不归一会误判不合法白白触发 LLM 维护链路 */
    public void setDirection(String direction) {
        this.direction = direction == null ? null : direction.trim().toLowerCase();
    }

    /** direction 缺省值（与 Python 引擎一致：order_by 缺省 asc，window.order_by 缺省 desc） */
    public String directionOrDefault(String fallback) {
        return direction == null || direction.isEmpty() ? fallback : direction;
    }
}
