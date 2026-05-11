package com.bidr.td.sync;

import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TdAdvancedQueryParser {

    private static final Logger log = LoggerFactory.getLogger(TdAdvancedQueryParser.class);

    /**
     * 将 AdvancedQuery（嵌套 AND/OR）解析为 WHERE 子句和参数列表
     */
    public ParsedCondition parse(AdvancedQuery condition) {
        if (condition == null) {
            return new ParsedCondition("1=1", new ArrayList<>());
        }
        // 叶子节点：普通条件
        if (condition.getProperty() != null) {
            return buildSingleCondition(condition);
        }
        // 非叶子节点：递归处理 conditionList
        if (condition.getConditionList() != null && !condition.getConditionList().isEmpty()) {
            String operator = AdvancedQuery.AND.equals(condition.getAndOr()) ? "AND" : "OR";
            List<String> clauseParts = new ArrayList<>();
            List<Object> allParams = new ArrayList<>();
            for (AdvancedQuery child : condition.getConditionList()) {
                ParsedCondition childResult = parse(child);
                clauseParts.add(childResult.getClause());
                allParams.addAll(childResult.getParams());
            }
            String clause = "(" + String.join(" " + operator + " ", clauseParts) + ")";
            return new ParsedCondition(clause, allParams);
        }
        return new ParsedCondition("1=1", new ArrayList<>());
    }

    private ParsedCondition buildSingleCondition(AdvancedQuery cond) {
        String property = cond.getProperty();
        // Validate property to prevent SQL injection
        validateIdentifier(property, "property");
        List<?> value = cond.getValue();
        Integer relation = cond.getRelation();
        if (property == null || value == null || value.isEmpty()) {
            return new ParsedCondition("1=1", new ArrayList<>());
        }

        Object singleValue = value.get(0);
        String clause;
        List<Object> params = new ArrayList<>();

        if (PortalConditionDict.EQUAL.getValue().equals(relation)) {
            clause = property + " = ?";
            params.add(singleValue);
        } else if (PortalConditionDict.NOT_EQUAL.getValue().equals(relation)) {
            clause = property + " <> ?";
            params.add(singleValue);
        } else if (PortalConditionDict.GREATER.getValue().equals(relation)) {
            clause = property + " > ?";
            params.add(singleValue);
        } else if (PortalConditionDict.GREATER_EQUAL.getValue().equals(relation)) {
            clause = property + " >= ?";
            params.add(singleValue);
        } else if (PortalConditionDict.LESS.getValue().equals(relation)) {
            clause = property + " < ?";
            params.add(singleValue);
        } else if (PortalConditionDict.LESS_EQUAL.getValue().equals(relation)) {
            clause = property + " <= ?";
            params.add(singleValue);
        } else if (PortalConditionDict.LIKE.getValue().equals(relation)) {
            clause = property + " LIKE ?";
            params.add(singleValue);
        } else if (PortalConditionDict.IN.getValue().equals(relation)) {
            StringBuilder sb = new StringBuilder(property + " IN (");
            for (int i = 0; i < value.size(); i++) {
                sb.append("?");
                if (i < value.size() - 1) sb.append(", ");
                params.add(value.get(i));
            }
            sb.append(")");
            clause = sb.toString();
        } else {
            throw new IllegalStateException("Unknown relation type: " + relation);
        }
        return new ParsedCondition(clause, params);
    }

    /**
     * Validate identifier to prevent SQL injection.
     */
    private void validateIdentifier(String name, String label) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
        if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(label + " '" + name + "' is not a valid identifier");
        }
    }

    public static class ParsedCondition {
        private final String clause;
        private final List<Object> params;

        public ParsedCondition(String clause, List<Object> params) {
            this.clause = clause;
            this.params = params;
        }

        public String getClause() { return clause; }
        public List<Object> getParams() { return params; }
    }
}
