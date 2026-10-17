package com.bidr.forge.utils;

import java.util.regex.Pattern;

/**
 * SQL 标识符/别名相关工具。
 * <p>
 * 用于清洗历史数据或配置中的“带引号 token”，例如：'alias'、`alias`、"alias"、`'alias'`。
 * </p>
 */
public final class SqlIdentifierUtil {

    private SqlIdentifierUtil() {
    }

    /**
     * 清洗标识符：去掉最外层的单引号/反引号/双引号（可多层）。
     * <p>
     * 典型场景：column_alias 落库时被写成 'managerAppointStatus'，导致后续映射/拼 SQL 出错。
     * </p>
     */
    public static String sanitizeQuotedIdentifier(String s) {
        if (s == null) {
            return null;
        }
        String x = s.trim();
        boolean changed = true;
        while (changed && x.length() >= 2) {
            changed = false;
            if ((x.startsWith("'") && x.endsWith("'"))
                    || (x.startsWith("`") && x.endsWith("`"))
                    || (x.startsWith("\"") && x.endsWith("\""))) {
                x = x.substring(1, x.length() - 1).trim();
                changed = true;
            }
        }
        return x;
    }

    /**
     * 安全标识符字符集：字母、数字、下划线、$、点、连字符与中文。
     * <p>
     * 请求侧字段名（排序字段、条件字段、行维度/度量/透视列标识）最终会被拼进 SQL 的
     * {@code `...`} 引用位置，因此必须排除：<b>反引号</b>（直接闭合引用）、<b>反斜杠</b>
     * （MySQL 默认模式下能转义掉闭合的反引号，使引用提前不生效）、以及引号/空白/括号/
     * 分号等一切可改写语句结构的字符。连字符与点保留：透视列标识常见形如 2024-07，
     * 而 {@code alias.field} 也是合法写法，它们在引用内部无法逃逸。
     * </p>
     */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_$.\\-\\u4e00-\\u9fa5]+$");

    /**
     * 判断字符串是否可作为拼入 SQL 的标识符
     * <p>
     * 与 {@link #sanitizeQuotedIdentifier} 的区别：后者只剥最外层成对引号（清理落库脏数据），
     * 对内嵌的反引号无约束力，不能用作防注入；本方法采用白名单字符集直接判合法性。
     * </p>
     *
     * @param s 待校验标识符
     * @return true=仅含安全字符且非空
     */
    public static boolean isSafeIdentifier(String s) {
        // 单个连字符安全（透视列标识常见 2024-07），但成对出现就是行注释起点：
        // Dataset 的 ORDER BY 把映射值不加引号直接拼在 LIMIT 之前，`a--` 会注释掉后续子句改变语义
        return s != null && !s.contains("--") && SAFE_IDENTIFIER.matcher(s).matches();
    }
}

