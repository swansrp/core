package com.bidr.forge.utils;

import com.bidr.kernel.utils.FuncUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dataset 列备注处理：
 * 1) 支持从 SQL 中解析 SELECT 列后的行内备注：-- 备注 / # 备注（包括注释放在逗号后或列后）
 * 2) 没有备注时可生成一个英文备注兜底
 */
public class DatasetColumnRemarkUtil {

    // 简单的别名捕获正则（用于从单个 select 项提取 alias）
    // 兼容 AS 'alias' / AS `alias` / AS "alias" / AS alias
    private static final Pattern ALIAS_PATTERN = Pattern.compile("(?i)\\s+AS\\s+((?:'[^']+'|`[^`]+`|\"[^\"]+\"|\\w+))\\s*$");

    private DatasetColumnRemarkUtil() {
    }

    /**
     * 从 SQL 文本里提取 列别名/表达式 -> 备注 的映射。
     * 支持注释放在列后或逗号后（例如：`col AS alias, -- 注释`）。
     */
    public static Map<String, String> parseSelectColumnRemarks(String sql) {
        Map<String, String> remarkMap = new LinkedHashMap<>();
        if (FuncUtil.isEmpty(sql)) {
            return remarkMap;
        }

        String selectPart = extractSelectPart(sql);
        if (FuncUtil.isEmpty(selectPart)) {
            return remarkMap;
        }
        // 防御性：避免静态检查误报 NPE
        if (selectPart == null) {
            return remarkMap;
        }

        // 遍历 selectPart，按逗号分割（注意括号/字符串）并捕获“逗号后/列后”的行内注释。
        // 关键约束：注释如果出现在逗号后（expr, -- remark），必须绑定到刚结束的 expr，不能误绑到下一个 expr。
        int len = selectPart.length();
        StringBuilder buf = new StringBuilder();
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        String lastCompletedExpr = null;

        for (int i = 0; i < len; i++) {
            char c = selectPart.charAt(i);

            // 处理字符串引号（不在字符串里才识别注释/逗号/括号）
            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                buf.append(c);
                continue;
            }
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                buf.append(c);
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote) {
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    if (depth > 0) depth--;
                }

                // 顶层逗号：结束一个 select 项。结束后立即看“逗号后的注释”，并绑定到刚结束的表达式。
                if (c == ',' && depth == 0) {
                    String expr = buf.toString().trim();
                    if (!expr.isEmpty()) {
                        lastCompletedExpr = expr;
                    }
                    buf.setLength(0);

                    // 逗号后可能是空白 + 注释（同一行），例如："col AS a, -- 备注  nextCol AS b"
                    int j = i + 1;
                    while (j < len) {
                        char cj = selectPart.charAt(j);
                        if (cj == ' ' || cj == '\t') {
                            j++;
                            continue;
                        }
                        // 逗号后换行，说明这行没有注释
                        if (cj == '\n' || cj == '\r') {
                            break;
                        }
                        // 识别 -- 注释
                        if (cj == '-' && j + 1 < len && selectPart.charAt(j + 1) == '-') {
                            int k = j + 2;
                            StringBuilder comment = new StringBuilder();
                            while (k < len) {
                                char ck = selectPart.charAt(k);
                                // 行内注释到行尾为止；但为了兼容“全写一行”的情况，也允许遇到下一个列的明显起始（字母/下划线）前用空格分隔。
                                if (ck == '\n' || ck == '\r') {
                                    break;
                                }
                                comment.append(ck);
                                k++;
                            }

                            String commentStr = sanitizeInlineComment(comment.toString());
                            // 如果这一行后面还紧跟了下一个字段（没有换行），尝试截断，不把后续字段吞进注释
                            commentStr = truncateCommentIfLooksLikeNextColumn(commentStr);

                            String key = extractAliasOrExpr(lastCompletedExpr);
                            if (FuncUtil.isNotEmpty(key) && FuncUtil.isNotEmpty(commentStr)) {
                                remarkMap.put(key, commentStr);
                            }
                            i = k - 1; // 跳过注释到行尾
                            break;
                        }
                        // 识别 # 注释
                        if (cj == '#') {
                            int k = j + 1;
                            StringBuilder comment = new StringBuilder();
                            while (k < len && selectPart.charAt(k) != '\n' && selectPart.charAt(k) != '\r') {
                                comment.append(selectPart.charAt(k));
                                k++;
                            }

                            String commentStr = sanitizeInlineComment(comment.toString());
                            commentStr = truncateCommentIfLooksLikeNextColumn(commentStr);

                            String key = extractAliasOrExpr(lastCompletedExpr);
                            if (FuncUtil.isNotEmpty(key) && FuncUtil.isNotEmpty(commentStr)) {
                                remarkMap.put(key, commentStr);
                            }
                            i = k - 1;
                            break;
                        }

                        // 不是注释就停止（例如马上开始了下一个字段）
                        break;
                    }

                    continue;
                }

                // 列后注释（没有逗号也可能有：lastCol AS a -- remark）
                if (c == '-' && i + 1 < len && selectPart.charAt(i + 1) == '-') {
                    int j = i + 2;
                    StringBuilder comment = new StringBuilder();
                    while (j < len && selectPart.charAt(j) != '\n' && selectPart.charAt(j) != '\r') {
                        comment.append(selectPart.charAt(j));
                        j++;
                    }
                    String commentStr = sanitizeInlineComment(comment.toString());
                    commentStr = truncateCommentIfLooksLikeNextColumn(commentStr);

                    // 关键修复：列后注释必须绑定到“当前列”（buf）
                    String expr = buf.toString().trim();
                    String key = extractAliasOrExpr(expr);
                    if (FuncUtil.isNotEmpty(key) && FuncUtil.isNotEmpty(commentStr)) {
                        remarkMap.put(key, commentStr);
                    }
                    i = j - 1;
                    continue;
                }

                if (c == '#') {
                    int j = i + 1;
                    StringBuilder comment = new StringBuilder();
                    while (j < len && selectPart.charAt(j) != '\n' && selectPart.charAt(j) != '\r') {
                        comment.append(selectPart.charAt(j));
                        j++;
                    }

                    String commentStr = sanitizeInlineComment(comment.toString());
                    commentStr = truncateCommentIfLooksLikeNextColumn(commentStr);

                    // 关键修复：列后注释必须绑定到“当前列”（buf）
                    String expr = buf.toString().trim();
                    String key = extractAliasOrExpr(expr);
                    if (FuncUtil.isNotEmpty(key) && FuncUtil.isNotEmpty(commentStr)) {
                        remarkMap.put(key, commentStr);
                    }
                    i = j - 1;
                    continue;
                }
            }

            buf.append(c);
        }

        // 结束后，如果还有缓冲区内容且未被注释标记，仍然可能没有注释，我们不强制生成map项（备注为空由generateEnglishRemark处理）
        // 另外，考虑注释可能出现在最后一项之后的情况（没有换行），上面的注释处理已经尝试关联到 lastCompletedExpr

        return remarkMap;
    }

    private static String sanitizeInlineComment(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        // 去掉末尾常见分隔符，避免出现“主键id,”这种
        s = s.replaceAll("[,，;；.。:：\\s]+$", "").trim();
        return s;
    }

    /**
     * 当 SQL 写成一行时："col AS a, --备注 col2 AS b"，严格来说这不是标准 SQL，
     * 但很多人会这么写。这里做一个容错：如果注释里看起来包含了下一个字段的起始，就截断。
     */
    private static String truncateCommentIfLooksLikeNextColumn(String comment) {
        if (FuncUtil.isEmpty(comment)) return comment;

        // 典型 next column 模式：\s+<ident>.<ident> 或 \s+<ident>\s+AS\s+<ident>
        // 我们从 comment 中找到第一次出现这些模式的位置，然后截断。
        Pattern nextColPattern = Pattern.compile("(?i)\\s+[A-Za-z_][\\w$]*\\s*\\.\\s*[A-Za-z_][\\w$]*|\\s+[A-Za-z_][\\w$]*\\s+AS\\s+[A-Za-z_][\\w$]*");
        Matcher m = nextColPattern.matcher(comment);
        if (m.find()) {
            String truncated = comment.substring(0, m.start()).trim();
            return sanitizeInlineComment(truncated);
        }
        return comment;
    }

    // 决定将注释关联到哪一个key：优先使用 lastCompletedExpr 的 alias（如果存在），否则使用 bufExpr 的 alias/表达式
    private static String determineKeyForExpr(String lastCompletedExpr, String bufExpr) {
        String candidate;
        if (FuncUtil.isNotEmpty(lastCompletedExpr)) {
            candidate = extractAliasOrExpr(lastCompletedExpr);
            if (FuncUtil.isNotEmpty(candidate)) return candidate;
        }
        if (FuncUtil.isNotEmpty(bufExpr)) {
            candidate = extractAliasOrExpr(bufExpr);
            if (FuncUtil.isNotEmpty(candidate)) return candidate;
        }
        return null;
    }

    // 从一个 select 项文本中提取 alias（去外层引号）或返回整个表达式
    private static String extractAliasOrExpr(String expr) {
        if (FuncUtil.isEmpty(expr)) return null;
        // 先试着找 AS alias
        Matcher m = ALIAS_PATTERN.matcher(expr);
        if (m.find()) {
            String a = m.group(1);
            return sanitizeIdentifierToken(a);
        }
        // 否则尝试以最后的标识符作为别名（例如: `col alias`）
        String trimmed = expr.trim();
        String[] parts = trimmed.split("\\s+");
        if (parts.length > 1) {
            String last = parts[parts.length - 1];
            // 如果最后一部分看起来像标识符（可带引号），则返回
            if (last.matches("(?:'[^']+'|`[^`]+`|\"[^\"]+\"|\\w+)") ) {
                return sanitizeIdentifierToken(last);
            }
        }
        // 最后返回整个表达式
        return trimmed;
    }

    private static String stripQuotes(String s) {
        if (FuncUtil.isEmpty(s)) return s;
        return SqlIdentifierUtil.sanitizeQuotedIdentifier(s);
    }

    private static String sanitizeIdentifierToken(String token) {
        return SqlIdentifierUtil.sanitizeQuotedIdentifier(token);
    }

    private static String extractSelectPart(String sql) {
        // 很保守：只截取 SELECT 与 FROM 第一次出现之间
        // 注意：复杂 SQL（子查询、FROM 出现在字符串里）可能不完美，但对“配置 SQL”基本够用
        if (FuncUtil.isEmpty(sql)) return null;
        String upper = sql.toUpperCase();
        int idxSelect = upper.indexOf("SELECT");
        if (idxSelect < 0) {
            return null;
        }
        int idxFrom = upper.indexOf("FROM", idxSelect + 6);
        if (idxFrom < 0) {
            return sql.substring(idxSelect + 6);
        }
        return sql.substring(idxSelect + 6, idxFrom);
    }

    /**
     * 生成英文备注兜底：优先基于 alias（驼峰拆词），否则基于表达式。
     */
    public static String generateEnglishRemark(String aliasOrExpr) {
        if (FuncUtil.isEmpty(aliasOrExpr)) {
            return "";
        }
        String s = stripQuotes(aliasOrExpr.trim());

        // 如果是 SQL 表达式，尝试取最后一个标识符（a.b.c -> c）
        if (s.contains(".")) {
            s = s.substring(s.lastIndexOf('.') + 1);
        }
        // 去掉括号等
        s = s.replaceAll("[()\\[\\]]", "");

        // 下划线转空格
        if (s.contains("_")) {
            s = s.replace('_', ' ');
            return toTitleCase(s);
        }

        // 驼峰拆词
        String spaced = s.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        return toTitleCase(spaced);
    }

    /**
     * 根据拆解后的英文生成中文备注（兜底）。
     * 策略：先调用 generateEnglishRemark 得到词组（例如 "User Name"），然后逐词映射为中文（内置词典），
     * 未命中词典时使用英文词作为回退，最终拼接为中文字符串（无空格）。
     */
    public static String generateChineseRemark(String aliasOrExpr) {
        String eng = generateEnglishRemark(aliasOrExpr);
        if (FuncUtil.isEmpty(eng)) return "";

        // 先尝试整体匹配：去掉空格和下划线并小写，例如 userName -> username, user_no -> userno
        String normalized = aliasOrExpr == null ? "" : aliasOrExpr.trim();
        normalized = stripQuotes(normalized);
        normalized = normalized.replaceAll("\\s+", "").replaceAll("_", "").toLowerCase();
        if (FuncUtil.isNotEmpty(normalized)) {
            String whole = enToZhMap().get(normalized);
            if (whole != null) return whole;
        }

        // 再尝试把 eng 拼接后小写匹配（例如 "User Name" -> "username")
        String engNoSpace = eng.replaceAll("\\s+", "").toLowerCase();
        if (FuncUtil.isNotEmpty(engNoSpace)) {
            String whole = enToZhMap().get(engNoSpace);
            if (whole != null) return whole;
        }

        // 按词拆分映射
        String[] parts = eng.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (FuncUtil.isEmpty(p)) continue;
            String key = p.toLowerCase();
            String mapped = enToZhMap().get(key);
            if (mapped != null) {
                sb.append(mapped);
            } else {
                // 回退：如果英文词比较短，则直接大写首字母并加入（避免完全空）
                sb.append(toTitleCase(p));
            }
        }
        return sb.toString();
    }

    // 简单词典，常见英文标识符到中文的映射；可按需扩充
    private static Map<String, String> enToZhMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", "主键");
        m.put("pk", "主键");
        m.put("name", "名称");
        m.put("username", "用户名");
        m.put("user", "用户");
        m.put("no", "编号");
        m.put("number", "编号");
        m.put("age", "年龄");
        m.put("date", "日期");
        m.put("time", "时间");
        m.put("amount", "金额");
        m.put("amt", "金额");
        m.put("price", "价格");
        m.put("code", "编码");
        m.put("status", "状态");
        m.put("type", "类型");
        m.put("phone", "手机号");
        m.put("mobile", "手机号");
        m.put("email", "邮箱");
        m.put("title", "标题");
        m.put("desc", "描述");
        m.put("description", "描述");
        m.put("address", "地址");
        m.put("count", "数量");
        m.put("total", "总计");
        m.put("sum", "合计");
        m.put("create", "创建");
        m.put("created", "创建时间");
        m.put("update", "更新");
        m.put("updated", "更新时间");
        m.put("userno", "工号");
        return m;
    }

    private static String toTitleCase(String s) {
        if (FuncUtil.isEmpty(s)) {
            return s;
        }
        String[] parts = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (FuncUtil.isEmpty(p)) {
                continue;
            }
            if (i > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }
}
