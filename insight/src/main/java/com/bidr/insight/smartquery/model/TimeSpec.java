package com.bidr.insight.smartquery.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: TimeSpec
 * Description: semantic_query 时间窗口协议（time）：preset 预设模板（固定/参数化）
 * 或 between 明确区间，field 指定口径时间轴（SKILL.md §6.4）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TimeSpec {

    /** 时间预设：today/last_n_days 等 */
    private String preset;

    /** 参数化预设的数量 n（解析期做正整数类型校验，非法时置 null 并由解析器报错） */
    private Integer n;

    /** 口径时间轴字段 */
    private String field;

    /** 明确区间 [起始日期, 结束日期]，YYYY-MM-DD */
    private List<Object> between;
}
