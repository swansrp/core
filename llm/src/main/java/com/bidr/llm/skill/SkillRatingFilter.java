package com.bidr.llm.skill;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: SkillRatingFilter
 * Description: skill 评价统计筛选条件——通用维度（类型/评价人/时间段/关键词）作用于全部 skill；
 * 业务维度走 extEquals（对 {@link SkillRatingRecord#getExt()} 的键值精确匹配，空值条件跳过）。
 * 筛选作用于明细列表，汇总随筛选联动。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class SkillRatingFilter {

    /**
     * 评价类型：like/dislike（空=不限）
     */
    private String rating;

    /**
     * 评价人（空=不限）
     */
    private String operator;

    /**
     * 评价时间起（毫秒时间戳，空=不限）
     */
    private Long startTime;

    /**
     * 评价时间止（毫秒时间戳，空=不限）
     */
    private Long endTime;

    /**
     * 关键词（提问/回答摘要包含命中，空=不限）
     */
    private String keyword;

    /**
     * 业务维度精确匹配（key=ext 键，value=期望值；空值条件跳过）
     */
    private Map<String, String> extEquals = new LinkedHashMap<>();
}
