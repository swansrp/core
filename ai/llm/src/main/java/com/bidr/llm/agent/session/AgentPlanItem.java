package com.bidr.llm.agent.session;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Title: AgentPlanItem
 * Description: 自主 agent 会话计划待办条目（LLM 经 submit_plan 工具开局提交清单、
 * start_plan_item 标记执行中、done_plan_item 逐条挑勾；状态快照随 sessionStatus 轮询全量渲染前端清单）：
 * id 自 1 递增供 LLM 引用，status 四态 pending/running/done/stopped（执行中同一时刻至多一条，
 * 新开自动回退旧执行中为 pending；会话终态时 running 由会话层收口置位——FINISHED 补挑勾 done、
 * 其余 stopped，pending 保持原样如实反映未执行部分）
 *
 * @author Sharp
 * @since 2026/8/21
 */
@Data
@NoArgsConstructor
public class AgentPlanItem implements Serializable {

    /** 待办（未挑勾） */
    public static final String PENDING = "pending";

    /** 执行中（start_plan_item 标记；同一时刻至多一条，新开自动回退旧执行中为 pending） */
    public static final String RUNNING = "running";

    /** 已完成（挑勾；会话 FINISHED 收口时 running 条目自动补挑勾） */
    public static final String DONE = "done";

    /** 已停止（会话 FAILED/STOPPED 收口时 running 条目置位：执行被打断、未挑勾） */
    public static final String STOPPED = "stopped";

    /** 条目编号（1 起递增，LLM 挑勾引用依据） */
    private int id;

    /** 待办文本 */
    private String text;

    /** 状态：pending / running / done / stopped */
    private String status;

    /** 完成备注（挑勾时可携成果摘要，如「落库 12 指标」） */
    private String note;

    public AgentPlanItem(int id, String text, String status, String note) {
        this.id = id;
        this.text = text;
        this.status = status;
        this.note = note;
    }
}
