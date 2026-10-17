package com.bidr.llm.agent;

import com.bidr.llm.agent.session.PlanBoard;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Title: AgentPlanTools
 * Description: 通用计划待办工具组（llm 框架，业务零绑定）：submit_plan 开局提交清单、
 * start_plan_item 标记执行中、done_plan_item 逐条挑勾——状态机见 {@link PlanBoard}，
 * 纪律提示词经 {@link #planDiscipline(String)} 拼装（业务只注入自己的覆盖示例）。
 * 会话链传 ctx.planBoard()；无交互载体/非会话链传 null board（工具调用返回 error 文案，
 * 注册不炸）。stopGuard（可空）返回非 null 时拒绝执行（停止请求即时打断）
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class AgentPlanTools {

    /** 轮次豁免工具名集合：本组三工具为纯状态仪式（不产生新信息），整轮只调它们时不消耗
     *  AgentLoopOptions.maxRounds 轮次配额；业务链路注册本组工具时应同步
     *  opt.setRoundExemptTools(AgentPlanTools.ROUND_EXEMPT_TOOLS)，防计划仪式挤占真探索轮次 */
    public static final Set<String> ROUND_EXEMPT_TOOLS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "submit_plan", "start_plan_item", "done_plan_item")));

    private final PlanBoard board;
    /** 停止检查点（可空）：返回非 null 拒绝文本时本组工具拒绝执行 */
    private final Supplier<String> stopGuard;
    /** 过程日志上报（可空） */
    private final Consumer<String> report;

    public AgentPlanTools(PlanBoard board, Supplier<String> stopGuard, Consumer<String> report) {
        this.board = board;
        this.stopGuard = stopGuard;
        this.report = report;
    }

    @Tool("提交本次任务的计划待办清单（强制第一步：在探索/执行之前先调用，提交 3-10 条待办）。"
            + "提交后用户端将实时展示清单，开始做某条前调 start_plan_item 标记执行中，完成后调 done_plan_item 挑勾")
    public String submitPlan(@P("待办纯文本字符串数组（元素即待办文字本身，禁止对象/嵌套 JSON），如 [\"探索 xx 表结构\",\"生成指标\"]") String itemsJson) {
        String stop = stop();
        if (stop != null) {
            return stop;
        }
        if (board == null) {
            return "{\"error\":\"当前链路无计划清单载体，计划工具不可用\"}";
        }
        List<String> items = PlanBoard.parseItems(itemsJson);
        if (items == null || items.isEmpty()) {
            return "拒绝：待办清单为空或不是合法 JSON 数组，请提交 3-10 条待办";
        }
        if (items.size() > PlanBoard.PLAN_MAX_ITEMS) {
            items = new ArrayList<>(items.subList(0, PlanBoard.PLAN_MAX_ITEMS));
        }
        board.submit(items);
        if (report != null) {
            report.accept("工具 submit_plan：提交计划待办 " + items.size() + " 条");
        }
        return "计划已提交，前端将实时展示清单并随完成挑勾。当前计划：\n" + board.planText();
    }

    @Tool("标记一条计划待办为执行中（开始做该条目的工作前立即调用，前端该条目将显示执行中标识；"
            + "同一时刻至多一条执行中，开新的自动回退旧执行中；完成后调 done_plan_item 挑勾）。id 为计划清单中的编号")
    public String startPlanItem(@P("计划待办编号（自 1 起）") int id) {
        String stop = stop();
        if (stop != null) {
            return stop;
        }
        if (board == null) {
            return "{\"error\":\"当前链路无计划清单载体，计划工具不可用\"}";
        }
        boolean ok = board.start(id);
        if (report != null) {
            report.accept(ok ? "计划执行中 #" + id : "计划执行中未命中 #" + id);
        }
        return (ok ? "已标记执行中。" : "编号未命中，未标记。") + board.planBrief();
    }

    @Tool("挑勾一条已完成的计划待办（完成对应工作后立即调用，不要攒到最后）。id 为计划清单中的编号")
    public String donePlanItem(@P("计划待办编号（自 1 起）") int id,
                               @P("完成备注（可空，如：命中 3 个指标）") String note) {
        String stop = stop();
        if (stop != null) {
            return stop;
        }
        if (board == null) {
            return "{\"error\":\"当前链路无计划清单载体，计划工具不可用\"}";
        }
        boolean ok = board.done(id, note);
        if (report != null) {
            report.accept(ok ? "计划挑勾 #" + id : "计划挑勾未命中 #" + id);
        }
        return (ok ? "已挑勾。" : "编号未命中，未挑勾。") + board.planBrief();
    }

    /** 计划纪律提示词片段（业务提示词拼接用）：入参为该业务的待办覆盖示例
     *  （如「搜索资产并确认口径、组装语义查询、校验并作答」），纪律主体框架级只写一遍 */
    public static String planDiscipline(String coverageExamples) {
        return "\n\n【计划待办】开始时必须先调 submit_plan 提交计划待办清单（3-8 条，覆盖：" + coverageExamples + "），"
                + "开始做某条前调 start_plan_item 标记执行中（计划工具可与同轮其他工具一起批量调用，"
                + "不要为标记进度单独占一轮），每完成一条立即调 done_plan_item 挑勾，不要攒到最后。";
    }

    private String stop() {
        return stopGuard == null ? null : stopGuard.get();
    }
}
