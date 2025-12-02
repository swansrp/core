package com.bidr.kernel.mybatis.log;


import java.util.ArrayList;
import java.util.List;

/**
 * @author Sharp
 * @since 2025/12/2 10:54
 */

public class MybatisLogFormatter {

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
            // 解析参数：13(Long), 0(Long)
            String[] params = paramsStr.split(",");
            String result = sql;

            for (String param : params) {
                param = param.trim();
                if (param.isEmpty()) continue;

                // 提取值和类型：13(Long) -> 值=13, 类型=Long
                int typeStart = param.indexOf('(');
                if (typeStart == -1) continue;

                String value = param.substring(0, typeStart).trim();
                String type = param.substring(typeStart + 1, param.indexOf(')')).trim();

                // 根据类型格式化值
                String formattedValue = formatValue(value, type);

                // 替换第一个 ?
                result = result.replaceFirst("\\?", formattedValue);
            }

            return result;
        } catch (Exception e) {
            return sql;
        }
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
            for (int i = 0; i < row.size(); i++) {
                sb.append(wrap(row.get(i))).append(" | ");
            }
            sb.append("\n");
        }
        // 去除最后一个换行符
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    private static String wrap(String s) {
        if (s == null) return "";

        // BLOB
        if (s.startsWith("<<BLOB")) return "[BLOB]";

        // JSON 美化
        if ((s.startsWith("{") && s.endsWith("}"))
                || (s.startsWith("[") && s.endsWith("]"))) {
            return prettyJson(s, 0);
        }

        if (s.length() <= MAX_COL_WIDTH) return s;

        return s.substring(0, MAX_COL_WIDTH - 3) + "...";
    }

    private static String prettyJson(String text, int indent) {
        // 简化版 JSON pretty printer
        StringBuilder sb = new StringBuilder();
        int level = 0;
        boolean inString = false;

        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '"':
                    sb.append(ch);
                    inString = !inString;
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

