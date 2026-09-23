package com.bidr.llm.agent.tools;

import com.bidr.llm.agent.external.ExternalRuntimeTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Title: ExternalRuntimeTools
 * Description: 自主 agent 的<b>外部 runtime 派发工具</b>（形态 B：外部 runtime 是"手"不是"脑"）。
 *
 * <p>四个方法全是<b>两段式</b>：{@code submit} 立即返回任务句柄（不阻塞本轮循环），
 * 由模型在后续轮次用 {@code query}/{@code read} 拉进度与结果，必要时 {@code cancel}。
 * 🔴 绝不在工具里同步等分钟级任务完结——那会霸占轮次并把上游全文灌回上下文。</p>
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

    private final ExternalRuntimeTaskService service;

    /** 发起本次派发的父会话（任务回链与归属继承用） */
    private final String parentSessionId;

    public ExternalRuntimeTools(ExternalRuntimeTaskService service, String parentSessionId) {
        this.service = service;
        this.parentSessionId = parentSessionId;
    }

    @Tool("把一个需要真实执行环境（跑命令/改文件/长耗时）的子任务派给外部 agent runtime，"
            + "立即返回 taskId（不等待完成）。要求必须打包自足：写清目标、涉及路径、验收标准——"
            + "外部 runtime 看不到本会话上下文。之后用 query_external_task 拉进度、"
            + "read_external_task_result 取结论。禁止把小改动拆成多次派发（每次派发开销很大）")
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
            res.put("next", "稍后用 query_external_task 查进度；完成后再 read_external_task_result 取结论");
            return json(res);
        } catch (Exception e) {
            log.warn("外部任务派发失败：{}", e.getMessage());
            res.put("ok", false);
            res.put("error", "派发失败：" + e.getMessage());
            return json(res);
        }
    }

    @Tool("查外部任务进度：返回状态、自 cursor 起的新事件摘要与下次 cursor。"
            + "status=RUNNING 表示仍在跑（可继续做别的或稍后再查）；终局后请改用 read_external_task_result")
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

    private static String json(Map<String, Object> map) {
        try {
            return OM.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"结果序列化失败\"}";
        }
    }
}
