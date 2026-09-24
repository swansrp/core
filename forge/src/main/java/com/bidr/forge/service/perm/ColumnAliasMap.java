package com.bidr.forge.service.perm;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.forge.utils.SqlIdentifierUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: ColumnAliasMap
 * Description: 带「列权限收窄」标记的字段别名映射，用于堵住「绕过别名直传物理列名」的读取通道
 * <p>
 * 门户引擎解析请求字段时统一使用 {@code aliasMap.getOrDefault(field, field)}：命中别名则换成真实列，
 * 未命中则把前端传来的字符串原样当列名拼进 SQL。矩阵模式的 FROM 直接是物理表名，于是被列权限隐藏的列
 * 只要用真实列名请求就能读回来。
 * </p>
 * <p>
 * 本类把「本次是否真的剔除了列」随别名映射一起带到 SQL 构建处：只有剔除发生过（即该数据配置确实配了列
 * 权限、且当前用户至少被隐藏了一列），{@link #resolve} 才拒绝未命中别名的字段。没配列权限的资源走原逻辑，
 * 行为零变更。
 * </p>
 *
 * @author Sharp
 * @since 2026/09/23
 */
@Slf4j
public class ColumnAliasMap extends LinkedHashMap<String, String> {

    /**
     * 本次列权限判定是否剔除了至少一列
     */
    private boolean columnFilterNarrowed;

    /**
     * 因 portal 显示权限（{@code sys_portal_column}）被隐藏的字段名集合（aliasMap 的 key，已去 {@code c:} 前缀）。
     * <p>
     * 与 {@link #columnFilterNarrowed}（旧的 sys_dataset_column/sys_matrix_column 硬权限，直接从映射里删除）不同：
     * 显示权限只要求「不把该列的值返回」，但 WHERE/ORDER BY 仍可能要引用它（全局搜索、看板钻取链接过滤、
     * default_condition 等）。所以隐藏列<b>保留</b>在映射里供条件解析，仅登记于此供投影层与输出字段解析剔除。
     * </p>
     */
    private final Set<String> hiddenFields = new HashSet<>();

    /**
     * 登记一个「显示隐藏」字段：映射保留（条件/排序仍可解析），仅结果回传前置空该列的值
     */
    public void hideField(String fieldName) {
        if (FuncUtil.isNotEmpty(fieldName)) {
            hiddenFields.add(fieldName);
        }
    }

    /**
     * 被显示权限隐藏的字段名集合（aliasMap 的 key，已去 {@code c:} 前缀）；供结果置空时遍历
     */
    public Set<String> getHiddenFields() {
        return hiddenFields;
    }

    /**
     * 判断字段是否被显示权限隐藏（兼容裸名与驼峰两种口径，与 {@link #blankHiddenColumns} 一致）
     *
     * @param fieldName 待判定的字段名
     * @return 命中隐藏集则为 true
     */
    public boolean isHidden(String fieldName) {
        if (FuncUtil.isEmpty(fieldName)) {
            return false;
        }
        return hiddenFields.contains(fieldName) || hiddenFields.contains(StringUtil.underlineToCamel(fieldName));
    }

    /**
     * 标记列权限收窄结果
     *
     * @param allColumns       权限判定前的列清单
     * @param permittedColumns 权限判定后保留的列清单
     */
    public void markColumnFilterNarrowed(List<?> allColumns, List<?> permittedColumns) {
        this.columnFilterNarrowed = FuncUtil.isNotEmpty(allColumns)
                && FuncUtil.isNotEmpty(permittedColumns)
                && permittedColumns.size() < allColumns.size();
    }

    public boolean isColumnFilterNarrowed() {
        return columnFilterNarrowed;
    }

    /**
     * 解析请求字段对应的数据库列/表达式
     * <p>
     * 命中别名 → 返回映射值；未命中且列权限收窄过 → 拒绝；未命中且未收窄 → 原样返回字段名（历史行为）。
     * </p>
     *
     * @param aliasMap  字段别名映射（可能是普通 Map，此时不携带收窄信息，按历史行为处理）
     * @param fieldName 请求侧传入的字段名
     * @return 可用于拼 SQL 的列名或表达式
     */
    public static String resolve(Map<String, String> aliasMap, String fieldName) {
        if (FuncUtil.isEmpty(fieldName)) {
            return fieldName;
        }
        // 请求侧字段名会被拼进 `...` 位置，先校验字符集再参与映射/拼接
        assertSafeFieldName(fieldName);
        if (aliasMap != null && aliasMap.containsKey(fieldName)) {
            return aliasMap.get(fieldName);
        }
        if (aliasMap instanceof ColumnAliasMap && ((ColumnAliasMap) aliasMap).isColumnFilterNarrowed()) {
            log.warn("[列权限] 用户 {} 尝试以直传字段名方式访问隐藏列：field={}", AccountContext.getOperator(), fieldName);
            throw new NoticeException(ErrCodeSys.SYS_PERMIT_ERROR, "无权访问该字段：" + fieldName);
        }
        return fieldName;
    }

    /**
     * 用户请求排序（ORDER BY sortList）拒绝引用被显示权限隐藏的列
     * <p>
     * 隐藏列的值虽已在结果层置空，但排序仍会经由完整映射落到真实列，行序会泄露该列的大小关系；
     * 而链接过滤、{@code default_condition}、行级权限等系统注入的 WHERE 必须保留命中隐藏列的能力，
     * 故此闸门只卡「用户显式排序」这一侧，不动条件解析路径。
     * </p>
     *
     * @param aliasMap  字段别名映射（携带隐藏列信息）
     * @param fieldName 请求侧传入的排序字段名
     */
    public static void assertNotSortableField(Map<String, String> aliasMap, String fieldName) {
        assertSafeFieldName(fieldName);
        if (aliasMap instanceof ColumnAliasMap && ((ColumnAliasMap) aliasMap).isHidden(fieldName)) {
            log.warn("[列权限] 用户 {} 尝试以隐藏列排序：field={}", AccountContext.getOperator(), fieldName);
            throw new NoticeException(ErrCodeSys.SYS_PERMIT_ERROR, "无权按该字段排序：" + fieldName);
        }
    }

    /**
     * 断言请求字段属于本次可读的字段集合
     * <p>
     * 用于「字段名即输出别名」的场景（如透视的 ORDER BY 引用 SELECT 的 AS 别名）：这类字段不能经
     * {@link #resolve} 映射（映射后会丢失聚合列别名），改为比对调用方现场收集的可读字段集。
     * 列权限未收窄时不校验，保持历史行为。
     * </p>
     *
     * @param aliasMap   字段别名映射（携带是否收窄的标记）
     * @param readableFields 本次请求自身生成的可读字段集（如输出列别名）
     * @param fieldName  请求侧传入的字段名
     */
    public static void assertReadableField(Map<String, String> aliasMap, Collection<String> readableFields,
                                           String fieldName) {
        assertSafeFieldName(fieldName);
        boolean narrowed = aliasMap instanceof ColumnAliasMap && ((ColumnAliasMap) aliasMap).isColumnFilterNarrowed();
        if (narrowed && (readableFields == null || !readableFields.contains(fieldName))) {
            log.warn("[列权限] 用户 {} 尝试以不可见字段排序：field={}", AccountContext.getOperator(), fieldName);
            throw new NoticeException(ErrCodeSys.SYS_PERMIT_ERROR, "无权访问该字段：" + fieldName);
        }
    }

    /**
     * 校验请求侧字段名仅含可安全拼入 SQL 的字符
     * <p>
     * 这一层与列权限无关：字段名来自请求体，含反引号或反斜杠即可逃逸出 {@code `...`} 引用改写整条
     * SQL，所以不论有没有配置权限都必须拦。拒绝而非剔字：静默删字会留下一个被截断的假字段名，
     * 表现为“查不到数据”的频发难题，比直接报错更难排查。
     * </p>
     *
     * @param fieldName 请求侧传入的字段名
     */
    private static void assertSafeFieldName(String fieldName) {
        if (SqlIdentifierUtil.isSafeIdentifier(fieldName)) {
            return;
        }
        log.warn("[字段校验] 用户 {} 传入非法字段名：field={}", AccountContext.getOperator(), fieldName);
        throw new NoticeException(ErrCodeSys.SYS_ERR_MSG, "非法字段名：" + fieldName);
    }

    /**
     * 将单行结果中被显示权限隐藏的列置空（值设为 null，保留键以维持行结构完整）
     * <p>
     * 兼容两种输出列名口径：结果行键可能是 dataset 的 columnAlias（如下划线形式），而 hiddenFields 存的是
     * VO 驼峰字段名，故键本身或其驼峰化形式命中即置空。
     * </p>
     *
     * @param row          一行结果（可为空）
     * @param hiddenFields 隐藏字段名集合（{@link #getHiddenFields()}）
     */
    public static void blankHiddenColumns(Map<String, Object> row, Set<String> hiddenFields) {
        if (row == null || row.isEmpty() || FuncUtil.isEmpty(hiddenFields)) {
            return;
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (hiddenFields.contains(key) || hiddenFields.contains(StringUtil.underlineToCamel(key))) {
                entry.setValue(null);
            }
        }
    }

    /**
     * 批量版：对多行结果逐行 {@link #blankHiddenColumns(Map, Set)}
     */
    public static void blankHiddenColumns(List<Map<String, Object>> rows, Set<String> hiddenFields) {
        if (FuncUtil.isEmpty(rows) || FuncUtil.isEmpty(hiddenFields)) {
            return;
        }
        rows.forEach(row -> blankHiddenColumns(row, hiddenFields));
    }
}
