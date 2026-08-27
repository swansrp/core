package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.kernel.utils.FuncUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: AgentExploreTools
 * Description: 资产生成期暴露给 LLM 的只读数据探索工具（langchain4j function calling）。
 * 每次生成任务构造一个实例：作用域限 Agent 已选表（骨架实体），列名白名单 + 行数上限防注入防拖库；
 * 连接经 {@link ConnSupplier} 每次执行时从池借、用完即还（池借前校验活性，长会话/长思考轮闲置
 * 不再复用被服务端掐死的死连接——2026-08-23 自主会话实证：任务期独占单连接闲置 628s 被
 * wait_timeout 掐线，后续 19 轮全部 Connection is closed）；工具均为单语句只读，无跨语句
 * 事务状态，借还零成本。所有出参为紧凑 JSON 字符串，单元格值截断 100 字符控制上下文膨胀；
 * stopChecker 非空时每次工具调用前检查停止信号
 *
 * @author Sharp
 * @since 2026/8/19
 */
@Slf4j
public class AgentExploreTools {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 采样行数上限（sample_rows） */
    private static final int SAMPLE_ROWS_LIMIT = 20;
    /** GROUP BY 码值行数上限（group_by_field） */
    private static final int GROUP_BY_LIMIT = 50;
    /** 单元格值最大长度（超长截断，控制上下文） */
    private static final int CELL_MAX_LEN = 100;
    /** 探索类工具调用次数上限（防模型死循环刷库，超限后工具拒绝执行；自管循环 20 轮、每轮可多工具，预算相应放宽） */
    private static final int MAX_EXPLORE_CALLS = 60;
    /** 探索预算软警告阈值（80%）：触顶前一次性提醒收敛，避免 LLM 不知不觉打满配额 */
    private static final int SOFT_EXPLORE_LIMIT = MAX_EXPLORE_CALLS * 4 / 5;
    /** run_sql 返回行数上限（SELECT 包装子查询 LIMIT） */
    private static final int SQL_ROWS_LIMIT = 50;

    /** run_sql 写操作/危险关键字黑名单（词边界匹配，大小写不敏感；INTO 拦 SELECT INTO） */
    private static final Pattern SQL_FORBIDDEN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|REPLACE|GRANT|REVOKE|CALL|SET|LOCK|RENAME|LOAD|OUTFILE|INTO)\\b",
            Pattern.CASE_INSENSITIVE);
    /** run_sql 语句头白名单（只读类；WITH 仅允许引出 SELECT，见 validateReadOnly 内二次校验） */
    private static final Pattern SQL_READONLY_HEAD = Pattern.compile(
            "^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH)\\b", Pattern.CASE_INSENSITIVE);
    /** run_sql 表引用提取：三段式 db.tbl.col 优先整体命中，两段式 db.tbl 须命中选表 */
    private static final Pattern SQL_TABLE_REF = Pattern.compile("(\\w+)\\.(\\w+)(?:\\.(\\w+))?");
    /** WITH 语句体内任意位置的 SELECT（WITH 头语句必须最终引出 SELECT 才放行） */
    private static final Pattern SQL_SELECT_ANY = Pattern.compile("\\bSELECT\\b", Pattern.CASE_INSENSITIVE);
    /** run_sql CTE 名提取：`WITH [RECURSIVE] name AS (` 及后续 `, name AS (`（MySQL 不支持 CTE 列清单，无需考虑） */
    private static final Pattern SQL_CTE_NAME = Pattern.compile(
            "(?:\\bWITH\\b(?:\\s+RECURSIVE)?|,)\\s*(\\w+)\\s+AS\\s*\\(", Pattern.CASE_INSENSITIVE);
    /** run_sql 禁止引用的系统库（与选表排除口径一致） */
    private static final Set<String> SQL_FORBIDDEN_SCHEMAS = new HashSet<>(java.util.Arrays.asList(
            "information_schema", "mysql", "sys", "__internal_schema"));

    /** 连接供给器：每次工具执行时取（池模式下 get 即借前校验，死连接被池逐出换新），
     *  用完即还（close=归还池） */
    private final ConnSupplier connSupplier;
    private final String agentCode;
    private final List<EntityDef> entities;
    /** 注册回调：由生成服务实现（校验+采样+合入 domains），工具侧只透传 */
    private final PairRegistrar pairRegistrar;
    /** 过程日志上报：每次工具调用推一条到进度记录，前端进度窗可见（可为 null） */
    private final Consumer<String> logSink;
    /** 事实摘录上报（跨阶段交接台账，可为 null）：表结构/码值分布/SQL 结果行数等探索核实成功的事实，
     *  由编排层收集注入后续子会话（解析链核实 → 维护链/兜底链直接采信，禁止重复探索） */
    private final Consumer<String> factSink;
    /** 停止检查（可为 null）：任务被用户停止时工具拒绝执行，配合引擎轮头 shouldStop 双保险收口 */
    private final BooleanSupplier stopChecker;

    /** 探索类工具调用计数（register_code_label_pair 是产出动作，不计入） */
    private int exploreCalls;
    /** 预算软警告是否已发过（一次性，避免每次调用都刷屏） */
    private boolean budgetWarned;

    /** 码值配对注册回调：返回注册结果描述（新增维度名或跳过原因） */
    public interface PairRegistrar {
        String register(String entityName, String codeField, String labelField);
    }

    /** 连接供给器：每次 get 借一条新连接、用完即还；允许抛受检 SQLException
     *  （DataSource::getConnection 等池引用可直接以方法引用传入，包装层无需包一层 try） */
    @FunctionalInterface
    public interface ConnSupplier {
        Connection get() throws java.sql.SQLException;
    }

    /** 数据探索链路失败熔断规则集（三类：列校验/表范围/只读守卫，阈值 3）：
     *  失败文案出自本工具，规则也内聚在此；各链路注册方式
     *  opt.setFailureBreaker(new AgentFailureBreaker(exploreFailureRules()))，
     *  机制（计数/一次性触发/指令注入）在框架 AgentFailureBreaker+ToolAgentRunner */
    public static java.util.List<com.bidr.llm.agent.AgentFailureBreaker.Rule> exploreFailureRules() {
        return java.util.Arrays.asList(
                com.bidr.llm.agent.AgentFailureBreaker.Rule.of("列不存在",
                        "unknown column|列不存在|不在实体.*字段清单", 3,
                        "禁止继续凭猜测写列名：先调用 describe_table 核实目标表真实列名后再重新组织查询/公式。"),
                com.bidr.llm.agent.AgentFailureBreaker.Rule.of("表不在选表范围",
                        "不在本 Agent 选表范围|禁止引用系统库", 3,
                        "数据源仅限本 Agent 已选表（骨架实体清单），换表无法解决：改用已选表组织口径，"
                                + "或直接基于已有信息产出结论并说明局限。"),
                com.bidr.llm.agent.AgentFailureBreaker.Rule.of("只读守卫拒绝",
                        "仅允许只读|仅允许单条语句|检测到写操作", 3,
                        "run_sql 只接受单条只读 SELECT/SHOW/DESC/EXPLAIN 且表名用 db.tbl 全名："
                                + "停止尝试写操作或多语句拼接，按约束重写。"));
    }

    public AgentExploreTools(ConnSupplier connSupplier, String agentCode, List<EntityDef> entities,
                             PairRegistrar pairRegistrar, Consumer<String> logSink, BooleanSupplier stopChecker) {
        this(connSupplier, agentCode, entities, pairRegistrar, logSink, stopChecker, null);
    }

    public AgentExploreTools(ConnSupplier connSupplier, String agentCode, List<EntityDef> entities,
                             PairRegistrar pairRegistrar, Consumer<String> logSink, BooleanSupplier stopChecker,
                             Consumer<String> factSink) {
        this.connSupplier = connSupplier;
        this.agentCode = agentCode;
        this.entities = entities;
        this.pairRegistrar = pairRegistrar;
        this.logSink = logSink;
        this.stopChecker = stopChecker;
        this.factSink = factSink;
    }

    /** 借连接（每次工具执行一次）：供给器返回 null 时收口为可读异常文案，不裸抛 NPE */
    private Connection borrowConn() throws java.sql.SQLException {
        Connection c = connSupplier == null ? null : connSupplier.get();
        if (c == null) {
            throw new java.sql.SQLException("数据源连接不可用");
        }
        return c;
    }

    /** 上报一条过程日志（前端进度窗滚动展示，让用户看见 LLM 在调什么工具） */
    private void report(String msg) {
        if (logSink != null) {
            logSink.accept(msg);
        }
    }

    /** 停止检查点：返回 null 放行；任务停止时返回拒绝文本（LLM 收到后不再调用，引擎轮头同步收口） */
    private String stopGuard() {
        if (stopChecker != null && stopChecker.getAsBoolean()) {
            report("停止请求已收到，工具拒绝执行（任务收口中）");
            return "{\"error\":\"任务已被用户停止，禁止再调用任何工具\"}";
        }
        return null;
    }

    /** 单条事实摘录上限（台账体量控制） */
    private static final int FACT_MAX_LEN = 240;

    /** 事实摘录上报（超长截断）：null sink 或空行直接忽略 */
    private void recordFact(String line) {
        if (factSink == null || line == null || line.isEmpty()) {
            return;
        }
        factSink.accept(line.length() > FACT_MAX_LEN ? line.substring(0, FACT_MAX_LEN) + "…" : line);
    }

    /** 长文本截断（日志只留前 120 字符，避免进度记录膨胀） */
    private static String brief(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 120) + "..." : one;
    }

    @Tool("查询某张已选表的字段清单：字段名、类型、注释。参数 table 为完整表名（如 db.tbl）")
    public String describeTable(@P("完整表名，格式 db.tbl，必须来自实体骨架") String table) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' describe_table: {}", agentCode, table);
        report("工具 describe_table：查看表结构 " + table);
        EntityDef entity = requireTable(table);
        if (exploreCalls++ >= MAX_EXPLORE_CALLS) {
            reportLimitHit("describe_table");
            return "{\"error\":\"探索次数已达上限，请直接输出最终 JSON\"}";
        }
        StringBuilder sb = new StringBuilder("{\"table\":\"").append(entity.getTable())
                .append("\",\"display_name\":\"").append(jsonEscape(entity.getDisplayName()))
                .append("\",\"fields\":[");
        boolean first = true;
        for (EntityDef.EntityFieldDef field : entity.getFields()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"name\":\"").append(jsonEscape(field.getName()))
                    .append("\",\"type\":\"").append(jsonEscape(field.getType()))
                    .append("\",\"comment\":\"").append(jsonEscape(field.getDisplayName())).append("\"}");
        }
        StringBuilder fl = new StringBuilder();
        for (EntityDef.EntityFieldDef field : entity.getFields()) {
            if (fl.length() > 0) {
                fl.append(',');
            }
            fl.append(field.getName());
        }
        recordFact("[describeTable] " + entity.getTable() + " 字段：" + fl);
        return budgetWarnPrefix() + sb.append("]}").toString();
    }

    @Tool("采样某张已选表的前 N 行内容（最多20行，只读）。用于观察字段真实取值形态")
    public String sampleRows(@P("完整表名，格式 db.tbl") String table,
                             @P("采样行数，可省略，默认10，最大20") Integer limit) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' sample_rows: {} limit={}", agentCode, table, limit);
        report("工具 sample_rows：采样 " + table + " 真实数据");
        EntityDef entity = requireTable(table);
        if (exploreCalls++ >= MAX_EXPLORE_CALLS) {
            reportLimitHit("sample_rows");
            return "{\"error\":\"探索次数已达上限，请直接输出最终 JSON\"}";
        }
        int rows = limit == null || limit <= 0 ? 10 : Math.min(limit, SAMPLE_ROWS_LIMIT);
        String sql = "SELECT * FROM " + entity.getTable() + " LIMIT " + rows;
        try (Connection c = borrowConn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> data = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    String s = v == null ? null : v.toString();
                    if (s != null && s.length() > CELL_MAX_LEN) {
                        s = s.substring(0, CELL_MAX_LEN) + "...";
                    }
                    row.put(md.getColumnLabel(i), s);
                }
                data.add(row);
            }
            report("sample_rows 执行完成：返回 " + data.size() + " 行");
            return budgetWarnPrefix() + "{\"rows\":" + toJson(data) + "}";
        } catch (Exception e) {
            log.warn("[LLM工具] Agent '{}' sample_rows 失败 {}.{}: {}", agentCode, table, rows, e.getMessage());
            report("sample_rows 执行失败：" + brief(e.getMessage()));
            return "{\"error\":\"采样失败: " + jsonEscape(e.getMessage()) + "\"}";
        }
    }

    @Tool("对已选表某字段执行 GROUP BY，返回全部码值及出现次数（最多50组，按次数降序）。" +
            "用于判断字段是否枚举、查看码值分布")
    public String groupByField(@P("完整表名，格式 db.tbl") String table,
                               @P("字段名，必须是该表真实存在的列") String field) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' group_by_field: {}.{}", agentCode, table, field);
        report("工具 group_by_field：统计 " + table + "." + field + " 码值分布");
        EntityDef entity = requireTable(table);
        requireColumn(entity, field);
        if (exploreCalls++ >= MAX_EXPLORE_CALLS) {
            reportLimitHit("group_by_field");
            return "{\"error\":\"探索次数已达上限，请直接输出最终 JSON\"}";
        }
        String sql = "SELECT `" + field + "` AS v, COUNT(*) AS n FROM " + entity.getTable() +
                " WHERE `" + field + "` IS NOT NULL GROUP BY `" + field + "` ORDER BY n DESC LIMIT " + GROUP_BY_LIMIT;
        try (Connection c = borrowConn(); PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            StringBuilder sb = new StringBuilder("{\"field\":\"").append(jsonEscape(field)).append("\",\"values\":[");
            StringBuilder fact = new StringBuilder();
            boolean first = true;
            int groups = 0;
            while (rs.next()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                groups++;
                String v = rs.getString("v");
                if (v != null && v.length() > CELL_MAX_LEN) {
                    v = v.substring(0, CELL_MAX_LEN) + "...";
                }
                sb.append("{\"value\":\"").append(jsonEscape(v)).append("\",\"count\":").append(rs.getLong("n")).append('}');
                if (groups <= 10) {
                    if (fact.length() > 0) {
                        fact.append('、');
                    }
                    fact.append(v == null ? "NULL" : v).append('×').append(rs.getLong("n"));
                }
            }
            report("group_by_field 执行完成：" + field + " 共 " + groups + " 组码值");
            recordFact("[groupByField] " + table + "." + field + " 码值：" + fact);
            return budgetWarnPrefix() + sb.append("]}").toString();
        } catch (Exception e) {
            log.warn("[LLM工具] Agent '{}' group_by_field 失败 {}.{}: {}", agentCode, table, field, e.getMessage());
            report("group_by_field 执行失败：" + brief(e.getMessage()));
            return "{\"error\":\"GROUP BY 失败: " + jsonEscape(e.getMessage()) + "\"}";
        }
    }

    @Tool("执行你自己写的一条只读 SQL（仅允许 SELECT/SHOW/DESC/EXPLAIN 及 WITH...SELECT，表名必须用已选表的 db.tbl 全名，"
            + "禁止表别名——真实表的 别名.列 形式列引用都会被直接拒绝，列引用一律写 db.tbl.col（WITH CTE 名除外）；"
            + "最多返回50行）。用于灵活探索：聚合统计、条件采样、分布核查等。只可查询不可修改数据")
    public String runSql(@P("一条只读 SQL 语句，表引用须用 db.tbl 全名") String sql) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' run_sql: {}", agentCode, sql);
        report("工具 run_sql：" + brief(sql));
        String stripped = stripSqlComments(sql);
        String check = validateReadOnly(stripped);
        if (check != null) {
            report("run_sql 被拒绝：" + check);
            return "{\"error\":\"" + jsonEscape(check) + "\"}";
        }
        if (exploreCalls++ >= MAX_EXPLORE_CALLS) {
            reportLimitHit("run_sql");
            return "{\"error\":\"探索次数已达上限，请直接输出最终 JSON\"}";
        }
        // SELECT/WITH 包装子查询限行；SHOW/DESC/EXPLAIN 结果天然有限直执
        String execSql = wrapWithRowLimit(stripped);
        try (Connection c = borrowConn(); PreparedStatement ps = c.prepareStatement(execSql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> data = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    String s = v == null ? null : v.toString();
                    if (s != null && s.length() > CELL_MAX_LEN) {
                        s = s.substring(0, CELL_MAX_LEN) + "...";
                    }
                    row.put(md.getColumnLabel(i), s);
                }
                data.add(row);
            }
            report("run_sql 执行完成：返回 " + data.size() + " 行");
            recordFact("[runSql] " + brief(stripped) + " → " + data.size() + " 行");
            return budgetWarnPrefix() + "{\"rows\":" + toJson(data) + "}";
        } catch (Exception e) {
            log.warn("[LLM工具] Agent '{}' run_sql 失败: {}", agentCode, e.getMessage());
            report("run_sql 执行失败：" + brief(e.getMessage()));
            String hint = unknownColumnHint(e.getMessage());
            return "{\"error\":\"SQL 执行失败: " + jsonEscape(e.getMessage())
                    + (hint.isEmpty() ? "" : "；" + jsonEscape(hint)) + "}";
        }
    }
    
    /** 兜底通道服务端执行：LLM 产出的最终 SELECT 经同一只读守卫校验后执行，
     *  包装子查询限行（与 run_sql 同上限）；返回行集（LinkedHashMap 保序，列名→字符串值，
     *  超长截断同工具口径）。守卫拒绝或执行失败抛 IllegalArgumentException（调用方回落失败应答） */
    public List<Map<String, Object>> runGuardedSelect(String sql) {
        String stripped = stripSqlComments(sql);
        String check = validateReadOnly(stripped);
        if (check != null) {
            throw new IllegalArgumentException("兜底 SQL 未通过只读守卫: " + check);
        }
        String execSql = wrapWithRowLimit(stripped);
        try (Connection c = borrowConn(); PreparedStatement ps = c.prepareStatement(execSql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<Map<String, Object>> data = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    Object v = rs.getObject(i);
                    String s = v == null ? null : v.toString();
                    if (s != null && s.length() > CELL_MAX_LEN) {
                        s = s.substring(0, CELL_MAX_LEN) + "...";
                    }
                    row.put(md.getColumnLabel(i), s);
                }
                data.add(row);
            }
            return data;
        } catch (Exception e) {
            throw new IllegalArgumentException("兜底 SQL 执行失败: " + e.getMessage(), e);
        }
    }

    @Tool("批量登记「编码字段↔业务名称字段」码值配对：后端逐对校验字段真实存在后全表 GROUP BY 采样真实映射，"
            + "成功则自动生成码值域与维度（与骨架确定性配对同口径）。"
            + "发现疑似枚举配对（如 type_code↔type_name、status↔status_name）时，"
            + "请把同一张表发现的所有疑似配对收集齐后一次调用全部登记，不要一对一对地调。"
            + "勿对高基数业务列（如合同号、项目编码）调用。"
            + "参数为 JSON 数组：[{\"entity\":\"实体名\",\"code_field\":\"编码字段\",\"label_field\":\"名称字段\"}, ...]")
    public String registerCodeLabelPairs(@P("JSON 数组，每项含 entity（骨架实体名非表名）、code_field（*_code/*_no/*_type/*_status 等）、"
            + "label_field（*_name/*_nm/*_mc 等）") String pairsJson) {
        String stop = stopGuard();
        if (stop != null) {
            return stop;
        }
        log.info("[LLM工具] Agent '{}' register_code_label_pairs(批量): {}", agentCode, brief(pairsJson));
        report("工具 register_code_label_pairs：批量登记码值配对 " + brief(pairsJson));
        JsonNode arr;
        try {
            arr = OM.readTree(pairsJson);
        } catch (Exception e) {
            return "{\"error\":\"参数不是合法 JSON，请传 [{\\\"entity\\\":...,\\\"code_field\\\":...,\\\"label_field\\\":...}] 数组\"}";
        }
        if (!arr.isArray() || arr.isEmpty()) {
            return "{\"error\":\"参数须为非空 JSON 数组，单对也用数组包一层\"}";
        }
        StringBuilder sb = new StringBuilder();
        int ok = 0;
        for (JsonNode p : arr) {
            String result = pairRegistrar.register(p.path("entity").asText(null),
                    p.path("code_field").asText(null), p.path("label_field").asText(null));
            if (result.startsWith("已登记")) {
                ok++;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append("- ").append(p.path("entity").asText("?"))
                    .append('.').append(p.path("code_field").asText("?"))
                    .append("↔").append(p.path("label_field").asText("?"))
                    .append(": ").append(result);
        }
        report("码值配对批量登记完成：" + ok + "/" + arr.size() + " 对成功");
        return "本次登记 " + ok + "/" + arr.size() + " 对：\n" + sb;
    }

    /** 探索预算触顶上报（防死循环的保险丝，非失败）：进度窗可见，LLM 端收到收口提示 */
    private void reportLimitHit(String tool) {
        report("工具 " + tool + "：探索次数达上限（" + MAX_EXPLORE_CALLS + " 次），已拒绝并要求 LLM 直接产出结果");
    }

    /** 探索预算软警告：达 80% 时一次性在工具结果前缀提醒收敛，引导 LLM 提前进入产出阶段 */
    private String budgetWarnPrefix() {
        if (!budgetWarned && exploreCalls >= SOFT_EXPLORE_LIMIT) {
            budgetWarned = true;
            report("探索预算已用 " + exploreCalls + "/" + MAX_EXPLORE_CALLS + "（80%），已提醒 LLM 收敛");
            return "【探索预算警告】已用 " + exploreCalls + "/" + MAX_EXPLORE_CALLS
                    + " 次，剩余预算有限：请停止非必要探索，优先用已掌握的信息直接产出资产，仅对产出必需的缺口做最后核实。\n";
        }
        return "";
    }

    /** run_sql 只读校验：返回 null 通过，否则返回拒绝原因（LLM 可见可自行纠正）。
     *  层层收紧：单语句 → 语句头白名单 → 危险关键字黑名单 → 表引用限已选表 */
    private String validateReadOnly(String sql) {
        if (FuncUtil.isEmpty(sql)) {
            return "SQL 不能为空";
        }
        String trimmed = sql.trim();
        String[] parts = trimmed.split(";");
        int count = 0;
        for (String p : parts) {
            if (FuncUtil.isNotEmpty(p.trim())) {
                count++;
            }
        }
        if (count > 1) {
            return "仅允许单条语句，请勿用分号拼接多条";
        }
        if (!SQL_READONLY_HEAD.matcher(trimmed).find()) {
            return "仅允许只读语句（SELECT/SHOW/DESC/EXPLAIN/WITH...SELECT 开头）";
        }
        if (SQL_FORBIDDEN.matcher(trimmed).find()) {
            return "检测到写操作/危险关键字，仅允许只读查询";
        }
        if (trimmed.toUpperCase().startsWith("WITH") && !SQL_SELECT_ANY.matcher(trimmed).find()) {
            return "WITH 语句必须最终引出 SELECT 查询";
        }
        // CTE 名集：两段式 cte.列 形式的虚表引用不算越权表（CTE 内部真实表照常逐一校验）
        Set<String> cteNames = new HashSet<>();
        Matcher cm = SQL_CTE_NAME.matcher(trimmed);
        while (cm.find()) {
            cteNames.add(cm.group(1).toLowerCase());
        }
        Set<String> allowed = new HashSet<>();
        Set<String> schemaPrefixes = new HashSet<>();
        for (EntityDef entity : entities) {
            allowed.add(entity.getTable().toLowerCase());
            int dot = entity.getTable().indexOf('.');
            if (dot > 0) {
                schemaPrefixes.add(entity.getTable().substring(0, dot).toLowerCase());
            }
        }
        Matcher m = SQL_TABLE_REF.matcher(trimmed);
        while (m.find()) {
            String schema = m.group(1);
            String table = m.group(2);
            // 两段均为纯数字的是数字字面量（如 26522802.86 / 1.5），非表引用，跳过
            if (m.group(3) == null && schema.matches("\\d+") && table.matches("\\d+")) {
                continue;
            }
            if (SQL_FORBIDDEN_SCHEMAS.contains(schema.toLowerCase())) {
                return "禁止引用系统库: " + schema;
            }
            // 两段式且首段为 CTE 名：CTE 虚表列引用，放行；与选表 schema 同名的 CTE 名不放行（
            // MySQL 会将 db.tbl 解析为真实库表，照旧走白名单，防借名绕过）
            if (m.group(3) == null && cteNames.contains(schema.toLowerCase())
                    && !schemaPrefixes.contains(schema.toLowerCase())) {
                continue;
            }
            String ref = schema.toLowerCase() + "." + table.toLowerCase();
            if (!allowed.contains(ref)) {
                return "表引用 " + schema + "." + table + " 不在本 Agent 选表范围，须用 db.tbl 全名且来自已选表";
            }
        }
        return null;
    }

    /** 限行包装：SELECT/WITH 语句外包一层子查询加 LIMIT（MySQL 8 允许子查询内 WITH）；其余原样直执 */
    private String wrapWithRowLimit(String stripped) {
        String head = stripped.trim().toUpperCase();
        return head.startsWith("SELECT") || head.startsWith("WITH")
                ? "SELECT * FROM (" + stripped + ") _t LIMIT " + SQL_ROWS_LIMIT
                : stripped;
    }

    /** 去 SQL 注释（斜杠星号块注释与 -- 行注释），避免注释内关键字绕过校验 */
    private String stripSqlComments(String sql) {
        if (FuncUtil.isEmpty(sql)) {
            return sql;
        }
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("--[^\\n]*", " ").trim();
    }

    /** 表名 → 骨架实体；不在选表范围直接拒绝（LLM 可见错误文本自行纠正） */
    private EntityDef requireTable(String table) {
        if (FuncUtil.isEmpty(table)) {
            throw new IllegalArgumentException("表名不能为空");
        }
        return entities.stream().filter(e -> table.equals(e.getTable()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "表 " + table + " 不在本 Agent 选表范围，可用实体见提示词骨架"));
    }

    /** 列名白名单校验：只允许骨架中真实存在的字段（杜绝任意 SQL 片段注入）；
     *  拼错时附编辑距离相近的候选列，避免 LLM 反复试错浪费轮次 */
    private void requireColumn(EntityDef entity, String field) {
        if (FuncUtil.isEmpty(field) || entity.getFields().stream().noneMatch(f -> field.equals(f.getName()))) {
            List<String> near = new ArrayList<>();
            if (FuncUtil.isNotEmpty(field)) {
                for (EntityDef.EntityFieldDef f : entity.getFields()) {
                    if (editDistance(field, f.getName()) <= 2) {
                        near.add(f.getName());
                    }
                    if (near.size() >= 3) {
                        break;
                    }
                }
            }
            throw new IllegalArgumentException("字段 " + field + " 不存在于表 " + entity.getTable()
                    + (near.isEmpty() ? "" : "，是否想写：" + String.join("、", near)));
        }
    }

    /** run_sql 报 Unknown column 时的纠错建议：取报错列名末段，在全部选表列中找编辑距离≤2 的候选 */
    private String unknownColumnHint(String errMsg) {
        if (errMsg == null) {
            return "";
        }
        Matcher m = Pattern.compile("Unknown column '([^']+)'", Pattern.CASE_INSENSITIVE).matcher(errMsg);
        if (!m.find()) {
            return "";
        }
        String bad = m.group(1);
        int dot = bad.lastIndexOf('.');
        String col = dot >= 0 ? bad.substring(dot + 1) : bad;
        List<String> hits = new ArrayList<>();
        for (EntityDef entity : entities) {
            for (EntityDef.EntityFieldDef f : entity.getFields()) {
                if (editDistance(col, f.getName()) <= 2) {
                    hits.add(entity.getTable() + "." + f.getName());
                }
                if (hits.size() >= 3) {
                    break;
                }
            }
            if (hits.size() >= 3) {
                break;
            }
        }
        return hits.isEmpty() ? "" : "是否想写：" + String.join("、", hits);
    }

    /** 列名纠错候选的编辑距离（Levenshtein，两个空滚动数组实现） */
    private static int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    /** 最小 JSON 序列化（仅 Map/List/String，值已按需截断） */
    @SuppressWarnings("unchecked")
    private String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (List<Object>) value) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(toJson(item));
            }
            return sb.append(']').toString();
        }
        if (value instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) value).entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(jsonEscape(String.valueOf(entry.getKey())))
                        .append("\":").append(toJson(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        return "\"" + jsonEscape(value.toString()) + "\"";
    }

    private String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
