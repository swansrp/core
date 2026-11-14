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
     *  触顶后强制收口（追加收口指令＋按 {@link #closeoutKeepTools} 收窄后的工具面）而非报错 */
    private int maxRounds = 30;

    /** 触顶/超预算收口保留工具名集合：收口路径默认剥光工具（仅 askUser 例外口子），但个别链路的
     *  收尾动作本身就是工具（监理日志 save_supervision_log→finish，先存后收）——不保留则触顶产出
     *  必然只留会话文本不落库（2026-11-14 真机实锤：素材缺口会话 12 轮触顶，落库与 finish 双缺，
     *  agent 自述「尚未进入数据库」）。登记进本集合的工具在收口轮仍带规格，收口指令同步改为
     *  「先完成收尾工具再输出最终结论」；收口轮配额见 {@link #closeoutMaxRounds}。
     *  只应登记收尾必需的落库/终结类工具；探索类工具严禁登记（收口的意义就是终结探索） */
    private Set<String> closeoutKeepTools = Collections.emptySet();

    /** 收口轮工具循环上限：收口段允许模型连续使用保留工具（save→finish 天然两轮）的执行批数上限，
     *  到顶执行完最后一批工具请求后无工具面强制直出（防收口段长跑）；业务终止信号（finish 确认）
     *  在收口轮命中时立即短路返回终文本，不消耗后续轮。默认 3＝save→finish→askUser 例外三连够用 */
    private int closeoutMaxRounds = 3;

    /** 轮次触顶续期闸（<b>默认开</b>，2026-11-14 用户二次拍板「默认开吧」）：maxRounds 用尽时不直接
     *  收口，而是由引擎合成一次 askUser 问用户「是否追加轮数继续」——人在回路决定成本，把硬停止
     *  变人闸。默认开的安全性：链路未注册 askUser 工具时闸自动跳过（等效关闭，轻链路/无交互通道
     *  零影响）；有 askUser 的链触顶从「静默截断」变「先问人」，用户不在场（KEEP_RUNNING 后台）时
     *  超时/跳过即回落收口，maxRounds 的无人值守成本兜底语义不变。仅作用于<b>轮次</b>触顶；
     *  budgetSeconds 超限直接收口（续轮救不了时间预算）。收尾工具不丢库的完整形态＝本闸（默认开）
     *  ＋{@link #closeoutKeepTools}（业务按需登记） */
    private boolean askBeforeCloseout = true;

    /** 续期一次授予的轮数（{@link #askBeforeCloseout} 开且用户选继续时生效）；默认 12 与监理链上限同源 */
    private int roundExtension = 12;

    /** 续期次数上限：每次续期都须用户真人确认，封顶后触顶直接走收口（防无人值守无限续命） */
    private int maxRoundExtensions = 1;

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

    /** 上下文 token 预算（估算 token，0=关闭仅按条数裁窗即既有行为，I9）：正值（建议 24000）为进入
     *  模型的消息估算 token 上限，与条数窗口取更严者（I11）。0 时回落 sys_config
     *  {@code AGENT_CONTEXT_TOKEN_BUDGET}（见 AgentContextBudget，Param 亦默认 0=全关）。
     *  <p>为什么不复用 memoryWindow 承载 token 量纲：memoryWindow 被 10+ 处生产调用点以位置参数传入，
     *  改其量纲会静默改变所有既有语义（20 突然变成 20 token），是最典型的「编译过得了、行为全变了」
     *  事故形态；新增字段 + 默认关才满足 I9 关闭态零行为变化 */
    private int contextTokenBudget = 0;

    /** 工具结果入场卸载阈值（字符，0=关闭不卸载，I9）：超过该字符数的工具结果入场时替换为
     *  预览+句柄指针文本，原文进框架自带的 run 作用域回捞缓冲（会话链另有事件流全文副本），
     *  模型经内建 recallToolResult 工具按句柄回捞（机制见 ToolResultOffloader/AgentToolRecall/
     *  RunScopedRecallBuffer，不变式 I4/I5/I6/I7/I10/I16）。
     *  0 时回落 sys_config {@code AGENT_TOOL_RESULT_OFFLOAD_CHARS}；A10 起任何链路零改动即生效
     *  （旧"须 listener 支持回捞"前置已作废，通道位只决定能否跨 run 回捞） */
    private int toolResultOffloadChars = 0;

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
