package com.bidr.llm.agent;

/**
 * Title: OperatorProfileResolver
 * Description: 会话发起人人员档案 SPI（可选装配）——llm 基础框架不依赖 authorization 与业务表，
 * 「当前用户叫什么、属于哪个部门」的来源由宿主实现注入（同 {@link OperatorResolver} 的解耦口径）。
 * operator 入参语义随宿主（epc＝账号主键 userId，其它宿主可能是工号/显示名），实现方按自己的
 * 口径解读；查无/未登录/解析失败一律返回 null（消费方回落框架登记原文，不抛异常打断会话）。
 * <p>
 * 消费方＝{@link SessionCurrentUserTool}（{@code current_user} 工具，框架工具面默认并入）；
 * 宿主不实现本 SPI 时工具仍在，只回落「框架登记的发起人原文＋部门未知」——身份不是能力件，
 * 不参与 {@code llm.agent.file-tools.enabled} 总开关。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public interface OperatorProfileResolver {

    /**
     * 解析发起人人员档案。
     *
     * @param operator 会话发起人（{@code AgentSessionState#getOperator()} 原文，语义随宿主）
     * @return 档案（姓名/部门可为 null，消费方回落）；查无或解析失败返回 null
     */
    OperatorProfile profileOf(String operator);
}
