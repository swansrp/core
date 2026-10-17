package com.bidr.insight.smartquery.service;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.llm.agent.session.AgentConfirmation;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Title: AssetReviewTools
 * Description: AI 评审链（asset-review-autonomous）暴露给 LLM 的产出工具：本链只读评审，
 * 不注册任何写工具（无 save_draft），"评审不修改"由工具集结构保证而非提示词自觉。
 * add_review_item 逐条累积评审结论（同表同疑点覆盖），submit_review 统一落盘结构化报告
 * （review-report 草稿，发布/校验一律跳过），finish 为会话收口信号；
 * report_unconfirmed 复用框架确认登记桥（会话结束后确认页逐条裁决）
 *
 * @author Sharp
 * @since 2026/8/25
 */
@Slf4j
public class AssetReviewTools {

    private static final ObjectMapper OM = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /** 评审结论取值：ok=合理 / questionable=值得商榷 */
    public static final String VERDICT_OK = "ok";
    public static final String VERDICT_QUESTIONABLE = "questionable";

    private final GenTaskContext ctx;
    /** 报告落盘回调（agentCode, reportJson）：生产链路接 SmartAgentMetaService.saveReviewReport，测试可替换 */
    private final BiConsumer<String, String> reportSink;
    private final Consumer<String> logSink;
    private final BooleanSupplier stopChecker;

    /** 累积评审条目（插入序；同 表+疑点 视为重复，后来者覆盖） */
    private final List<ObjectNode> items = new ArrayList<>();

    /** submit_review 已落盘（finish 前置闸依据；重复提交覆盖旧报告） */
    private volatile boolean reportSubmitted;
    /** finish 首次拒绝标记（未提交报告时先拒一次给改正机会，二次调用强制收口——逃生口） */
    private volatile boolean closeForced;
    private volatile boolean finished;
    private volatile String finishSummary;

    public AssetReviewTools(GenTaskContext ctx, BiConsumer<String, String> reportSink,
                            Consumer<String> logSink, BooleanSupplier stopChecker) {
        this.ctx = ctx;
        this.reportSink = reportSink;
        this.logSink = logSink;
        this.stopChecker = stopChecker;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getFinishSummary() {
        return finishSummary;
    }

    public boolean isReportSubmitted() {
        return reportSubmitted;
    }

    public synchronized int getItemCount() {
        return items.size();
    }

    public synchronized int getQuestionableCount() {
        int n = 0;
        for (ObjectNode item : items) {
            if (VERDICT_QUESTIONABLE.equals(item.path("verdict").asText())) {
                n++;
            }
        }
        return n;
    }

    private void report(String msg) {
        if (logSink != null) {
            logSink.accept(msg);
        }
    }

    /** 停止检查点：返回 null 放行；任务停止时返回拒绝文本（引擎轮头同步收口） */
    private String stopGuard() {
        if (stopChecker != null && stopChecker.getAsBoolean()) {
            return "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}";
        }
        return null;
    }

    @Tool("添加一条评审结论（逐条累积，全部评完后调 submit_review 统一落盘）：每张表的每个评审点一条——"
            + "verdict=ok 表示该结论合理（issue 填结论要点一句话即可），"
            + "verdict=questionable 表示值得商榷（issue 写疑点、evidence 写证据链、suggestion 写建议口径，三者必填）；"
            + "疑点针对具体列时务必填 column（前端据此定位高亮该列供修正），表级疑点（键/分区/快照口径）留空；"
            + "同一张表多个疑点分多条添加。不要为无关紧要的措辞差异标商榷")
    public synchronized String addReviewItem(@P("被评审表全名（db.tbl）") String table,
                                             @P("结论：ok=合理 / questionable=值得商榷") String verdict,
                                             @P("ok 时为结论要点一句话；questionable 时为疑点描述") String issue,
                                             @P("证据链（采样数据/字段结构等依据，ok 可空）") String evidence,
                                             @P("建议口径（该怎么改，ok 可空）") String suggestion,
                                             @P("疑点针对的具体列名（前端定位修正用；表级疑点/ok 可空）") String column) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        if (FuncUtil.isEmpty(table)) {
            return "拒绝：表全名不能为空";
        }
        if (!VERDICT_OK.equals(verdict) && !VERDICT_QUESTIONABLE.equals(verdict)) {
            return "拒绝：verdict 仅支持 ok / questionable（收到：" + verdict + "）";
        }
        if (VERDICT_QUESTIONABLE.equals(verdict)) {
            if (FuncUtil.isEmpty(issue)) {
                return "拒绝：questionable 条目必须写明疑点描述（issue）";
            }
            if (FuncUtil.isEmpty(evidence)) {
                return "拒绝：questionable 条目必须附证据链（evidence：采样/结构等依据），无证据的疑点不收";
            }
        }
        String t = table.trim();
        String i = FuncUtil.isEmpty(issue) ? "" : issue.trim();
        // 同表同疑点视为重复：后来者覆盖（结论修正后重提，不留陈旧条目）
        ObjectNode node = OM.createObjectNode();
        node.put("table", t);
        node.put("verdict", verdict);
        if (FuncUtil.isNotEmpty(i)) {
            node.put("issue", i);
        }
        if (FuncUtil.isNotEmpty(evidence)) {
            node.put("evidence", evidence.trim());
        }
        if (FuncUtil.isNotEmpty(suggestion)) {
            node.put("suggestion", suggestion.trim());
        }
        if (FuncUtil.isNotEmpty(column)) {
            node.put("column", column.trim());
        }
        for (int k = 0; k < items.size(); k++) {
            ObjectNode old = items.get(k);
            if (t.equals(old.path("table").asText()) && i.equals(old.path("issue").asText(""))) {
                items.set(k, node);
                log.info("[LLM工具] Agent '{}' add_review_item 覆盖: {} / {}", ctx.getAgentCode(), t, i);
                return "已更新评审条目（累计 " + items.size() + " 条，其中值得商榷 " + getQuestionableCount() + " 条）";
            }
        }
        items.add(node);
        log.info("[LLM工具] Agent '{}' add_review_item: {} / {} / {}", ctx.getAgentCode(), t, verdict, brief(i));
        report("工具 add_review_item：" + t + " → " + (VERDICT_OK.equals(verdict) ? "✅ 合理" : "⚠️ 值得商榷"));
        return "已添加评审条目（累计 " + items.size() + " 条，其中值得商榷 " + getQuestionableCount() + " 条）";
    }

    @Tool("将已累积的评审条目落盘为结构化评审报告（finish 前必调；重复调用覆盖上一份报告）。"
            + "报告将展示给管理员逐条处理：值得商榷项由管理员在实体确认页修正后重新认证")
    public synchronized String submitReview() {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        if (items.isEmpty()) {
            return "拒绝：尚无任何评审条目——请先逐表评审并 add_review_item，再提交报告";
        }
        ObjectNode root = OM.createObjectNode();
        root.put("schema_version", "1.0");
        root.put("agent_code", ctx.getAgentCode());
        root.put("reviewed_at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        root.put("total", items.size());
        root.put("questionable", getQuestionableCount());
        ArrayNode arr = OM.createArrayNode();
        for (ObjectNode item : items) {
            arr.add(item);
        }
        root.set("items", arr);
        try {
            reportSink.accept(ctx.getAgentCode(), OM.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (Exception e) {
            log.error("Agent '{}' 评审报告落盘失败", ctx.getAgentCode(), e);
            return "落盘失败：" + e.getMessage() + "，请稍后重试";
        }
        reportSubmitted = true;
        log.info("[LLM工具] Agent '{}' submit_review: {} 条（商榷 {}）",
                ctx.getAgentCode(), items.size(), getQuestionableCount());
        report("工具 submit_review：评审报告已落盘（" + items.size() + " 条，值得商榷 "
                + getQuestionableCount() + " 条）");
        return "评审报告已落盘（共 " + items.size() + " 条，值得商榷 " + getQuestionableCount()
                + " 条），可调用 finish 收口";
    }

    @Tool("登记一条未经用户确认的自决口径（收口闭环，会话结束前逐条登记完）：凡 ask_user 超时/被跳过/无法提问"
            + "而由你自行裁决的评审结论（尤其把商榷项判为合理、或替用户拍板口径），在 finish 前逐条登记——"
            + "确认页会逐条展示给用户裁决，漏登则该口径风险无收口渠道。同一疑点登记一次即可")
    public String reportUnconfirmed(@P("疑点描述（一句话含背景）") String question,
                                    @P("你采纳的口径（自决结论）") String adopted,
                                    @P("证据链（采样数据/工具返回等依据）") String evidence,
                                    @P("影响的产出（表全名或评审条目，多个逗号分隔）") String impact) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        if (FuncUtil.isEmpty(question) || FuncUtil.isEmpty(adopted)) {
            return "拒绝：疑点描述与采纳口径均不能为空";
        }
        AgentSessionContext sc = ctx.getSessionCtx();
        if (sc == null) {
            return "无会话通道，登记不生效；请在 finish 总结中注明该口径未经用户确认";
        }
        AgentConfirmation item = sc.addConfirmation(question.trim(), adopted.trim(),
                FuncUtil.isEmpty(evidence) ? null : evidence.trim(),
                FuncUtil.isEmpty(impact) ? null : impact.trim());
        log.info("[LLM工具] Agent '{}' report_unconfirmed: {}", ctx.getAgentCode(), question);
        report("工具 report_unconfirmed：登记待确认口径 #" + item.getId());
        return "已登记待确认口径 #" + item.getId() + "（会话结束后确认页将由用户裁决）";
    }

    @Tool("评审收口：须已调 submit_review 落盘评审报告。总结列出评审了几张表、值得商榷几条、"
            + "关键疑点与未确认口径条数（不必重复报告全文）")
    public String finish(@P("评审总结：覆盖表数、商榷条数、关键疑点、需人工关注点") String summary) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' finish: {}", ctx.getAgentCode(), summary);
        report("工具 finish：评审收口，总结=" + brief(summary));
        // 前置闸：未落盘报告先拒一次给改正机会；二次调用视为确认强制收口（逃生口）
        if (!reportSubmitted && !closeForced) {
            closeForced = true;
            return "评审报告尚未落盘：请先调 submit_review 提交累积的评审条目，再调 finish（若确认无评审结论可再次调用强制收口）";
        }
        finished = true;
        finishSummary = summary;
        return "已确认收口。评审总结已记录";
    }

    /** 摘要截断（日志可读） */
    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() <= 120 ? oneLine : oneLine.substring(0, 120) + "…";
    }
}
