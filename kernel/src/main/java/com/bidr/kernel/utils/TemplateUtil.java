package com.bidr.kernel.utils;


import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Sharp
 * @since 2026/1/29 11:26
 *
 */

public class TemplateUtil {
    private static final Pattern PATTERN =
            Pattern.compile("\\$\\{\\s*(\\w+)\\s*(?:=\\s*([^?]+)\\?\\s*([^:}]+)\\s*:\\s*([^}]+)|\\?\\:\\s*([^}]+))?\\s*}");

    /**
     * 支持语法
     * ${key}                                  - 简单替换
     * ${key ?: defaultValue}                  - 带默认值
     * ${key=value?trueExpr:falseExpr}         - 比较分支：key的值等于value时输出trueExpr，否则falseExpr
     *
     * @param template  模版
     * @param variables 参数
     * @return 解析后的字符串
     */
    public static String parse(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (variables == null) {
            variables = new HashMap<>();
        }

        Matcher matcher = PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String compareValue = matcher.group(2);
            String trueExpr = matcher.group(3);
            String falseExpr = matcher.group(4);
            String defaultValue = matcher.group(5);

            Object value = variables.get(key);
            String replacement;

            if (compareValue != null) {
                // 比较分支: ${key=value?trueExpr:falseExpr}
                String valStr = value != null ? value.toString() : "";
                if (compareValue.equals(valStr)) {
                    replacement = trueExpr != null ? trueExpr.trim() : "";
                } else {
                    replacement = falseExpr != null ? falseExpr.trim() : "";
                }
            } else if (value != null) {
                // 简单替换: ${key}
                replacement = value.toString();
            } else if (defaultValue != null) {
                // 默认值: ${key?:defaultValue}
                replacement = defaultValue.trim();
            } else {
                replacement = "";
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(sb);
        return sb.toString();
    }
}
