package com.bidr.kernel.utils;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Title: ConditionVariableUtil
 * Description: 查询条件内置动态变量解析（后端唯一正源）
 * <p>
 * 前端 erp-view framework/utils/common.ts 的 resolveDynamicVariable 持有同一张 token 表，
 * 条件值以 ${xxx} 形式书写（默认条件、列筛选项默认值、行权限条件共用本解析器）。
 * <p>
 * 双端口径已对齐（2026-09-24 前端修复历史缺陷后）：
 * 1. 时区固定 GMT+8 —— 前端旧版用 toISOString 按 UTC 日历取值（东八区每天 08:00 前取到“昨天”），
 *    现已改为同样按东八区日历取值，不再随用户机器时区漂移；
 * 2. ${currentDateTime} 输出 "yyyy-MM-dd HH:mm:ss" —— 前端旧版输出 ISO 带 T 格式，
 *    MySQL 无法直接与 DATETIME 列比较，现已同步为可比较格式。
 * <p>
 * 变量整体匹配：值必须恰好等于 "${token}" 才参与替换（与前端一致），不做子串替换，
 * 避免把用户填写的字面值（如备注里的 "${currentYear} 年度"）误解析。
 * <p>
 * 通用格式变量 {@code ${now:<DateTimeFormatter pattern>}}（如 {@code ${now:yyyyMMdd}}、
 * {@code ${now:yyyy-MM-dd HH:mm:ss}}）：按东八区当前时刻格式化输出，支持任意合法 pattern，
 * 固定 token（currentMonth=yyyyMM 等）覆盖不到的格式无需再加新 token。
 * pattern 非法时不解析原样返回（与未知 token 同口径）；前端 common.ts formatNow 实现同一 Java token 子集。
 * </p>
 *
 * @author Sharp
 * @since 2026/9/23
 */
public class ConditionVariableUtil {

    /**
     * 条件变量统一按东八区日历取值，不随服务器时区漂移
     */
    private static final ZoneId ZONE = ZoneId.of("GMT+8");
    private static final String PREFIX = "${";
    private static final String SUFFIX = "}";

    public static final String CURRENT_YEAR = "currentYear";
    public static final String CURRENT_MONTH = "currentMonth";
    public static final String CURRENT_DAY = "currentDay";
    public static final String CURRENT_DATE_TIME = "currentDateTime";
    public static final String LAST_YEAR = "lastYear";
    public static final String NEXT_YEAR = "nextYear";
    public static final String LAST_MONTH = "lastMonth";
    public static final String NEXT_MONTH = "nextMonth";
    public static final String LAST_DAY = "lastDay";
    public static final String NEXT_DAY = "nextDay";

    /**
     * 通用格式变量前缀：{@code ${now:pattern}}，token 以 now: 开头即走 pattern 解析
     */
    public static final String NOW_PREFIX = "now:";

    /**
     * 已知 token 全集：供启动自检/校验配置值是否书写了不存在的变量
     */
    private static final Set<String> SUPPORTED_TOKENS = new HashSet<>(Arrays.asList(
            CURRENT_YEAR, CURRENT_MONTH, CURRENT_DAY, CURRENT_DATE_TIME,
            LAST_YEAR, NEXT_YEAR, LAST_MONTH, NEXT_MONTH, LAST_DAY, NEXT_DAY));

    /**
     * 主体类 token（由 authorization 层 resolveSubjectToken 解析，kernel 不做取值，
     * 仅登记名称供保存校验识别）
     */
    private static final Set<String> SUBJECT_TOKENS = new HashSet<>(Arrays.asList(
            "currentCustomerNumber", "currentDeptId", "currentGroupId",
            "currentDeptIds", "currentGroupIds"));

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ConditionVariableUtil() {
    }

    /**
     * 判断值是否为动态变量书写形式（${...}），是则由调用方交 resolve 解析
     * <p>合法形态两类：固定 token 在全集内，或 {@code now:pattern} 且 pattern 可编译（供保存校验）。</p>
     */
    public static boolean isVariable(String value) {
        if (!isVariableForm(value)) {
            return false;
        }
        String token = value.substring(PREFIX.length(), value.length() - SUFFIX.length());
        return SUPPORTED_TOKENS.contains(token) || isValidNowPattern(token);
    }

    /**
     * token 是否为合法的 {@code now:pattern} 形态（pattern 能被 DateTimeFormatter 编译）
     */
    public static boolean isValidNowPattern(String token) {
        if (FuncUtil.isEmpty(token) || !token.startsWith(NOW_PREFIX)) {
            return false;
        }
        try {
            DateTimeFormatter.ofPattern(token.substring(NOW_PREFIX.length()));
            return true;
        } catch (IllegalArgumentException e) {
            // 非法 pattern（如 ${now:年}）：不识别，保持原样不解析
            return false;
        }
    }

    /**
     * 解析条件值：命中内置变量 token 则替换为当前时间值，否则原样返回（兜底+专项双模式，与前端一致）
     *
     * @param value 条件值（可能是 "${currentYear}" 这类变量，也可能是字面值）
     * @return 解析后的值；非变量输入保证原样返回
     */
    public static String resolve(String value) {
        return resolve(value, token -> resolveTimeToken(token, LocalDate.now(ZONE)));
    }

    /**
     * 解析条件值，主体类变量（如 ${currentCustomerNumber}）由调用方注入解析器：
     * kernel 不依赖 authorization，主体取值只能以回调方式传入。
     * 解析优先级：主体解析器 → 内置时间变量 → 原样返回。
     *
     * @param value        条件值
     * @param tokenResolve 主体类 token 解析器（返回 null 表示不识别，继续走时间变量）
     * @return 解析后的值
     */
    public static String resolve(String value, Function<String, String> tokenResolve) {
        if (!isVariableForm(value)) {
            return value;
        }
        String token = value.substring(PREFIX.length(), value.length() - SUFFIX.length());
        if (tokenResolve != null) {
            String resolved = tokenResolve.apply(token);
            if (resolved != null) {
                return resolved;
            }
        }
        String timeValue = resolveTimeToken(token, LocalDate.now(ZONE));
        return timeValue != null ? timeValue : value;
    }

    /**
     * 值形如 ${...}（不校验 token 是否在全集内，交由解析链逐层识别：主体类 token 不在时间变量全集内）
     */
    public static boolean isVariableForm(String value) {
        return FuncUtil.isNotEmpty(value) && value.startsWith(PREFIX) && value.endsWith(SUFFIX)
                && value.length() > PREFIX.length() + SUFFIX.length();
    }

    /**
     * 时间变量求值：年/月/日三个维度的当期、上期、下期，全部基于同一天 today 推导；
     * 兼容 {@code now:pattern} 通用格式（按东八区当前时刻，不固定日期粒度）
     */
    private static String resolveTimeToken(String token, LocalDate today) {
        if (token.startsWith(NOW_PREFIX)) {
            return formatNow(token.substring(NOW_PREFIX.length()));
        }
        switch (token) {
            case CURRENT_YEAR:
                return String.valueOf(today.getYear());
            case LAST_YEAR:
                return String.valueOf(today.getYear() - 1);
            case NEXT_YEAR:
                return String.valueOf(today.getYear() + 1);
            case CURRENT_MONTH:
                return today.format(MONTH_FORMAT);
            case LAST_MONTH:
                return today.minusMonths(1).format(MONTH_FORMAT);
            case NEXT_MONTH:
                return today.plusMonths(1).format(MONTH_FORMAT);
            case CURRENT_DAY:
                return today.format(DAY_FORMAT);
            case LAST_DAY:
                return today.minusDays(1).format(DAY_FORMAT);
            case NEXT_DAY:
                return today.plusDays(1).format(DAY_FORMAT);
            case CURRENT_DATE_TIME:
                return LocalDateTime.now(ZONE).format(DATE_TIME_FORMAT);
            default:
                return null;
        }
    }

    /**
     * 保存校验：递归扫描 JSON 条件树（或纯字符串），对所有 ${...} 形态的值验证 token 合法性。
     * 非法 token 将抛 NoticeException 阻止脏配置入库。
     * <p>
     * 适用于 default_condition、extra_data、indicator.condition、table_filter.default_value、column.default_value 等
     * 全部可能承载动态变量的保存入口——调用方只需一行 {@code ConditionVariableUtil.validateTokens(value)}。
     *
     * @param valueOrJson 纯字符串（default_value）或 JSON 条件树（default_condition / extra_data）
     */
    public static void validateTokens(String valueOrJson) {
        if (FuncUtil.isEmpty(valueOrJson)) {
            return;
        }
        Set<String> invalidTokens = new LinkedHashSet<>();
        String trimmed = valueOrJson.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // JSON 条件树：递归遍历所有字符串叶子
            try {
                JsonNode root = JSON_MAPPER.readTree(trimmed);
                walkAndValidate(root, invalidTokens);
            } catch (Exception e) {
                // JSON 解析失败，当作普通字符串处理
                checkTokenValidity(trimmed, invalidTokens);
            }
        } else {
            checkTokenValidity(trimmed, invalidTokens);
        }
        if (!invalidTokens.isEmpty()) {
            Validator.assertException(ErrCodeSys.PA_PARAM_FORMAT,
                    "动态变量 " + invalidTokens + " 不合法，支持的变量：${currentYear}/${currentMonth}/${currentDay}/" +
                            "${currentDateTime}/${lastYear}/${nextYear}/${lastMonth}/${nextMonth}/${lastDay}/${nextDay}/" +
                            "${now:pattern}/${currentCustomerNumber}/${currentDeptId}/${currentGroupId}/${currentDeptIds}/${currentGroupIds}");
        }
    }

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static void walkAndValidate(JsonNode node, Set<String> invalidTokens) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            checkTokenValidity(node.asText(), invalidTokens);
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                walkAndValidate(child, invalidTokens);
            }
        } else if (node.isObject()) {
            Iterator<JsonNode> fields = node.elements();
            while (fields.hasNext()) {
                walkAndValidate(fields.next(), invalidTokens);
            }
        }
    }

    private static void checkTokenValidity(String value, Set<String> invalidTokens) {
        if (!isVariableForm(value)) {
            return;
        }
        String token = value.substring(PREFIX.length(), value.length() - SUFFIX.length());
        if (!SUPPORTED_TOKENS.contains(token) && !SUBJECT_TOKENS.contains(token) && !isValidNowPattern(token)) {
            invalidTokens.add(value);
        }
    }

    /**
     * {@code now:pattern} 求值：按东八区当前时刻格式化；非法 pattern 返回 null（交回调用方按未解析处理）
     */
    private static String formatNow(String pattern) {
        try {
            return LocalDateTime.now(ZONE).format(DateTimeFormatter.ofPattern(pattern));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
