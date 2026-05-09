package com.bidr.kernel.mybatis.log;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * MyBatis SQL 日志格式化工具
 * 负责将 SQL 模板和参数拼接为完整的可执行 SQL，以及格式化查询结果为 Markdown 表格
 * 
 * @author Sharp
 * @since 2025/12/2 10:54
 */
public class MybatisLogFormatter {

    private static final Logger log = LoggerFactory.getLogger(MybatisLogFormatter.class);
    
    /** 固定列宽（可调） */
    private static final int MAX_COL_WIDTH = 30;

    /** JSON格式输出是否换行和缩进 */
    private static final String INDENT = "  ";

    /**
     * 将 SQL 和参数拼接成完整的可执行 SQL
     */
    public static String buildSql(String sql, String paramsStr) {
        if (sql == null || paramsStr == null || paramsStr.isEmpty()) {
            return sql;
        }

        try {
            // 智能解析参数，处理参数值中包含逗号的情况
            List<String> params = parseParameters(paramsStr);
            String result = sql;

            for (String param : params) {
                param = param.trim();
                if (param.isEmpty()) continue;

                // 检查是否是纯 null 值（无类型标注）
                if ("null".equalsIgnoreCase(param)) {
                    result = replaceFirstPlaceholder(result, "NULL");
                    continue;
                }

                // 提取值和类型：13(Long) -> 值=13, 类型=Long
                int typeStart = param.indexOf('(');
                if (typeStart == -1) {
                    // 如果没有类型标注，尝试作为普通值处理
                    String escapedValue = param.replace("'", "''");
                    result = replaceFirstPlaceholder(result, "'" + escapedValue + "'");
                    continue;
                }

                String value = param.substring(0, typeStart).trim();
                String type = param.substring(typeStart + 1, param.lastIndexOf(')')).trim();

                // 根据类型格式化值
                String formattedValue = formatValue(value, type);

                // 替换第一个 ? 占位符
                result = replaceFirstPlaceholder(result, formattedValue);
            }

            return result;
        } catch (Exception e) {
            log.debug("Failed to build complete SQL: {}", e.getMessage());
            return sql;
        }
    }

    /**
     * 智能解析参数列表，处理参数值中包含逗号的情况
     * 例如: "abc(String), def(String)" 不会被错误分割
     */
    private static List<String> parseParameters(String paramsStr) {
        List<String> params = new ArrayList<>();
        int bracketDepth = 0;
        int start = 0;

        for (int i = 0; i < paramsStr.length(); i++) {
            char c = paramsStr.charAt(i);
            if (c == '(') {
                bracketDepth++;
            } else if (c == ')') {
                bracketDepth--;
            } else if (c == ',' && bracketDepth == 0) {
                // 只有在括号外层的逗号才是参数分隔符
                params.add(paramsStr.substring(start, i).trim());
                start = i + 1;
            }
        }
        // 添加最后一个参数
        if (start < paramsStr.length()) {
            params.add(paramsStr.substring(start).trim());
        }

        return params;
    }

    /**
     * 替换第一个 SQL 参数占位符 ?
     * 避免替换字符串常量中的 ?
     */
    private static String replaceFirstPlaceholder(String sql, String value) {
        // 查找不在单引号内的第一个 ?
        boolean inString = false;
        int questionMarkIndex = -1;
        
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                inString = !inString;
            } else if (c == '?' && !inString) {
                questionMarkIndex = i;
                break;
            }
        }
        
        if (questionMarkIndex == -1) {
            return sql;
        }
        
        return sql.substring(0, questionMarkIndex) + value + sql.substring(questionMarkIndex + 1);
    }

    /**
     * 根据类型格式化值
     */
    private static String formatValue(String value, String type) {
        if ("null".equalsIgnoreCase(value)) {
            return "NULL";
        }

        // 字符串类型加单引号
        if (type.contains("String") || type.contains("Date") || type.contains("Time")) {
            return "'" + value.replace("'", "''") + "'";
        }

        // 数字类型直接返回
        return value;
    }

    public static String formatMarkdown(List<String> columns, List<List<String>> rows) {
        if (columns == null || rows == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // header
        sb.append("| ");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(columns.get(i)).append(" | ");
        }
        sb.append("\n");

        // separator
        sb.append("| ");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(repeat("-", 3)).append(" | ");
        }
        sb.append("\n");

        // rows
        for (List<String> row : rows) {
            sb.append("| ");
            for (int i = 0; i < columns.size(); i++) {
                // 处理行列数不匹配的情况
                String cellValue = (i < row.size()) ? row.get(i) : "";
                sb.append(wrap(cellValue)).append(" | ");
            }
            sb.append("\n");
        }
        // 去除最后一个换行符
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String wrap(String s) {
        if (s == null) return "";

        // BLOB
        if (s.startsWith("<<BLOB")) return "[BLOB]";

        // JSON 美化
        if ((s.startsWith("{") && s.endsWith("}"))
                || (s.startsWith("[") && s.endsWith("]"))) {
            return prettyJson(s);
        }

        if (s.length() <= MAX_COL_WIDTH) return s;

        return s.substring(0, MAX_COL_WIDTH - 3) + "...";
    }

    private static String prettyJson(String text) {
        // 简化版 JSON pretty printer
        StringBuilder sb = new StringBuilder();
        int level = 0;
        boolean inString = false;
        boolean escaped = false;

        for (char ch : text.toCharArray()) {
            if (escaped) {
                sb.append(ch);
                escaped = false;
                continue;
            }
            
            switch (ch) {
                case '\\':
                    sb.append(ch);
                    escaped = true;
                    break;
                case '"':
                    sb.append(ch);
                    if (!escaped) {
                        inString = !inString;
                    }
                    break;
                case '{':
                case '[':
                    sb.append(ch);
                    if (!inString) {
                        sb.append("\n").append(repeat(INDENT, ++level));
                    }
                    break;
                case '}':
                case ']':
                    if (!inString) {
                        sb.append("\n").append(repeat(INDENT, --level));
                    }
                    sb.append(ch);
                    break;
                case ',':
                    sb.append(ch);
                    if (!inString) {
                        sb.append("\n").append(repeat(INDENT, level));
                    }
                    break;
                default:
                    sb.append(ch);
            }
        }

        return sb.toString();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}

