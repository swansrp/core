package com.bidr.llm.team;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: TeamRunResult
 * Description: 团队运行结果（T4.2 团队编排门面）——编排台 run 端点的返回体：
 * 每成员一步（输入/输出/角色/耗时）+ 最终结论 + 失败原因（error 非空即中途失败，
 * steps 保留失败前的完整现场供排查）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Data
public class TeamRunResult {

    /**
     * 团队标识
     */
    private String teamKey;

    /**
     * 团队显示名
     */
    private String teamName;

    /**
     * 编排模式（枚举名：PIPELINE/ROUTER/DEBATE）
     */
    private String mode;

    /**
     * 最终结论（PIPELINE=末位成员输出；ROUTER=主控路由说明+派发结果汇集；DEBATE=主持人裁决；
     * 中途失败时为 null）
     */
    private String finalOutput;

    /**
     * 失败原因（null=成功）
     */
    private String error;

    /**
     * 总耗时（毫秒）
     */
    private long costMs;

    /**
     * 每成员一步（按执行顺序；含失败步）
     */
    private List<MemberStep> steps = new ArrayList<>();

    /**
     * 单成员执行步：谁、以什么输入、产出什么（或为何失败）
     */
    @Data
    public static class MemberStep {

        /**
         * 成员标识
         */
        private String memberKey;

        /**
         * 成员显示名
         */
        private String memberName;

        /**
         * 步骤角色：member=普通成员 / router=主控 / moderator=主持人
         */
        private String role;

        /**
         * 该成员收到的输入（移交链/派发任务/意见汇集文）
         */
        private String input;

        /**
         * 该成员产出（失败时为 null）
         */
        private String output;

        /**
         * 该成员失败原因（成功时为 null）
         */
        private String error;

        /**
         * 该步耗时（毫秒）
         */
        private long costMs;
    }
}
