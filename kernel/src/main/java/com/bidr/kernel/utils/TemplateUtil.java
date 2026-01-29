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
            Pattern.compile("\\$\\{\\s*(\\w+)\\s*(?:\\?\\:\\s*([^}]+))?\\s*}");

    /**
     * 支持语法
     * ${key}
     * <p>
     * ${key ?: defaultValue}
     *
     * @param template 模版
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
            String defaultValue = matcher.group(2);

            Object value = variables.get(key);
            String replacement;

            if (value != null) {
                replacement = value.toString();
            } else if (defaultValue != null) {
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
