package com.bidr.insight.smartquery.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.insight.smartquery.derive.IndicatorDeriver;
import com.bidr.insight.smartquery.derive.InteractionMerger;
import com.bidr.insight.smartquery.derive.PortalConfigDeriver;
import com.bidr.insight.smartquery.derive.StatisticResConverter;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.MetricDef;
import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.SemanticQuery;
import com.bidr.insight.smartquery.model.SmartQueryResult;
import com.bidr.insight.smartquery.semantic.SemanticLayer;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.semantic.SmartQueryParser;
import com.bidr.insight.smartquery.service.SmartQueryService;
import com.bidr.insight.smartquery.sqlgen.RowPolicyUserContext;
import com.bidr.insight.smartquery.vo.SmartQueryPlanReq;
import com.bidr.insight.smartquery.vo.SmartQueryPlanRes;
import com.bidr.insight.smartquery.vo.SmartQueryStatisticReq;
import com.bidr.insight.smartquery.vo.SmartQueryTableReq;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: SmartQueryPlanController
 * Description: 智能问数三端点（§45 协议，全部无状态）：
 * plan（semantic_query → 校验 + SQL + portalConfig/indicator/queryContext 同源推导）、
 * advanced/statistic（图表自拉数：queryContext 全量重校验 + 条件 JSON 增量合并 +
 * 执行，响应与现有 statistic 接口同构）、advanced/query（穿透明细：semantic_query
 * 切 list 模式 + 穿透条件合并 + 分页）。
 * 路径与现有 portal 通路对齐：前端取数 url 后拼 /advanced/statistic、/advanced/query，
 * 故端点沿用这两个后缀，前端条件构建/穿透/渲染管线零改动（仅附带 queryContext）。
 * queryContext 为不可信载荷：每次请求全量重校验，篡改最多换来拒绝。
 * 行级权限（P2）：三端点均从登录态构建用户上下文传入引擎，row-policies 资产
 * 渲染期注入 WHERE（对载荷不可见；配了策略而无登录态 fail-closed 拒绝）。
 * 多 Agent 隔离：semantic_query.agent 指定目标 Agent（缺省为 default），
 * 端点入口 bind / finally clear，期间语义层与数据源按 agentCode 路由
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Api(tags = "智能问数-SmartQuery")
@RequiredArgsConstructor
@RestController
@RequestMapping("/web/insight/smart-query")
public class SmartQueryPlanController {

    private static final ObjectMapper OM = new ObjectMapper();

    /** 引擎 LIMIT 上限（与 SqlGenerator 一致） */
    private static final int MAX_LIMIT = 1000;

    private final SmartQueryService smartQueryService;
    private final SmartQueryParser parser;
    private final SemanticLayerRegistry layers;
    private final StatisticResConverter converter;
    private final PortalConfigDeriver portalConfigDeriver;
    private final IndicatorDeriver indicatorDeriver;
    private final InteractionMerger interactionMerger;

    @ApiOperation("问数图表规划：semantic_query → 校验 + SQL + portalConfig + indicator + queryContext")
    @PostMapping("/plan")
    public SmartQueryPlanRes plan(@RequestBody SmartQueryPlanReq req) {
        if (req.getSemanticQuery() == null) {
            throw new NoticeException("semanticQuery 不能为空");
        }
        JsonNode query = unwrap(req.getSemanticQuery());
        layers.bind(agentOf(query));
        try {
            String queryContext = writeContext(query);
            SmartQueryResult r = smartQueryService.dryRun(queryContext, RowPolicyUserContext.fromCurrent());

            SmartQueryPlanRes res = new SmartQueryPlanRes();
            res.setValid(r.isValid());
            res.setErrors(r.getErrors());
            res.setWarnings(r.getWarnings());
            res.setErrorMessage(r.getErrorMessage());
            res.setSql(r.getSql());
            res.setParams(r.getParams());
            res.setNotes(r.getNotes());
            if (!r.isValid()) {
                return res;
            }

            SemanticQuery sq = parseContext(queryContext).getQuery();
            String chartMode = req.getChartMode() != null && !req.getChartMode().isEmpty()
                    ? req.getChartMode() : indicatorDeriver.inferChartMode(sq);
            String title = req.getTitle() != null && !req.getTitle().isEmpty()
                    ? req.getTitle() : defaultTitle(sq);

            res.setPortalConfig(portalConfigDeriver.derive(sq, r.getColumns(), title));
            res.setDicts(portalConfigDeriver.deriveDicts(r.getColumns()));
            SmartQueryPlanRes.ChartItem item = new SmartQueryPlanRes.ChartItem();
            item.setIndicator(indicatorDeriver.derive(sq, chartMode, title));
            item.setQueryContext(queryContext);
            res.setCharts(Collections.singletonList(item));
            return res;
        } finally {
            layers.clear();
        }
    }

    @ApiOperation("图表自拉数：queryContext 重校验 + 条件增量合并 + 执行（响应同现有 statistic）")
    @PostMapping("/advanced/statistic")
    public List<StatisticRes> statistic(@RequestBody SmartQueryStatisticReq req) {
        SemanticQuery sq = parseContext(req.getQueryContext()).getQuery();
        layers.bind(sq.getAgent());
        try {
            interactionMerger.mergeStatistic(sq, req);
            // 响应形状分支与 plan 同源：chartMode 由合并后的 semantic_query 推断
            String chartMode = indicatorDeriver.inferChartMode(sq);
            String engineMode = "rankingBar".equals(chartMode) ? "ranking" : "standard";
            SmartQueryResult r = smartQueryService.run(writeContext(sq), true, engineMode, true,
                    RowPolicyUserContext.fromCurrent());
            checkResult(r);
            return converter.convert(r.getPayload());
        } finally {
            layers.clear();
        }
    }

    @ApiOperation("穿透明细：semantic_query 切 list 模式 + 穿透条件合并 + 分页（实体字段码列保留码值，由 portal 字典列渲染翻译）")
    @PostMapping("/advanced/query")
    public Page<Map<String, Object>> table(@RequestBody SmartQueryTableReq req) {
        SemanticQuery sq = parseContext(req.getQueryContext()).getQuery();
        layers.bind(sq.getAgent());
        try {
            interactionMerger.merge(sq, req);
            toListMode(sq);

            long currentPage = req.getCurrentPage() == null || req.getCurrentPage() < 1 ? 1 : req.getCurrentPage();
            long pageSize = req.getPageSize() == null || req.getPageSize() < 1 ? 20 : req.getPageSize();
            // 引擎仅支持 LIMIT：取 currentPage*pageSize 上限内执行，内存切片返回当前页
            sq.setLimit((int) Math.min(currentPage * pageSize, MAX_LIMIT));
            SmartQueryResult r = smartQueryService.run(writeContext(sq), true, null, false,
                    RowPolicyUserContext.fromCurrent());
            checkResult(r);

            List<Map<String, Object>> all = rowsToMaps(r);
            int offset = (int) ((currentPage - 1) * pageSize);
            int from = Math.min(offset, all.size());
            int to = (int) Math.min(all.size(), (long) offset + pageSize);
            Page<Map<String, Object>> page = new Page<>(currentPage, pageSize, all.size());
            page.setRecords(new ArrayList<>(all.subList(from, to)));
            return page;
        } finally {
            layers.clear();
        }
    }

    // ── 私有辅助 ─────────────────────────────────────────────

    /** queryContext 解析：非法 JSON/结构即拒绝（载荷不可信） */
    private SmartQueryParser.ParseResult parseContext(String queryContext) {
        if (queryContext == null || queryContext.isEmpty()) {
            throw new NoticeException("queryContext 不能为空");
        }
        try {
            return parser.parse(queryContext);
        } catch (IllegalArgumentException e) {
            throw new NoticeException("queryContext 非法: " + e.getMessage());
        }
    }

    /** 校验/执行失败 → 明确错误，不降级 */
    private void checkResult(SmartQueryResult r) {
        if (!r.isValid()) {
            String msg = r.getErrors().isEmpty() ? "查询校验未通过" : r.getErrors().get(0).getMessage();
            throw new NoticeException(msg);
        }
        if (r.getErrorMessage() != null && !r.getErrorMessage().isEmpty()) {
            throw new NoticeException(r.getErrorMessage());
        }
    }

    /** {semantic_query:{...}} 包裹 → 裸对象（queryContext 恒为裸 semantic_query） */
    private JsonNode unwrap(JsonNode root) {
        return root.has("semantic_query") && root.get("semantic_query").isObject()
                ? root.get("semantic_query") : root;
    }

    /** 取 semantic_query.agent（缺省/空 → null 即默认 Agent），用于语义层/数据源路由 */
    private String agentOf(JsonNode query) {
        JsonNode node = query.path("agent");
        String agent = node.isTextual() ? node.asText().trim() : null;
        return agent == null || agent.isEmpty() ? null : agent;
    }

    private String writeContext(Object nodeOrQuery) {
        try {
            return OM.writeValueAsString(nodeOrQuery);
        } catch (Exception e) {
            throw new NoticeException("semantic_query 序列化失败: " + e.getMessage());
        }
    }

    /** 缺省标题：首个指标中文名 + 维度措辞 */
    private String defaultTitle(SemanticQuery sq) {
        if (sq.getMetrics() != null && !sq.getMetrics().isEmpty()) {
            MetricDef m = layers.current().metricMap().get(sq.getMetrics().get(0));
            if (m != null && m.getDisplayName() != null) {
                return m.getDisplayName();
            }
            return sq.getMetrics().get(0);
        }
        return "查询结果";
    }

    /**
     * metric 查询 → 明细（list）模式（§45.3）：实体取首指标源表对应实体，
     * 输出列 = 实体缺省展示字段 + 属于本实体的维度列；去聚合（清 metrics/having/window），
     * 穿透条件已在 filters 中。list 查询本身再做一次全量校验兜底
     */
    private void toListMode(SemanticQuery sq) {
        if ("list".equals(sq.queryTypeOrDefault())) {
            return;
        }
        if (sq.getMetrics() == null || sq.getMetrics().isEmpty()) {
            throw new NoticeException("查询上下文缺少指标，无法切换明细模式");
        }
        MetricDef m = layers.current().metricMap().get(sq.getMetrics().get(0));
        if (m == null || m.getSourceTable() == null) {
            throw new NoticeException("指标 '" + sq.getMetrics().get(0) + "' 无明细源表");
        }
        String entName = layers.current().tableToEntity().get(m.getSourceTable());
        EntityDef ent = entName == null ? null : layers.current().entityMap().get(entName);
        if (ent == null) {
            throw new NoticeException("指标源表未注册为实体，不支持穿透明细");
        }

        List<String> fields = new ArrayList<>();
        for (String f : ent.getDisplayFields() == null ? new ArrayList<String>() : ent.getDisplayFields()) {
            // 缺省展示字段可能含历史遗留的清单外字段，过滤掉保证 list 校验可过
            if (isEntityField(ent, f)) {
                fields.add(f);
            }
        }
        for (String dim : sq.getDimensions() == null ? new ArrayList<String>() : sq.getDimensions()) {
            DimensionDef d = layers.current().dimensionMap().get(dim);
            if (d == null || d.getExpression() == null || !entName.equals(layers.current().dimEntityOfOrNull(dim))) {
                continue;
            }
            String col = SemanticLayer.splitExpr(d.getExpression())[1];
            if (isEntityField(ent, col) && !fields.contains(col)) {
                fields.add(col);
            }
        }

        sq.setQueryType("list");
        sq.setEntity(entName);
        sq.setFields(fields.isEmpty() ? null : fields);
        sq.setMetrics(null);
        sq.setDimensions(null);
        sq.setHaving(null);
        sq.setWindow(null);
        sq.setScopeFilter(null);
        sq.setTime(null);
        sq.setOrderBy(null);
    }

    private boolean isEntityField(EntityDef ent, String col) {
        if (ent.getFields() == null) {
            return false;
        }
        for (EntityDef.EntityFieldDef f : ent.getFields()) {
            if (col.equals(f.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 执行行 → Map（alias → 值）；实体字段码列保留码值（portal SELECT 字典列自翻译） */
    private List<Map<String, Object>> rowsToMaps(SmartQueryResult r) {
        List<Map<String, Object>> all = new ArrayList<>();
        if (r.getRows() == null) {
            return all;
        }
        for (List<Object> row : r.getRows()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 0; i < r.getColumns().size() && i < row.size(); i++) {
                map.put(r.getColumns().get(i).getAlias(), row.get(i));
            }
            all.add(map);
        }
        return all;
    }
}
