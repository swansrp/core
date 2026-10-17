package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.adapter.StatisticPayloadAdapter;
import com.bidr.insight.smartquery.exec.SmartQueryJdbcExecutor;
import com.bidr.insight.smartquery.model.GenResult;
import com.bidr.insight.smartquery.model.QueryRows;
import com.bidr.insight.smartquery.model.SmartQueryResult;
import com.bidr.insight.smartquery.model.ValidationResult;
import com.bidr.insight.smartquery.semantic.SmartQueryParser;
import com.bidr.insight.smartquery.sqlgen.RowPolicyUserContext;
import com.bidr.insight.smartquery.sqlgen.SqlGenException;
import com.bidr.insight.smartquery.sqlgen.SqlGenerator;
import com.bidr.insight.smartquery.validate.SemanticQueryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Title: SmartQueryService
 * Description: 智能问数引擎编排：parse → validate（§6 安全阀）→ sqlgen → execute → payload。
 * 校验不通过即终止；运行期对语义层资产只读（SKILL.md §4.1 硬规则），
 * 缺失/错误一律走失败路径，不得放宽校验。chartMode=ranking 时产出扁平 payload，
 * 其余产出标准嵌套 payload，前端 ChartCard 可直接渲染
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartQueryService {

    private final SmartQueryParser parser;
    private final SemanticQueryValidator validator;
    private final SqlGenerator generator;
    private final SmartQueryJdbcExecutor executor;
    private final StatisticPayloadAdapter adapter;

    /** 仅校验 + 生成 SQL，不执行（dry-run，供双跑比对/预览） */
    public SmartQueryResult dryRun(String semanticQueryJson) {
        return run(semanticQueryJson, false, null, true, null);
    }

    /** 带用户上下文 dry-run（行权限渲染期注入） */
    public SmartQueryResult dryRun(String semanticQueryJson, RowPolicyUserContext userCtx) {
        return run(semanticQueryJson, false, null, true, userCtx);
    }

    /**
     * 完整执行。chartMode=ranking 时产出扁平 payload，其余产出标准嵌套 payload；
     * chartMode 为 null 时不产出 payload
     */
    public SmartQueryResult run(String semanticQueryJson, boolean execute, String chartMode) {
        return run(semanticQueryJson, execute, chartMode, true, null);
    }

    /** translateEntityFields=false：实体字段码列不翻译（穿透明细走 portal 字典列自翻译）；
     * userCtx 非空时行权限渲染期注入（fail-closed） */
    public SmartQueryResult run(String semanticQueryJson, boolean execute, String chartMode,
                                boolean translateEntityFields, RowPolicyUserContext userCtx) {
        SmartQueryResult r = new SmartQueryResult();
        r.setStage("parse");

        SmartQueryParser.ParseResult parsed;
        try {
            parsed = parser.parse(semanticQueryJson);
        } catch (IllegalArgumentException e) {
            r.setErrorMessage(e.getMessage());
            r.getErrors().add(ValidationResult.issue("input", "", e.getMessage()));
            return r;
        }

        r.setStage("validate");
        ValidationResult vr = validator.validate(parsed.getQuery(), parsed.getRaw());
        r.setValid(vr.isValid());
        r.setErrors(vr.getErrors());
        r.setWarnings(vr.getWarnings());
        if (!vr.isValid()) {
            return r;
        }

        r.setStage("generate");
        GenResult gen;
        try {
            gen = generator.generate(parsed.getQuery(), userCtx);
        } catch (SqlGenException e) {
            r.setValid(false);
            r.setErrorMessage(e.getMessage());
            r.getErrors().add(ValidationResult.issue("sqlgen", "", e.getMessage()));
            return r;
        }
        r.setSql(gen.getSql());
        r.setParams(gen.getParams());
        r.setColumns(gen.getColumns());
        r.setNotes(gen.getNotes());

        if (!execute) {
            return r;
        }

        r.setStage("execute");
        if (!executor.isConfigured()) {
            r.setErrorMessage("未配置问数数据源（数据源管理页面维护，或 smartquery.datasource.url 兜底），仅返回 SQL");
            return r;
        }
        QueryRows rows = executor.execute(gen.getSql(), gen.getParams());
        r.setRows(generator.translateResult(gen, rows.getRows(), translateEntityFields));

        if (chartMode != null) {
            r.setPayload("ranking".equals(chartMode)
                    ? adapter.toRankingPayload(gen, rows)
                    : adapter.toStandardPayload(gen, rows));
        }
        return r;
    }
}
