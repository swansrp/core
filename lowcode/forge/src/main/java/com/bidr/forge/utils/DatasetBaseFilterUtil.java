package com.bidr.forge.utils;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.ConditionVariableUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dataset 常驻过滤谓词（sys_dataset.base_filter）校验与参数化工具。
 * <p>
 * base_filter 是 WHERE 片段（仅谓词），每次查询与请求条件按 "(base) AND (req)" 拼接；
 * '${currentYear}' 等条件变量必须写在单引号内，运行期解析为命名参数绑定，不做字符串替换。
 * 部门/客户等主体类变量不在此支持——行级数据隔离请走行权限。
 * </p>
 *
 * @author Sharp
 * @since 2026/10/8
 */
public class DatasetBaseFilterUtil {

    /**
     * 谓词片段不允许出现的子句：parseCondExpression 会静默截掉尾部子句（a=1 GROUP BY b 只返回 a=1），
     * 所以 GROUP BY/ORDER BY 等必须靠关键词拒绝，不能只依赖解析
     */
    private static final Pattern FORBIDDEN_CLAUSE = Pattern.compile("(?i)\\b(GROUP\\s+BY|ORDER\\s+BY|HAVING|LIMIT|OFFSET|UNION)\\b");
    /**
     * 带引号的变量字面量：'${currentYear}'
     */
    private static final Pattern TOKEN_LITERAL = Pattern.compile("'(\\$\\{[^']*\\})'");
    private static final Pattern STRING_LITERAL = Pattern.compile("'[^']*'");
    private static final Pattern VARIABLE_FORM = Pattern.compile("\\$\\{[^}]*\\}");

    private DatasetBaseFilterUtil() {
    }

    /**
     * 保存校验：非法内容抛 NoticeException 阻止入库；null/空白归一化为 null。
     */
    public static String validate(String baseFilter) {
        if (FuncUtil.isEmpty(baseFilter)) {
            return null;
        }
        String fragment = baseFilter.trim();
        if (FuncUtil.isEmpty(fragment)) {
            return null;
        }

        // 文本检查先剔除字符串字面量，避免 'GROUP BY'、'a#b' 这类合法字面量误伤
        String noLiterals = STRING_LITERAL.matcher(fragment).replaceAll("''");
        if (noLiterals.contains("--") || noLiterals.contains("#") || noLiterals.contains("/*")) {
            throwInvalid("常驻条件不允许包含注释");
        }
        if (noLiterals.contains(";")) {
            throwInvalid("常驻条件不允许分号，仅支持单个过滤谓词");
        }
        if (FORBIDDEN_CLAUSE.matcher(noLiterals).find()) {
            throwInvalid("常驻条件只允许过滤谓词，不允许 GROUP BY/ORDER BY/HAVING/LIMIT/UNION");
        }
        if (VARIABLE_FORM.matcher(noLiterals).find()) {
            throwInvalid("条件变量必须写在单引号内，如 dy = '${currentYear}'");
        }

        Matcher tokens = TOKEN_LITERAL.matcher(fragment);
        while (tokens.find()) {
            String token = tokens.group(1);
            if (!ConditionVariableUtil.isVariable(token)) {
                throwInvalid("常驻条件变量 " + token + " 不合法，支持 ${currentYear} 等日期变量与 ${now:pattern}；" +
                        "部门/客户等主体类变量请走行权限");
            }
        }
        // 合法 token 剔除后仍残留 ${ 的（如 '${currentYear' 残缺形态）：语法上是普通字面量，
        // 但运行期不会解析、查询静默返回 0 行，必须保存期拦下
        String withoutTokens = TOKEN_LITERAL.matcher(fragment).replaceAll("");
        if (withoutTokens.contains("${")) {
            throwInvalid("存在书写不完整的条件变量，${ 必须构成完整的 '${...}' 变量，如 dy = '${currentYear}'");
        }

        try {
            // 严格模式：宽松模式会静默吞掉 x = = 1 这类垃圾语法，保存期必须报错而非查询期
            CCJSqlParserUtil.parseCondExpression(fragment, false);
        } catch (JSQLParserException e) {
            throwInvalid("常驻条件语法错误：" + e.getMessage());
        }
        return fragment;
    }

    /**
     * 运行期把带引号的变量字面量替换为命名参数占位符（:param_N），解析值写入 parameters。
     * 非变量内容原样返回。
     */
    public static String toBindableSql(String baseFilter, Map<String, Object> parameters) {
        if (FuncUtil.isEmpty(baseFilter)) {
            return null;
        }
        Matcher tokens = TOKEN_LITERAL.matcher(baseFilter);
        if (!tokens.find()) {
            return baseFilter;
        }
        tokens.reset();
        StringBuffer sb = new StringBuffer();
        while (tokens.find()) {
            String token = tokens.group(1);
            if (!ConditionVariableUtil.isVariable(token)) {
                // 库内数据可能绕过保存校验（如手工改库），运行期宁可报错也不静默绑定字面量
                throwInvalid("常驻条件变量 " + token + " 不合法");
            }
            String key = "param_" + parameters.size();
            parameters.put(key, toBindableValue(ConditionVariableUtil.resolve(token)));
            tokens.appendReplacement(sb, Matcher.quoteReplacement(":" + key));
        }
        tokens.appendTail(sb);
        return sb.toString();
    }

    /**
     * 纯数字解析结果按整型绑定（dy = '2026' → dy = 2026），其余按字符串
     */
    private static Object toBindableValue(String resolved) {
        if (resolved != null && resolved.matches("-?\\d{1,9}")) {
            try {
                return Integer.valueOf(resolved);
            } catch (NumberFormatException ignore) {
                // 超出整型范围按字符串绑定
            }
        }
        return resolved;
    }

    private static void throwInvalid(String msg) {
        Validator.assertException(ErrCodeSys.PA_PARAM_FORMAT, msg);
    }
}
