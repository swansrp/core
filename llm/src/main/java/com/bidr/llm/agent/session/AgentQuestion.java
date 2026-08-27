package com.bidr.llm.agent.session;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Title: AgentQuestion
 * Description: 自主 agent 会话用户决策问题条目（LLM 经 ask_user 工具在口径分歧时提问、
 * 用户在前端问题卡片选择或输入后作答；状态快照随 sessionStatus 轮询渲染，waiting 条目
 * 渲染为可交互卡片，其余按状态留痕）：id 会话内自 1 递增，作答控制键按 id 匹配防串答
 *
 * @author Sharp
 * @since 2026/8/21
 */
@Data
@NoArgsConstructor
public class AgentQuestion implements Serializable {

    /** 等待用户作答（前端渲染可交互卡片） */
    public static final String WAITING = "waiting";

    /** 用户已作答（answer 为选择或输入文本） */
    public static final String ANSWERED = "answered";

    /** 用户主动跳过，交由 LLM 自行决策 */
    public static final String SKIPPED = "skipped";

    /** 等待超时未作答（LLM 按合理默认继续） */
    public static final String EXPIRED = "expired";

    /** 问题编号（会话内 1 起递增，作答端点与等待侧匹配依据） */
    private int id;

    /** 问题文本（含必要背景与影响说明） */
    private String question;

    /** 候选项清单（来自真实数据，2-6 个；可空=纯开放题，前端始终允许自由输入） */
    private List<String> options;

    /** 状态：waiting / answered / skipped / expired */
    private String status;

    /** 用户作答文本（answered 时有值） */
    private String answer;

    /** 提问时间 */
    private long askedAt;

    /** 作答/跳过/超时时间（waiting 时为空） */
    private Long answeredAt;

    public AgentQuestion(int id, String question, List<String> options) {
        this.id = id;
        this.question = question;
        this.options = options;
        this.status = WAITING;
        this.askedAt = System.currentTimeMillis();
    }
}
