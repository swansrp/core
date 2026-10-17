package com.bidr.llm.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: SkillRatingStatRes
 * Description: skill 评价运营统计响应——筛选条件作用于列表，汇总随筛选联动（运营看"差评集中在哪块"更直观）。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Data
public class SkillRatingStatRes {

    /**
     * 筛选后评价总数
     */
    private int total;

    /**
     * 筛选后点赞数
     */
    private int likeCount;

    /**
     * 筛选后点踩数
     */
    private int dislikeCount;

    /**
     * 评价明细（评价时间新→旧，上限见服务内防御常量）
     */
    private List<SkillRatingRecord> records = new ArrayList<>();
}
