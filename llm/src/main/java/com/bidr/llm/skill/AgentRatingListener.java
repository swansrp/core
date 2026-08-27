package com.bidr.llm.skill;

/**
 * Title: AgentRatingListener
 * Description: 评价动作业务钩子（SPI）——通用评价端点（/web/api/agent/rating/save）落底座前回调，
 * 给业务侧补自己的语义（如 chatbi 把 rating 内嵌对话正文供本人恢复回显）。按 skillCode 分发，
 * 首个 supports 命中的生效；无任何监听器时端点按请求体自组装记录直落底座。
 * <p>
 * 红线：llm 不反向依赖业务结构——钩子只进出底座模型（{@link SkillRatingRecord}）与
 * 业务自定的 ratingId 字符串，业务内部动作（定位消息/改正文等）全部自包含。
 * 钩子异常向外抛出，端点透传失败（正文未内嵌成功则底座不落库，双写一致性由钩子保证）。
 *
 * @author Sharp
 * @since 2026/8/20
 */
public interface AgentRatingListener {

    /**
     * 是否承接该 skill 的评价动作
     */
    boolean supports(String skillCode);

    /**
     * 评价入库前置动作（like/dislike）：业务方完成正文内嵌等动作后返回组装完整的评价记录
     * （question/answer/ext 等；operator 按业务口径填，如对话属主）。返回 null 表示不接管，
     * 端点按请求体自组装。动作失败直接抛异常（端点不再落底座）
     *
     * @param skillCode skill 标识
     * @param ratingId  评价标识（业务自定，如 conversationId:messageId）
     * @param rating    like / dislike（已校验二值）
     * @param operator  当前访问人（OperatorResolver 解析，可能为 null）
     */
    default SkillRatingRecord beforeRate(String skillCode, String ratingId, String rating, String operator) {
        return null;
    }

    /**
     * 取消评价前置动作（底座移除前）：业务方清理正文内嵌等；异常向外抛出则不移除底座记录
     */
    default void beforeRemove(String skillCode, String ratingId, String operator) {
    }

    /**
     * 评价快照保留天数：业务按自身保留策略定（如与对话共用系统参数）；默认 30 天
     */
    default int retentionDays(String skillCode) {
        return 30;
    }
}
