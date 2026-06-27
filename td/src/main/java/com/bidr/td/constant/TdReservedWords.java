package com.bidr.td.constant;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * TDengine SQL 保留关键字集合。
 * <p>
 * 在 DDL/DML 中直接使用这些关键字作为列名或标签名会导致语法错误，
 * 必须用反引号转义。框架层（BaseTdSchema / BaseTdRepo）调用
 * {@link #escape(String)} 自动处理，实体类无需手动加反引号。
 * </p>
 *
 * @author Sharp
 */
public final class TdReservedWords {

    private TdReservedWords() {
    }

    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            // TDengine 常见保留字（小写匹配）
            "ablock", "ablocks", "abort", "account", "accounts", "add", "aggregate",
            "all", "alter", "and", "as", "asc", "attach", "before", "between",
            "bigint", "binary", "bitand", "bitor", "block", "blocks", "bool",
            "break", "buffer", "by", "cache", "case", "cast", "change", "cluster",
            "clusters", "colon", "column", "columns", "comma", "concat", "concat_ws",
            "configs", "connection", "connection_id", "connections", "copy", "count",
            "create", "current_user", "database", "databases", "db_interval",
            "deflate", "delete", "delimiter", "desc", "describe", "devices",
            "dnode", "dnodes", "dnodes", "double", "drop", "duration", "else",
            "end", "eq", "exists", "explain", "file", "fill", "first", "float",
            "for", "from", "function", "functions", "ge", "gid", "grant", "grants",
            "group", "having", "hostname", "if", "in", "inner", "insert", "int",
            "integer", "interval", "into", "is", "join", "keep", "kill", "last",
            "last_row", "lfill", "like", "limit", "linear", "local", "localhost",
            "lp", "lshift", "lt", "match", "maxrows", "merge", "meta", "minrows",
            "minus", "mnode", "mnodes", "modify", "neq", "next", "none", "not",
            "now", "null", "nulls", "of", "offset", "on", "or", "order", "outer",
            "partition", "pass", "percent", "plus", "prev", "qnode", "qnodes",
            "qstart", "range", "ratio", "rblock", "rblocks", "repsma", "reset",
            "respect", "restore", "rows", "rseg", "rshift", "schema", "schemas",
            "select", "session", "set", "show", "single", "sliding", "slimit",
            "smallint", "smavalue", "soffset", "stable", "stables", "star",
            "stddev", "sum", "sys", "table", "tables", "tables", "tag", "tags",
            "tbname", "temp", "template", "templates", "through", "time",
            "timestamp", "timezone", "to", "today", "top", "trigger", "trim",
            // ts 作为 TDengine 默认时间戳列名，实际无需转义
            "tsma", "tsmavalues", "union", "update", "use", "user", "users",
            "using", "value", "values", "variable", "variables", "vgroup",
            "vgroups", "vnode", "vnodes", "wal", "where", "with"
    ));

    /**
     * 如果 name 是保留字，则用反引号包裹；否则原样返回。
     * <p>
     * 已包含反引号的 name 原样返回，不重复包裹。
     * </p>
     *
     * @param name 列名或标签名
     * @return 转义后的名称
     */
    public static String escape(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        // 已有反引号，不重复包裹
        if (name.startsWith("`") && name.endsWith("`")) {
            return name;
        }
        // 检查是否是保留字（不区分大小写）
        if (RESERVED.contains(name.toLowerCase())) {
            return "`" + name + "`";
        }
        return name;
    }
}
