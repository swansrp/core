package com.bidr.forge.utils;

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
}

