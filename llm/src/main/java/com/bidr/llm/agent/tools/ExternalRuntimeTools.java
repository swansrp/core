package com.bidr.llm.agent.tools;

import com.bidr.llm.agent.external.ExternalRuntimeTaskService;
import com.bidr.llm.agent.session.AgentSessionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Title: ExternalRuntimeTools
 * Description: 自主 agent 的<b>外部 runtime 派发工具</b>（形态 B：外部 runtime 是"手"不是"脑"）。
 *
 * <p>五件工具覆盖一个任务句柄的全生命周期：{@code submit} 立即返回 taskId（不阻塞派发轮），
 * {@code wait} 等终局，{@code query} 拉中间过程明细，{@code read} 取结论，{@code cancel} 取消。</p>
 *
 * <p><b>等待的标准路径是 {@code waitExternalTask}，不是 {@code queryExternalTask} 反复轮询</b>：
 * 回归实测（InkHub 208 页扫描件核查）纯轮询等待占 LLM 调用 78%，每次"继续等"都要付一轮
 * <b>满载上下文</b>的模型调用（≈1.5 万 prompt tokens/轮）。本工具把等待搬进服务端：
 * 内部按 {@link #POLL_INTERVAL_MILLIS} 探询进度（纯读，无上下文代价），
 * <b>状态变更或到 {@link #MAX_WAIT_MILLIS} 上限才返回</b>，返回只带状态与一行 live 摘要。
 * 单次阻塞有界且可中断（被 interrupt 立即返回并恢复中断位，停止按钮照常生效），
 * 任务心跳由派发侧泵线程独立刷新，不受本方法阻塞影响——
 * 这是"绝不在 @Tool 里同步等分钟级任务"旧口径的<b>有界例外</b>：
 * 边界是「不能无界阻塞 + 不能霸占轮次 + 不能堵死停止」，而非「不能阻塞」。
 * 配套纪律：本族的纯状态工具（query/read/wait）不产出新证据，消费方应把它们配进
 * {@code AgentLoopOptions.roundExemptTools}（等待不吃探索预算），用 {@link #STATUS_ONLY_TOOL_NAMES}。</p>
 *
 * <p>守卫：未接入 provider 时明确回绝（不静默假成功）；所有异常转成紧凑 JSON 回传给模型自纠。
 * 派单粒度提示写在工具描述里：外部 runtime 每轮固定开销大，<b>要打包成"值得开一次沙箱会话"的任务</b>。</p>
 *
 * @author sharp
 * @since 2026/9/24
 */
@Slf4j
public class ExternalRuntimeTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** waitExternalTask 单次阻塞上限（毫秒）：状态没变也最迟 60s 回一次模型，不让它静默挂死 */
    public static final long MAX_WAIT_MILLIS = 60_000L;

    /** waitExternalTask 内部探询间隔（毫秒）：读进度快照是纯读，间隔小只花服务端开销 */
    public static final long POLL_INTERVAL_MILLIS = 3_000L;

    /**
     * 🔴 注册名常量：本族 {@code @Tool} 未写显式 name，LangChain4j 按 <b>camelCase 方法名</b>注册，
     * 消费方配 roundExemptTools/pinnedTools 必须引用这里的常量——手写 snake_case 会静默失配
     * （工具照样能调，豁免/钉住却不生效，实测烧掉数百轮才看出来）。
     */
    public static final String TOOL_SUBMIT = "submitExternalTask";
    public static final String TOOL_QUERY = "queryExternalTask";
    public static final String TOOL_READ = "readExternalTaskResult";
    public static final String TOOL_CANCEL = "cancelExternalTask";
    public static final String TOOL_WAIT = "waitExternalTask";

    /** 纯状态仪式工具（不产出新证据）：可直接配进 {@code AgentLoopOptions.roundExemptTools} */
    public static final Set<String> STATUS_ONLY_TOOL_NAMES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(TOOL_QUERY, TOOL_READ, TOOL_WAIT)));

    private final ExternalRuntimeTaskService service;

    /** 发起本次派发的父会话（任务回链与归属继承用） */
    private final String parentSessionId;

    private final long pollIntervalMillis;

    private final long maxWaitMillis;

    public ExternalRuntimeTools(ExternalRuntimeTaskService service, String parentSessionId) {
        this(service, parentSessionId, POLL_INTERVAL_MILLIS, MAX_WAIT_MILLIS);
    }

    /** 等待时序可注入（单测用毫秒级验证阻塞循环，不真等 3s/60s）；生产走上一构造即默认值 */
    ExternalRuntimeTools(ExternalRuntimeTaskService service, String parentSessionId,
                         long pollIntervalMillis, long maxWaitMillis) {
        this.service = service;
        this.parentSessionId = parentSessionId;
        this.pollIntervalMillis = pollIntervalMillis;
        this.maxWaitMillis = maxWaitMillis;
    }

    @Tool("把一个需要真实执行环境（跑命令/改文件/长耗时）的子任务派给外部 agent runtime，"
            + "立即返回 taskId（不等待完成）。要求必须打包自足：写清目标、涉及路径、验收标准——"
            + "外部 runtime 看不到本会话上下文。之后用 waitExternalTask 等终局（纯等待不吃探索预算，"
            + "比反复 queryExternalTask 轮询省得多），终局后用 readExternalTaskResult 取结论。"
            + "禁止把小改动拆成多次派发（每次派发开销很大）")
    public String submitExternalTask(
            @P("外部 agent 编码；不确定就传 default") String agentCode,
            @P("任务需求全文（自足、含验收标准）") String requirement) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            if (!service.available()) {
                res.put("ok", false);
                res.put("error", "系统未接入外部 agent runtime，请自行完成该步骤");
                return json(res);
            }
            String taskId = service.submit(parentSessionId, agentCode, requirement);
            res.put("ok", true);
            res.put("taskId", taskId);
            res.put("next", "用 waitExternalTask 等终局（仍 RUNNING 就再次调用它继续等）；"
                    + "终局后用 readExternalTaskResult 取一次结论");
            return json(res);
        } catch (Exception e) {
            log.warn("外部任务派发失败：{}", e.getMessage());
            res.put("ok", false);
            res.put("error", "派发失败：" + e.getMessage());
            return json(res);
        }
    }

    @Tool("等待外部任务到终局（等待的标准路径）：服务端有界阻塞等待（单次最长 60 秒），"
            + "任务状态变化或超时才返回，纯等待轮不产出新证据（agent 侧应把本工具配进轮次豁免）。"
            + "返回仍 RUNNING 就再次调用本工具继续等；返回 FINISHED 后用 readExternalTaskResult "
            + "取一次全文结论（取过就不要重复取）。要看中间过程明细时才用 queryExternalTask。")
    public String waitExternalTask(@P("submitExternalTask 返回的 taskId") String taskId) {
        String id = taskId == null ? "" : taskId.trim();
        long deadline = System.currentTimeMillis() + maxWaitMillis;
        try {
            while (true) {
                Map<String, Object> progress = service.progress(id, 0, 1);
                String status = String.valueOf(progress.get("status"));
                if (!AgentSessionState.RUNNING.equals(status)) {
                    return terminalBrief(id, status, progress);
                }
                if (System.currentTimeMillis() >= deadline) {
                    return "仍 RUNNING（等待 " + (maxWaitMillis / 1000) + "s 后超时返回）："
                            + progress.get("live") + "。再次调用 waitExternalTask 继续等待；"
                            + "该任务未终局前不得给出最终回答。";
                }
                Thread.sleep(pollIntervalMillis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "等待被中断（会话可能正在停止）。";
        } catch (IllegalArgumentException e) {
            return "外部任务不存在或已过期：" + id + "。如需继续请重新派发任务。";
        } catch (Exception e) {
            log.warn("外部任务等待异常：{}", e.getMessage());
            return "等待失败：" + e.getMessage();
        }
    }

    @Tool("查外部任务进度：返回状态、自 cursor 起的新事件摘要与下次 cursor。"
            + "status=RUNNING 表示仍在跑——通常应改用 waitExternalTask 等终局，本工具用于看中间过程明细；"
            + "终局后请改用 readExternalTaskResult")
    public String queryExternalTask(
            @P("submit_external_task 返回的 taskId") String taskId,
            @P("上次返回的 cursor（首次传 0）") long cursor) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            res.put("ok", true);
            res.putAll(service.progress(taskId, cursor, 20));
            return json(res);
        } catch (Exception e) {
            res.put("ok", false);
            res.put("error", e.getMessage());
            return json(res);
        }
    }

    @Tool("取外部任务最终结论。未完成会返回 running=true（此时不要重复轮询，先做别的或据进度信息判断）")
    public String readExternalTaskResult(@P("taskId") String taskId) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            Map<String, Object> done = service.result(taskId);
            if (done == null) {
                res.put("ok", true);
                res.put("running", true);
                res.put("note", "任务仍在执行");
                return json(res);
            }
            res.put("ok", !com.bidr.llm.agent.session.AgentSessionState.FAILED.equals(done.get("status")));
            res.putAll(done);
            return json(res);
        } catch (Exception e) {
            res.put("ok", false);
            res.put("error", e.getMessage());
            return json(res);
        }
    }

    @Tool("取消外部任务（仅在任务明显跑偏或不再需要时使用；已终局则幂等返回现状）")
    public String cancelExternalTask(@P("taskId") String taskId) {
        Map<String, Object> res = new LinkedHashMap<>();
        try {
            res.put("ok", true);
            res.putAll(service.cancel(taskId));
            return json(res);
        } catch (Exception e) {
            res.put("ok", false);
            res.put("error", e.getMessage());
            return json(res);
        }
    }

    /** 终态瘦身回执：FINISHED 指向取结果，失败/停止带原因截文——不把事件明细灌回上下文 */
    private static String terminalBrief(String taskId, String status, Map<String, Object> progress) {
        if (AgentSessionState.FINISHED.equals(status)) {
            return "任务 FINISHED（已完成）：用 readExternalTaskResult(\"" + taskId
                    + "\") 取一次全文结论并纳入汇总（取过就不要重复取）。";
        }
        Object error = progress.get("error");
        Object summary = progress.get("summary");
        StringBuilder sb = new StringBuilder("任务 ").append(status)
                .append("（未正常完成，不得当作已完成）：");
        if (error != null && !String.valueOf(error).trim().isEmpty()) {
            sb.append("原因=").append(abbreviate(String.valueOf(error), 200)).append("。");
        }
        if (summary != null && !String.valueOf(summary).trim().isEmpty()) {
            sb.append("部分产出（只能当线索）：").append(abbreviate(String.valueOf(summary), 300)).append("。");
        }
        sb.append("如实向用户说明任务未完成及原因；如需继续请重新派发。");
        return sb.toString();
    }

    private static String abbreviate(String text, int max) {
        String flat = text == null ? "" : text.trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private static String json(Map<String, Object> map) {
        try {
            return OM.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"结果序列化失败\"}";
        }
    }
}
