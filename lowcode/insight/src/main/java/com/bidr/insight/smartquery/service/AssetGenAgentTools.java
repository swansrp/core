package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.dao.entity.InsightAgentAsset;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.meta.CertifiedDraftMerger;
import com.bidr.insight.smartquery.meta.SupportedDimensionSupport;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.llm.agent.session.AgentConfirmation;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: AssetGenAgentTools
 * Description: AI 自主模式（autonomous）暴露给 LLM 的产出工具（与 AgentExploreTools 只读探索工具
 * 同会话共用）：save_draft 落库守卫内置（类型白名单/JSON 合法/formula 防幻觉/敏感列拦截），
 * 拒绝原因回传 LLM 自纠；get_draft/list_status 支撑增量续作（已有草稿非失败不重做）；
 * finish 为会话收口信号。守卫不依赖 LLM 自律：幻觉引用与敏感列在代码层强制拦截
 *
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public class AssetGenAgentTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** save_draft 类型白名单（骨架三类确定性生成、敏感字段人工声明，不经 LLM 落库） */
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList("metrics", "relations", "concepts"));

    /** formula 三段式列引用提取（db.tbl.col）：守卫校验引用真实存在防幻觉 */
    private static final Pattern COL_REF = Pattern.compile("(\\w+)\\.(\\w+)\\.(\\w+)");

    private final GenTaskContext ctx;
    private final SmartAgentMetaService metaService;
    private final CertifiedDraftMerger certifiedDraftMerger;
    private final Consumer<String> logSink;
    private final BooleanSupplier stopChecker;

    /** finish 已调用（会话收口信号，服务侧日志与兜底判断用） */
    private volatile boolean finished;
    private volatile String finishSummary;

    public AssetGenAgentTools(GenTaskContext ctx, SmartAgentMetaService metaService,
                              CertifiedDraftMerger certifiedDraftMerger,
                              Consumer<String> logSink, BooleanSupplier stopChecker) {
        this.ctx = ctx;
        this.metaService = metaService;
        this.certifiedDraftMerger = certifiedDraftMerger;
        this.logSink = logSink;
        this.stopChecker = stopChecker;
    }

    public boolean isFinished() {
        return finished;
    }

    public String getFinishSummary() {
        return finishSummary;
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

    @Tool("保存一类资产草稿并立即落库。三类均为 upsert 合并：来项按 name 新增/覆盖同名旧项，未提及的现存条目保留不动；"
            + "relations 传数组 [...] 或 {\"relations\":[...],\"drop_names\":[\"要删的条目名\"]}，"
            + "concepts 传 {\"concepts\":[...],\"drop_names\":[...]}；删除条目用 drop_names 显式声明（认证项不可删）；"
            + "metrics 按表合并：本次提交中出现的表（source_table）整体替换草稿中该表的指标，未提及的表保留不动。"
            + "指标的 supported_dimensions 不必输出（后端按骨架+关系自动展开覆盖，不会漏）。"
            + "内容带守卫校验：引用不存在的表/列/维度或命中敏感列的条目会被拒绝并给出原因，"
            + "修正后重提即可。返回会带合并后草稿总数：内容无变化的重复提交不会改变草稿，只浪费轮次，已保存过的类不要重复保存。"
            + "每完成一类（或一张表的指标）就保存一次，不要攒到最后")
    public String saveDraft(@P("资产类型：metrics / relations / concepts") String type,
                            @P("资产内容 JSON：metrics/relations 为数组 [...]，concepts 为 {\"concepts\":[...]} 对象") String contentJson) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' save_draft: {}", ctx.getAgentCode(), type);
        report("工具 save_draft：保存 " + type + " 草稿");
        if (type == null || !TYPES.contains(type)) {
            return "拒绝：类型不合法（" + type + "），仅支持 " + TYPES;
        }
        JsonNode root;
        try {
            root = OM.readTree(contentJson);
        } catch (Exception e) {
            return "拒绝：内容不是合法 JSON（" + e.getMessage() + "），请检查后重提";
        }
        Set<String> rejects = new LinkedHashSet<>();
        String summary;
        if ("metrics".equals(type)) {
            summary = saveMetrics(root, rejects);
        } else if ("relations".equals(type)) {
            summary = saveRelations(root, rejects);
        } else {
            summary = saveConcepts(root, rejects);
        }
        if (!rejects.isEmpty()) {
            // 守卫拒绝：本次整体不落库，逐条原因回传供 LLM 自纠重提
            report("save_draft(" + type + ") 拒绝 " + rejects.size() + " 条");
            return "拒绝（未落库，修正后重提）：\n- " + String.join("\n- ", rejects);
        }
        // 返回带草稿计数：让模型看到已存多少，防同内容重复提交白烧输出 token（concepts 连提同批的教训）
        return summary;
    }

    /** 指标守卫+按表合并落库：name/source_table/formula 列引用逐条校验；
     *  supported_dimensions 后端确定性展开（骨架+关系可达，排除敏感），不收 LLM 清单 */
    private String saveMetrics(JsonNode root, Set<String> rejects) {
        if (!root.isArray()) {
            rejects.add("metrics 内容须为 JSON 数组");
            return null;
        }
        for (JsonNode m : root) {
            String name = m.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                rejects.add("指标缺少 name");
                continue;
            }
            String table = m.path("source_table").asText("").trim().toLowerCase();
            EntityDef entity = entityByTable(table);
            if (entity == null) {
                rejects.add("指标 " + name + "：source_table " + table + " 不在选表范围");
                continue;
            }
            String formula = m.path("formula").asText("");
            boolean refOk = true;
            Matcher matcher = COL_REF.matcher(formula);
            while (matcher.find()) {
                String ref = matcher.group(1) + "." + matcher.group(2);
                if (!entity.getTable().equals(ref)) {
                    rejects.add("指标 " + name + "：formula 引用表 " + ref + " 与 source_table " + table + " 不一致（指标须严格单表）");
                    refOk = false;
                    break;
                }
                String col = matcher.group(3);
                if (entity.getFields().stream().noneMatch(f -> col.equals(f.getName()))) {
                    rejects.add("指标 " + name + "：formula 引用列 " + ref + "." + col + " 不存在");
                    refOk = false;
                    break;
                }
                if (isSensitive(entity.getName(), col)) {
                    rejects.add("指标 " + name + "：formula 引用敏感列 " + col + "，禁止");
                    refOk = false;
                    break;
                }
            }
            if (!refOk) {
                continue;
            }
        }
        if (!rejects.isEmpty()) {
            return null;
        }
        // 按表合并 + 认证语义：已认证现存项原样保留、未认证按表替换，来项盖未认证（集中 mergeLlmDraftWithCertified）
        JsonNode mergedNode = certifiedDraftMerger.mergeLlmDraftWithCertified(ctx.getAgentCode(), "metrics", root);
        ArrayNode merged;
        if (mergedNode instanceof ArrayNode) {
            merged = (ArrayNode) mergedNode;
        } else {
            // 现存草稿不可解析时回落：按表覆盖（旧语义）
            Set<String> touched = new HashSet<>();
            for (JsonNode m : root) {
                touched.add(m.path("source_table").asText("").trim().toLowerCase());
            }
            merged = OM.createArrayNode();
            InsightAgentAsset existed = metaService.getAsset(ctx.getAgentCode(), "metrics");
            if (existed != null && FuncUtil.isNotEmpty(existed.getContent())) {
                try {
                    for (JsonNode old : OM.readTree(existed.getContent())) {
                        if (!touched.contains(old.path("source_table").asText("").trim().toLowerCase())) {
                            merged.add(old);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Agent '{}' 现有指标草稿解析失败，按全量覆盖保存: {}", ctx.getAgentCode(), e.getMessage());
                }
            }
            merged.addAll((ArrayNode) root);
        }
        // supported_dimensions 后端化展开：源表骨架维度 + 关系可达实体维度（排除敏感），全覆盖落库，
        // LLM 不必输出也不会漏列（问数校验白名单由确定性规则保证完备）
        SupportedDimensionSupport.expand(merged, ctx.getEntities(), ctx.getDimensions(),
                ctx.getSensitiveKeys(), relationsDraftNode());
        persist("metrics", merged);
        report("save_draft(metrics) 完成：本批 " + root.size() + " 项，合并后草稿共 " + merged.size() + " 项（认证项保留）");
        return "已保存 metrics 草稿：本批 " + root.size() + " 项，合并后草稿共 " + merged.size()
                + " 项（supported_dimensions 已按骨架+关系自动展开）";
    }

    /** 关系草稿解析为 JsonNode（supported_dimensions 展开的可达性依据）；无草稿/不可解析返 null */
    private JsonNode relationsDraftNode() {
        InsightAgentAsset asset = metaService.getAsset(ctx.getAgentCode(), "relations");
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return null;
        }
        try {
            return OM.readTree(asset.getContent());
        } catch (Exception e) {
            log.warn("Agent '{}' relations 草稿解析失败，维度展开仅含本表维度: {}", ctx.getAgentCode(), e.getMessage());
            return null;
        }
    }

    /** 关系守卫+upsert 落库：from/to 实体与 join 列逐条校验；支持数组或 {relations, drop_names} 包装 */
    private String saveRelations(JsonNode root, Set<String> rejects) {
        JsonNode items = root.isArray() ? root : root.path("relations");
        if (!items.isArray()) {
            rejects.add("relations 内容须为 JSON 数组 [...] 或 {\"relations\":[...],\"drop_names\":[...]} 对象");
            return null;
        }
        for (JsonNode r : items) {
            String name = r.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                rejects.add("关系缺少 name");
                continue;
            }
            EntityDef from = entityByName(r.path("from_entity").asText(""));
            EntityDef to = entityByName(r.path("to_entity").asText(""));
            if (from == null || to == null) {
                rejects.add("关系 " + name + "：from_entity/to_entity 不在骨架实体中");
                continue;
            }
            if (r.path("join").size() == 0) {
                rejects.add("关系 " + name + "：缺少 join 条件");
                continue;
            }
            for (JsonNode j : r.path("join")) {
                if (!columnExists(from, j.path("left").asText())) {
                    rejects.add("关系 " + name + "：join.left 列 " + j.path("left").asText() + " 不属于实体 " + from.getName());
                }
                if (!columnExists(to, j.path("right").asText())) {
                    rejects.add("关系 " + name + "：join.right 列 " + j.path("right").asText() + " 不属于实体 " + to.getName());
                }
            }
        }
        if (!rejects.isEmpty()) {
            return null;
        }
        // upsert：工具层预拼全量列表（现存 - drop - 同名 + 来项），再走认证合并（认证项永不删、来项盖未认证）
        ArrayNode full = upsertMergeList("relations", items, collectDropNames(root.path("drop_names")), rejects);
        if (!rejects.isEmpty()) {
            return null;
        }
        JsonNode mergedRelations = certifiedDraftMerger.mergeLlmDraftWithCertified(ctx.getAgentCode(), "relations", full);
        persist("relations", mergedRelations != null ? mergedRelations : full);
        report("save_draft(relations) 完成：本批 " + items.size() + " 项，合并后草稿共 " + full.size() + " 项（认证项保留）");
        return "已保存 relations 草稿：本批 " + items.size() + " 项，合并后草稿共 " + full.size() + " 项";
    }

    /** 概念守卫+upsert 落库：expands_to.dimension 逐条校验；支持 {concepts, drop_names} 对象 */
    private String saveConcepts(JsonNode root, Set<String> rejects) {
        JsonNode concepts = root.path("concepts");
        if (!root.isObject() || !concepts.isArray()) {
            rejects.add("concepts 内容须为 {\"concepts\":[...]} 对象");
            return null;
        }
        for (JsonNode c : concepts) {
            String name = c.path("name").asText("");
            if (FuncUtil.isEmpty(name)) {
                rejects.add("概念缺少 name");
                continue;
            }
            JsonNode expands = c.path("expands_to");
            String dim = expands.path("dimension").asText("");
            if (FuncUtil.isEmpty(dim) || !ctx.getDimensionNames().contains(dim)) {
                rejects.add("概念 " + name + "：expands_to.dimension 引用不存在的维度 " + dim);
            }
        }
        if (!rejects.isEmpty()) {
            return null;
        }
        // upsert：同 relations，预拼全量概念列表后重建对象再走认证合并；
        // hierarchy 不携带：分级目录是实体列级归类的派生视图，LLM 赋组不落库（合并根重建时恒保留现存）
        ArrayNode fullConcepts = upsertMergeList("concepts", concepts, collectDropNames(root.path("drop_names")), rejects);
        if (!rejects.isEmpty()) {
            return null;
        }
        com.fasterxml.jackson.databind.node.ObjectNode rebuilt = OM.createObjectNode();
        rebuilt.put("schema_version", root.path("schema_version").asText("1.0"));
        rebuilt.set("concepts", fullConcepts);
        rebuilt.set("hierarchy", OM.createArrayNode());
        JsonNode mergedConcepts = certifiedDraftMerger.mergeLlmDraftWithCertified(ctx.getAgentCode(), "concepts", rebuilt);
        persist("concepts", mergedConcepts != null ? mergedConcepts : rebuilt);
        report("save_draft(concepts) 完成：本批 " + concepts.size() + " 项，合并后草稿共 " + fullConcepts.size() + " 项（认证项保留）");
        return "已保存 concepts 草稿：本批 " + concepts.size() + " 项，合并后草稿共 " + fullConcepts.size()
                + " 项（同内容重复提交不会改变草稿，不要重复保存）";
    }

    @Tool("读取某类资产现有草稿全文（增量续作时先看已有成果，非失败不重做）")
    public String getDraft(@P("资产类型：metrics / relations / concepts") String type) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' get_draft: {}", ctx.getAgentCode(), type);
        if (type == null || !TYPES.contains(type)) {
            return "{\"error\":\"类型不合法，仅支持 metrics/relations/concepts\"}";
        }
        InsightAgentAsset asset = metaService.getAsset(ctx.getAgentCode(), type);
        report("工具 get_draft：读取 " + type + " 草稿");
        if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
            return "（" + type + " 尚无草稿）";
        }
        return asset.getContent();
    }

    @Tool("查看任务总览：选表清单（含各表已登记码值域数）、各类草稿完成状态、敏感声明状态。会话开始先调本工具，"
            + "据此决定还需生成什么（已有且非失败的草稿不要重做）、以什么顺序与拆分粒度进行")
    public String listStatus() {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' list_status", ctx.getAgentCode());
        report("工具 list_status：查看任务总览");
        StringBuilder sb = new StringBuilder("{\"tables\":[");
        boolean first = true;
        int coveredTables = 0;
        for (EntityDef e : ctx.getEntities()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            // 码值域覆盖信息：已登记数>0 即配对已办，模型无需再建码值域待办（消 0/17 类空转）
            int domainCount = 0;
            for (com.bidr.insight.smartquery.layer.ValueDomainDef d : ctx.getDomains().values()) {
                if (e.getName() != null && e.getName().equals(d.getEntity())) {
                    domainCount++;
                }
            }
            if (domainCount > 0) {
                coveredTables++;
            }
            sb.append("{\"entity\":\"").append(jsonEscape(e.getName()))
                    .append("\",\"table\":\"").append(jsonEscape(e.getTable()))
                    .append("\",\"domains\":").append(domainCount).append('}');
        }
        sb.append("],\"drafts\":{");
        first = true;
        for (String type : TYPES) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            InsightAgentAsset asset = metaService.getAsset(ctx.getAgentCode(), type);
            int count = 0;
            boolean has = asset != null && FuncUtil.isNotEmpty(asset.getContent());
            if (has) {
                try {
                    JsonNode root = OM.readTree(asset.getContent());
                    JsonNode arr = "concepts".equals(type) ? root.path("concepts") : root;
                    count = arr.isArray() ? arr.size() : 0;
                } catch (Exception ignored) {
                    count = 0;
                }
            }
            sb.append("\"").append(type).append("\":{\"exists\":").append(has)
                    .append(",\"count\":").append(count).append('}');
        }
        sb.append("},\"sensitive_fields\":{");
        InsightAgentAsset sf = metaService.getAsset(ctx.getAgentCode(), "sensitive-fields");
        boolean sfDeclared = sf != null && FuncUtil.isNotEmpty(sf.getContent());
        sb.append("\"declared\":").append(sfDeclared).append("}");
        if (finished) {
            sb.append(",\"finished\":true");
        }
        if (!ctx.getEntities().isEmpty() && coveredTables == ctx.getEntities().size()) {
            // 全覆盖提示：码值域已全登记，禁止再为配对/码值建待办（确定性结论直接采信）
            sb.append(",\"domain_note\":\"全部 ").append(ctx.getEntities().size())
                    .append(" 张表码值域已登记，无需再做编码↔名称配对\"");
        }
        sb.append('}');
        return sb.toString();
    }

    @Tool("按需查维度明细（提示词只给维度概要）：按表全名或关键词过滤，返回维度名/显示名/表达式/粒度，"
            + "上限 60 条。概念展开选维度、核实维度归属时用；不需要全量枚举——指标的 supported_dimensions 由后端自动展开")
    public String getDimensions(@P("表全名（db.tbl）或维度名/显示名片段，可空=返回前 60 条") String keyword) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' get_dimensions: {}", ctx.getAgentCode(), keyword);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        ArrayNode out = OM.createArrayNode();
        for (DimensionDef d : ctx.getDimensions()) {
            if (out.size() >= 60) {
                break;
            }
            if (!kw.isEmpty() && !((d.getName() != null && d.getName().toLowerCase().contains(kw))
                    || (d.getDisplayName() != null && d.getDisplayName().toLowerCase().contains(kw))
                    || (d.getExpression() != null && d.getExpression().toLowerCase().contains(kw)))) {
                continue;
            }
            com.fasterxml.jackson.databind.node.ObjectNode n = OM.createObjectNode();
            n.put("name", d.getName() == null ? "" : d.getName());
            n.put("display_name", d.getDisplayName() == null ? "" : d.getDisplayName());
            n.put("expression", d.getExpression() == null ? "" : d.getExpression());
            if (FuncUtil.isNotEmpty(d.getGranularity())) {
                n.put("granularity", d.getGranularity());
            }
            out.add(n);
        }
        return out.size() == 0 ? "（无匹配维度）" : out.toString();
    }

    @Tool("登记一条未经用户确认的自决口径（收口闭环，会话结束前逐条登记完）：凡 ask_user 超时/被跳过/无法提问"
            + "而由你自行决策的口径，在 finish 前逐条登记——确认页会逐条展示给用户裁决（一键确认或改口径重算），"
            + "漏登则该口径风险无收口渠道。同一疑点登记一次即可，不要重复")
    public String reportUnconfirmed(@P("疑点描述（一句话含背景）") String question,
                                    @P("你采纳的口径（自决结论，须已落实到对应资产）") String adopted,
                                    @P("证据链（采样数据/工具返回等依据）") String evidence,
                                    @P("影响的产出（资产项名，多个逗号分隔）") String impact) {
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

    @Tool("全部资产（指标/关系/概念）完成并保存后调用，提交任务总结收口。不要遗漏任何一类——"
            + "确无可产出的类也要在总结中说明理由。首次调用会做收口自查（草稿计数与骨架引用有效性），"
            + "发现问题按清单修正重存后再收口；确认无误再次调用即正式收口")
    public String finish(@P("任务总结：各类完成情况、条目数、需人工关注点") String summary) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' finish: {}", ctx.getAgentCode(), summary);
        report("工具 finish：会话收口，总结=" + brief(summary));
        // 首次收口硬闸：审计不通过不落 finished，问题清单回传修正；二次调用视为确认强制收口（逃生口）
        if (!finishAudited) {
            finishAudited = true;
            List<String> issues = auditDrafts();
            if (!issues.isEmpty()) {
                report("finish 收口自查发现 " + issues.size() + " 项问题，已回传待修正");
                return "收口自查发现问题（修正后重存相应草稿，再调 finish；若确认无碍可再次调用强制收口）：\n- "
                        + String.join("\n- ", issues);
            }
        }
        finished = true;
        finishSummary = summary;
        return "已确认收口。任务总结已记录";
    }

    /** finish 是否已做过一次收口审计（二次调用强制收口的判定依据） */
    private volatile boolean finishAudited;

    /** 收口审计：三类草稿计数 + 骨架引用有效性（表/列/实体/维度），返回问题清单（空为通过） */
    private List<String> auditDrafts() {
        List<String> issues = new ArrayList<>();
        for (String type : TYPES) {
            InsightAgentAsset asset = metaService.getAsset(ctx.getAgentCode(), type);
            if (asset == null || FuncUtil.isEmpty(asset.getContent())) {
                issues.add(type + " 无草稿：确无可产出请在 finish 总结中说明理由");
                continue;
            }
            JsonNode root;
            try {
                root = OM.readTree(asset.getContent());
            } catch (Exception e) {
                issues.add(type + " 草稿不是合法 JSON");
                continue;
            }
            JsonNode arr = "concepts".equals(type) ? root.path("concepts") : root;
            if (!arr.isArray()) {
                issues.add(type + " 草稿结构不符（应为数组）");
                continue;
            }
            if (arr.isEmpty()) {
                issues.add(type + " 草稿为空");
                continue;
            }
            if ("metrics".equals(type)) {
                auditMetrics(arr, issues);
            } else if ("relations".equals(type)) {
                auditRelations(arr, issues);
            } else {
                auditConcepts(arr, issues);
            }
        }
        return issues;
    }

    /** 指标审计：formula 列引用真实存在、与 source_table 同表、维度在骨架 */
    private void auditMetrics(JsonNode arr, List<String> issues) {
        for (JsonNode m : arr) {
            String name = m.path("name").asText("?");
            EntityDef entity = entityByTable(m.path("source_table").asText("").trim().toLowerCase());
            if (entity == null) {
                issues.add("指标 " + name + "：source_table 不在选表范围");
                continue;
            }
            Matcher matcher = COL_REF.matcher(m.path("formula").asText(""));
            while (matcher.find()) {
                String ref = matcher.group(1) + "." + matcher.group(2);
                if (!entity.getTable().equals(ref)) {
                    issues.add("指标 " + name + "：formula 跨表引用 " + ref);
                    break;
                }
                if (!columnExists(entity, matcher.group(3))) {
                    issues.add("指标 " + name + "：formula 列 " + matcher.group(3) + " 不存在");
                    break;
                }
            }
            for (JsonNode d : m.path("supported_dimensions")) {
                if (!ctx.getDimensionNames().contains(d.asText())) {
                    issues.add("指标 " + name + "：维度 " + d.asText() + " 不在骨架");
                    break;
                }
            }
        }
    }

    /** 关系审计：两端实体在骨架、join 列真实存在 */
    private void auditRelations(JsonNode arr, List<String> issues) {
        for (JsonNode r : arr) {
            String name = r.path("name").asText("?");
            EntityDef from = entityByName(r.path("from_entity").asText(""));
            EntityDef to = entityByName(r.path("to_entity").asText(""));
            if (from == null || to == null) {
                issues.add("关系 " + name + "：from_entity/to_entity 不在骨架实体中");
                continue;
            }
            for (JsonNode j : r.path("join")) {
                if (!columnExists(from, j.path("left").asText())) {
                    issues.add("关系 " + name + "：join.left 列不属于 " + from.getName());
                    break;
                }
                if (!columnExists(to, j.path("right").asText())) {
                    issues.add("关系 " + name + "：join.right 列不属于 " + to.getName());
                    break;
                }
            }
        }
    }

    /** 概念审计：expands_to.dimension 在骨架维度中 */
    private void auditConcepts(JsonNode arr, List<String> issues) {
        for (JsonNode c : arr) {
            String dim = c.path("expands_to").path("dimension").asText("");
            if (FuncUtil.isEmpty(dim) || !ctx.getDimensionNames().contains(dim)) {
                issues.add("概念 " + c.path("name").asText("?") + "：expands_to.dimension 不在骨架维度中");
            }
        }
    }

    // ---------------- 守卫与落库辅助 ----------------

    /** upsert 预拼全量列表：现存条目（认证项永留）- drop_names - 与来项同名者 + 来项，
     *  再交给 mergeLlmDraftWithCertified 走认证语义（来项盖未认证、同名认证优先） */
    private ArrayNode upsertMergeList(String type, JsonNode incoming, Set<String> dropNames, Set<String> rejects) {
        Set<String> inNames = new LinkedHashSet<>();
        for (JsonNode in : incoming) {
            String n = in.path("name").asText("");
            if (FuncUtil.isNotEmpty(n)) {
                inNames.add(n);
            }
        }
        ArrayNode result = OM.createArrayNode();
        InsightAgentAsset existed = metaService.getAsset(ctx.getAgentCode(), type);
        if (existed != null && FuncUtil.isNotEmpty(existed.getContent())) {
            try {
                JsonNode oldRoot = OM.readTree(existed.getContent());
                JsonNode oldArr = "concepts".equals(type) ? oldRoot.path("concepts") : oldRoot;
                if (oldArr.isArray()) {
                    for (JsonNode old : oldArr) {
                        String name = old.path("name").asText("");
                        boolean certified = old.path("certified").asBoolean(false);
                        if (dropNames.contains(name)) {
                            if (certified) {
                                rejects.add("条目 " + name + " 已认证，不可通过 drop_names 删除");
                            }
                            continue;
                        }
                        // 认证项永留；与来项同名的未认证旧项丢弃由新版本接管；同名认证项保留（认证优先，来项会被下游合并丢弃）
                        if (!inNames.contains(name) || certified) {
                            result.add(old);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Agent '{}' 现存 {} 草稿解析失败，仅保存本次来项: {}", ctx.getAgentCode(), type, e.getMessage());
            }
        }
        for (JsonNode in : incoming) {
            result.add(in);
        }
        return result;
    }

    /** drop_names 收集（非数组视为无删除项） */
    private static Set<String> collectDropNames(JsonNode dropNode) {
        Set<String> drops = new LinkedHashSet<>();
        if (dropNode != null && dropNode.isArray()) {
            for (JsonNode d : dropNode) {
                String n = d.asText("");
                if (FuncUtil.isNotEmpty(n)) {
                    drops.add(n);
                }
            }
        }
        return drops;
    }

    private EntityDef entityByTable(String table) {
        return ctx.getEntities().stream()
                .filter(e -> table.equals(e.getTable())).findFirst().orElse(null);
    }

    private EntityDef entityByName(String name) {
        return ctx.getEntities().stream()
                .filter(e -> name.equals(e.getName())).findFirst().orElse(null);
    }

    private static boolean columnExists(EntityDef entity, String column) {
        return entity.getFields().stream().anyMatch(f -> column.equals(f.getName()));
    }

    /** 敏感列判定（与生成服务 isSensitiveField 同口径：entity.field 小写键） */
    private boolean isSensitive(String entityName, String field) {
        return ctx.getSensitiveKeys() != null
                && ctx.getSensitiveKeys().contains((entityName + "." + field).toLowerCase());
    }

    /** 落库（与生成服务 saveLlmDraft 同口径：pretty 序列化覆盖保存） */
    private void persist(String type, JsonNode node) {
        try {
            metaService.saveAssetDraft(ctx.getAgentCode(), type,
                    OM.writerWithDefaultPrettyPrinter().writeValueAsString(node));
        } catch (Exception e) {
            log.error("Agent '{}' 草稿 '{}' 保存失败", ctx.getAgentCode(), type, e);
            throw new IllegalStateException("草稿 " + type + " 保存失败: " + e.getMessage(), e);
        }
    }

    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 160 ? one.substring(0, 160) + "..." : one;
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
