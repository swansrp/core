package com.bidr.llm.agent;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Title: OperatorProfile
 * Description: 会话发起人的人员档案（姓名＋所属部门）——{@link OperatorProfileResolver} 的返回值。
 * 框架只搬运不解释：数据来源、多部门语义（拼接还是取舍）由宿主实现决定；任一字段可为
 * null/空串，消费方（{@link SessionCurrentUserTool}）按缺失回落，不要求实现方兜底
 *
 * @author Sharp
 * @since 2026/11/18
 */
@Data
@AllArgsConstructor
public class OperatorProfile {

    /** 姓名（展示口径；null/空＝实现方未查到，消费方回落框架登记原文） */
    private String name;

    /** 所属部门（多部门时由实现方自行拼接；null/空＝未知） */
    private String department;
}
