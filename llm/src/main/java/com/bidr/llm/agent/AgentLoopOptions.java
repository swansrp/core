package com.bidr.llm.agent;

import lombok.Data;

import java.util.Collections;
import java.util.Set;

/**
 * Title: AgentLoopOptions
 * Description: 工具循环运行参数（任务级，随会话传入）
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Data
public class AgentLoopOptions {

    /** 工具循环轮次上限：自管循环突破 AiServices 0.33 内置「连续 10 轮工具调用」硬上限；
     *  触顶后强制收口（追加不带工具定义的收口指令）而非报错 */
    private int maxRounds = 30;

    /** 上下文滑动窗口（条数）：防多轮探索后上下文爆炸，中段只保留最近 N 条消息 */
    private int memoryWindow = 60;

    /** 钉住工具名集合：这些工具的「AI 调用↔结果」消息对永不被窗口驱逐
     *  （如 ask_user 的用户已确认口径，掉窗会导致后段产出违背用户决策） */
    private Set<String> pinnedTools = Collections.emptySet();
    
    /** 会话总时间预算（秒）：0 为不限；每轮循环头检查，超预算即走触顶收口路径强制产出结论，
     *  杜绝多轮慢模型串行叠加导致单会话长跑无结论（维护问数等编排按链路分级配置） */
    private int budgetSeconds = 0;

    /** 同参缓存工具名集合：同一会话内同名+同参调用直接返回缓存结果（零执行成本），
     *  消除模型重复拉取的重执行开销；仅应登记只读探索类工具（提案/写库等副作用工具严禁登记） */
    private Set<String> cachedTools = Collections.emptySet();

    /** 预算豁免工具名集合：这些工具的单次执行耗时不计入会话总时间预算——
     *  交互等待类工具（如 ask_user）阻塞在等用户作答上，占的是人机交互时长而非模型探索时长，
     *  不豁免会导致“等用户 10 分钟归来即触发超预算强制收口”，计划后半段被跳过 */
    private Set<String> budgetExemptTools = Collections.emptySet();

    /** 轮次豁免工具名集合：整轮只调这些工具时本轮不消耗轮次配额（等效免费轮）——
     *  纯状态仪式类（如 submit_plan/start_plan_item/done_plan_item）不产生新信息，
     *  不豁免会把轮次配额挤占给真探索（实测计划仪式曾吃掉解析链 4/8 轮）；
     *  时间预算不受豁免影响仍兜底（防无限仪式循环）。常量集合见 AgentPlanTools.ROUND_EXEMPT_TOOLS */
    private Set<String> roundExemptTools = Collections.emptySet();

    /** 失败分类熔断器（任务级有状态实例，null=不启用）：工具失败文本按规则集分类计数，
     *  同类累计达阈值时注入一条拉直方向的指令——机制在 AgentFailureBreaker，
     *  分类规则与建议文案由业务注入（问数列校验类/资产生成协议类各配各的） */
    private AgentFailureBreaker failureBreaker;

    public AgentLoopOptions() {
    }

    public AgentLoopOptions(int maxRounds, int memoryWindow) {
        this.maxRounds = maxRounds;
        this.memoryWindow = memoryWindow;
    }

    public AgentLoopOptions(int maxRounds, int memoryWindow, Set<String> pinnedTools) {
        this.maxRounds = maxRounds;
        this.memoryWindow = memoryWindow;
        this.pinnedTools = pinnedTools == null ? Collections.emptySet() : pinnedTools;
    }
}
