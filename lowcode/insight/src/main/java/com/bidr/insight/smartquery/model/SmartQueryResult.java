package com.bidr.insight.smartquery.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Title: SmartQueryResult
 * Description: 智能问数单次执行结果：校验结论 + 生成 SQL + 执行行 + statistic payload。
 * 各阶段产物按执行进度填充，调用方按 stage 取用
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
public class SmartQueryResult {

    /** parse → validate → generate → execute */
    private String stage;

    private boolean valid;

    private List<ValidationResult.Issue> errors = new ArrayList<>();

    private List<ValidationResult.Issue> warnings = new ArrayList<>();

    /** 参数化 SQL（? 占位符），回答展示用（SKILL.md 强制展示 SQL） */
    private String sql;

    private List<Object> params = new ArrayList<>();

    private List<GenResult.ColumnInfo> columns = new ArrayList<>();

    private List<String> notes = new ArrayList<>();

    /** 码值→标签翻译后的展示行 */
    private List<List<Object>> rows;

    /** statistic payload（chartMode=ranking 为扁平形状，否则标准嵌套形状） */
    private List<Map<String, Object>> payload;

    private String errorMessage;
}
