package com.bidr.insight.smartquery.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ValidationResult
 * Description: semantic_query 校验结果（对应 Python validate_query.py 输出）：
 * valid + errors（阻断）+ warnings（放行但需在回答中标注）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
public class ValidationResult {

    private boolean valid;

    private List<Issue> errors = new ArrayList<>();

    private List<Issue> warnings = new ArrayList<>();

    /** 单条校验问题（rule 为 SKILL.md 规则号，如 §6.2.1） */
    @Data
    public static class Issue {
        private String rule;
        private String field;
        private String message;

        public Issue(String rule, String field, String message) {
            this.rule = rule;
            this.field = field;
            this.message = message;
        }
    }

    public static Issue issue(String rule, String field, String message) {
        return new Issue(rule, field, message);
    }
}
