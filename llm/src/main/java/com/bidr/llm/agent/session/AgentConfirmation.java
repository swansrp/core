package com.bidr.llm.agent.session;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Title: AgentConfirmation
 * Description: 自主 agent 会话「未经用户确认的口径」条目（收口闭环载体）：
 * LLM 遇真歧义提问后用户超时/跳过/无交互通道时按自决口径继续，须在 finish 前经业务
 * 登记工具逐条登记（问题 + 采纳口径 + 证据链 + 影响产出）；会话结束后确认页逐条展示，
 * 用户可一键确认（认可该口径）或改口径重算（携新口径触发业务重生成），
 * 未收口的自决口径不再只藏在总结文本里失联即丢
 *
 * @author Sharp
 * @since 2026/8/25
 */
@Data
@NoArgsConstructor
public class AgentConfirmation implements Serializable {

    /** 待确认（确认页渲染可操作条目） */
    public static final String PENDING = "pending";

    /** 用户已确认（认可 LLM 自决口径） */
    public static final String CONFIRMED = "confirmed";

    /** 用户改口径（note 为新口径说明，业务侧据此重算对应产出） */
    public static final String REVISED = "revised";

    /** 条目编号（会话内 1 起递增，收口端点按 id 匹配） */
    private int id;

    /** 疑点描述（当初问不拢/没问成的决策面，一句话含背景） */
    private String question;

    /** LLM 采纳的口径（自决结论，须可直接落实到产出） */
    private String adopted;

    /** 证据链（采样数据/工具返回等依据，供用户裁决时核对） */
    private String evidence;

    /** 影响的产出（资产项名/查询口径等，改口径重算的定位依据） */
    private String impact;

    /** 状态：pending / confirmed / revised */
    private String status;

    /** 登记时间 */
    private long reportedAt;

    /** 收口时间（pending 时为空） */
    private Long resolvedAt;

    /** 收口说明（confirmed 可空；revised 为用户给出的新口径） */
    private String resolveNote;

    public AgentConfirmation(int id, String question, String adopted, String evidence, String impact) {
        this.id = id;
        this.question = question;
        this.adopted = adopted;
        this.evidence = evidence;
        this.impact = impact;
        this.status = PENDING;
        this.reportedAt = System.currentTimeMillis();
    }
}
