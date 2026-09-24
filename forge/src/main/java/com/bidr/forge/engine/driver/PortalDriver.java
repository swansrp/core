package com.bidr.forge.engine.driver;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.service.perm.PortalColumnPolicyService;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.engine.driver.inf.DriverCrudInf;
import com.bidr.forge.engine.driver.inf.DriverQueryOnlyInf;
import com.bidr.forge.engine.driver.inf.DriverTreeInf;
import com.bidr.forge.service.perm.ColumnAliasMap;
import com.bidr.forge.service.perm.ColumnPermGuard;
import com.bidr.forge.service.perm.PortalAccessGuard;
import com.bidr.forge.utils.SqlIdentifierUtil;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Portal数据驱动统合接口（最顶层接口）
 * <p>继承所有层级接口，提供完整的数据操作能力</p>
 *
 * <h3>接口层次：</h3>
 * <pre>
 * DriverQueryOnlyInf（第1层 - 只读）
 *   └── DriverCrudInf（第2层 - 增删改）
 *         └── DriverTreeInf（第3层 - 树形结构）
 *               └── PortalDriver（统合接口）⬅ 当前接口
 * </pre>
 *
 * <h3>完整能力清单：</h3>
 * <ul>
 *   <li><b>查询能力</b>（来自DriverQueryOnlyInf）：
 *     <ul>
 *       <li>queryPage - 分页查询</li>
 *       <li>queryList - 列表查询</li>
 *       <li>queryOne - 单条查询</li>
 *       <li>count - 计数查询</li>
 *       <li>selectById - 根据ID查询</li>
 *       <li>getAllData - 获取所有数据</li>
 *     </ul>
 *   </li>
 *   <li><b>统计能力</b>（来自DriverQueryOnlyInf）：
 *     <ul>
 *       <li>generalCount、advancedCount - 统计计数</li>
 *       <li>generalSummary、advancedSummary - 汇总统计</li>
 *       <li>generalStatistic、advancedStatistic - 指标统计</li>
 *     </ul>
 *   </li>
 *   <li><b>CRUD能力</b>（来自DriverCrudInf）：
 *     <ul>
 *       <li>insert、batchInsert - 插入数据</li>
 *       <li>update、batchUpdate - 更新数据</li>
 *       <li>delete、batchDelete - 删除数据</li>
 *     </ul>
 *   </li>
 *   <li><b>树形能力</b>（来自DriverTreeInf）：
 *     <ul>
 *       <li>getTreeData - 获取完整树形数据</li>
 *       <li>getAdvancedTreeData - 高级查询树形数据</li>
 *       <li>getParent - 获取父节点</li>
 *       <li>getChildren - 获取子节点</li>
 *       <li>getBrothers - 获取兄弟节点</li>
 *       <li>updatePid - 变更父节点</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>实现说明：</h3>
 * <ul>
 *   <li><b>Matrix驱动</b>：实现所有方法（完整功能）</li>
 *   <li><b>Dataset驱动</b>：只实现查询和统计方法，其他方法抛UnsupportedOperationException</li>
 * </ul>
 *
 * <h3>选择合适的接口：</h3>
 * <ul>
 *   <li>只需查询 → 使用 {@link DriverQueryOnlyInf}</li>
 *   <li>需要增删改 → 使用 {@link DriverCrudInf}</li>
 *   <li>需要树形结构 → 使用 {@link DriverTreeInf}</li>
 *   <li>需要所有功能 → 使用 {@link PortalDriver}</li>
 * </ul>
 *
 * @param <VO> 返回的VO类型
 * @author Sharp
 * @since 2025-11-27
 */
public interface PortalDriver<VO> extends DriverTreeInf<VO> {
    // 统合所有Driver能力（查询 + 统计 + CRUD + 树形）
    // 继承自 DriverTreeInf，自动获得所有层级的能力

    /**
     * 获取SysPortalService
     *
     * @return SysPortalService bean
     */
    default SysPortalService getSysPortalService() {
        SysPortalService bean = BeanUtil.getBean(SysPortalService.class);
        if (bean == null) {
            throw new IllegalArgumentException("未找到SysPortalService ");
        }
        return bean;
    }

    /**
     * 从SysPortal获取referenceId作为datasetId
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return datasetId
     */
    default Long getDatasetIdFromPortal(String portalName, Long roleId) {
        SysPortal portal = getSysPortalService().getByName(portalName, roleId);
        if (portal == null) {
            throw new IllegalArgumentException("未找到Portal配置: " + portalName);
        }

        String referenceId = portal.getReferenceId();
        if (FuncUtil.isEmpty(referenceId)) {
            throw new IllegalArgumentException("Portal的referenceId为空: " + portalName);
        }

        try {
            return Long.parseLong(referenceId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Portal的referenceId格式错误: " + referenceId, e);
        }
    }

    default Map<String, String> buildAliasMap(String portalName, Long roleId) {
        // 用带标记的实现类：列权限收窄信息要跟着映射一起传到 SQL 构建处
        ColumnAliasMap aliasMap = new ColumnAliasMap();
        SysPortal sysPortal = getSysPortalService().getByName(portalName, roleId);
        if (sysPortal == null) {
            throw new IllegalArgumentException("未找到Portal配置: " + portalName);
        }

        final String referenceId = sysPortal.getReferenceId();
        final String dataMode = sysPortal.getDataMode();
        if (FuncUtil.isEmpty(referenceId) || FuncUtil.isEmpty(dataMode)) {
            return aliasMap;
        }

        final Long refId;
        try {
            refId = Long.parseLong(referenceId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Portal的referenceId格式错误: " + referenceId, e);
        }

        if ("DATASET".equalsIgnoreCase(dataMode)) {
            SysDatasetColumnService datasetColumnService = BeanUtil.getBean(SysDatasetColumnService.class);
            if (datasetColumnService == null) {
                throw new IllegalArgumentException("未找到SysDatasetColumnService");
            }
            List<SysDatasetColumn> allCols = datasetColumnService.getByDatasetId(refId);
            List<SysDatasetColumn> cols = retainPermittedDatasetColumns(allCols);
            // 确实剔除了列才置位，未配列权限的资源保持原有“未命中则直传”的行为
            aliasMap.markColumnFilterNarrowed(allCols, cols);
            for (SysDatasetColumn c : cols) {
                // Dataset: 返回字段名以 columnAlias 为准（通常已是驼峰）；如果是下划线形式则转驼峰
                String alias = SqlIdentifierUtil.sanitizeQuotedIdentifier(c.getColumnAlias());
                if (FuncUtil.isEmpty(alias)) {
                    continue;
                }
                String fieldName = alias.contains("_") ? StringUtil.underlineToCamel(alias) : alias;

                // value: 真实 SQL 列/表达式（用于 WHERE/ORDER BY 等条件落到真实字段）
                String sqlExpr = SqlIdentifierUtil.sanitizeQuotedIdentifier(c.getColumnSql());
                if (FuncUtil.isEmpty(sqlExpr)) {
                    sqlExpr = fieldName;
                }

                aliasMap.put(fieldName, sqlExpr);
            }
        } else if ("MATRIX".equalsIgnoreCase(dataMode)) {
            SysMatrixColumnService matrixColumnService = BeanUtil.getBean(SysMatrixColumnService.class);
            if (matrixColumnService == null) {
                throw new IllegalArgumentException("未找到SysMatrixColumnService");
            }
            List<SysMatrixColumn> allCols = matrixColumnService.getByMatrixId(refId);
            List<SysMatrixColumn> cols = retainPermittedMatrixColumns(allCols);
            // 矩阵的 FROM 是物理表名，只能靠该标记启用字段白名单，堵住直传真实列名的读回通道
            aliasMap.markColumnFilterNarrowed(allCols, cols);
            for (SysMatrixColumn c : cols) {
                // Matrix: 统一别名策略：key=VO字段名(驼峰), value=数据库列名
                String columnName = SqlIdentifierUtil.sanitizeQuotedIdentifier(c.getColumnName());
                if (FuncUtil.isEmpty(columnName)) {
                    continue;
                }

                String fieldName = StringUtil.underlineToCamel(columnName);
                aliasMap.put(fieldName, columnName);
            }
        } else {
            // 兜底：如果 dataMode 不识别，回退到原 portal 列配置（如历史数据仍配置在 sys_portal_column）
            List<SysPortalColumn> sysPortalColumnList = getSysPortalService().getColumnsByPortalName(portalName, roleId);
            for (SysPortalColumn sysPortalColumn : sysPortalColumnList) {
                String prop = SqlIdentifierUtil.sanitizeQuotedIdentifier(sysPortalColumn.getProperty());
                String dbField = SqlIdentifierUtil.sanitizeQuotedIdentifier(sysPortalColumn.getDbField());
                if (FuncUtil.isNotEmpty(prop) && FuncUtil.isNotEmpty(dbField)) {
                    aliasMap.put(prop, dbField);
                }
            }
        }

        // 列级权限（原表列黑名单）：剔除当前用户无权查看的列，无权列不进 SELECT（与数据模式无关，统一收口）
        applyPortalColumnPerm(portalName, aliasMap);

        return aliasMap;
    }

    /**
     * 按 portal 列权限（{@code sys_portal_column}）登记当前用户无权查看的原表列
     * <p>
     * token 以 {@code c:} + alias key（VO 字段驼峰，与 sys_portal_column.property 对齐）匹配。采用「<b>保留映射、
     * 结果置空</b>」策略：不把隐藏列从 aliasMap 删除（否则全局搜索/看板钻取链接过滤/default_condition 等
     * 引用该列时无法解析为物理列，会在 summary 下推内层时退化成非法 {@code t.<col>}），而是登记到
     * {@link ColumnAliasMap#hideField}，由各查询返回点统一 {@code blankHiddenColumns} 置空其值。
     * 全列被隐藏也仅返回全空值，不会触发 {@code SELECT *} 泄漏（投影仍完整）。
     * </p>
     */
    default void applyPortalColumnPerm(String portalName, ColumnAliasMap aliasMap) {
        PortalColumnPolicyService columnPolicyService = BeanUtil.getBean(PortalColumnPolicyService.class);
        if (columnPolicyService == null) {
            return;
        }
        Set<String> hiddenC = columnPolicyService.resolveHiddenTokens(
                PortalAccessGuard.RESOURCE_TYPE_PORTAL_COLUMN, portalName);
        if (FuncUtil.isEmpty(hiddenC)) {
            return;
        }
        // 只登记隐藏字段、不删映射；条件/排序仍需解析隐藏列，其值在回传前置空
        for (String token : hiddenC) {
            if (token != null && token.startsWith("c:")) {
                aliasMap.hideField(token.substring(2));
            }
        }
    }

    /**
     * 解析当前用户在该 portal 上被显示权限隐藏的字段名集合（{@code c:} token 去前缀）
     * <p>
     * 供 summary/pivot 等不携带 {@link ColumnAliasMap#getHiddenFields()}（如 dataset 透视走普通
     * buildStatisticAliasMap）的返回路径，在结果回传前置空隐藏列的值。无 bean 或无隐藏时返回空集。
     * </p>
     */
    default Set<String> resolveHiddenColumnFields(String portalName) {
        PortalColumnPolicyService columnPolicyService = BeanUtil.getBean(PortalColumnPolicyService.class);
        if (columnPolicyService == null) {
            return Collections.emptySet();
        }
        Set<String> tokens = columnPolicyService.resolveHiddenTokens(
                PortalAccessGuard.RESOURCE_TYPE_PORTAL_COLUMN, portalName);
        Set<String> fields = new HashSet<>();
        for (String token : tokens) {
            if (token != null && token.startsWith("c:")) {
                fields.add(token.substring(2));
            }
        }
        return fields;
    }

    /**
     * 统计图表结果脱敏：递归把隐藏列（分类轴）的 metric（实际值）与 metricLabel（字典标签）置 null。
     * <p>口径与列表一致：保留节点与聚合数值，仅剥离隐藏列自身的值；隐藏列不作为分类轴时不受影响。
     */
    default void blankHiddenStatistics(String portalName, List<StatisticRes> resList) {
        if (FuncUtil.isEmpty(resList)) {
            return;
        }
        Set<String> hiddenFields = resolveHiddenColumnFields(portalName);
        if (FuncUtil.isEmpty(hiddenFields)) {
            return;
        }
        blankStatisticNodes(resList, hiddenFields);
    }

    /**
     * 递归遍历 {@link StatisticRes} 树，命中隐藏分类轴列的节点把值与标签置 null。
     */
    default void blankStatisticNodes(List<StatisticRes> nodes, Set<String> hiddenFields) {
        for (StatisticRes node : nodes) {
            if (node == null) {
                continue;
            }
            String metricColumn = node.getMetricColumn();
            if (metricColumn != null
                    && (hiddenFields.contains(metricColumn) || hiddenFields.contains(StringUtil.underlineToCamel(metricColumn)))) {
                node.setMetric(null);
                node.setMetricLabel(null);
            }
            if (FuncUtil.isNotEmpty(node.getChildren())) {
                blankStatisticNodes(node.getChildren(), hiddenFields);
            }
        }
    }

    /**
     * 求当前用户有权查看的列配置 id 集合
     * <p>
     * 返回 {@code null} 表示「跳过列过滤」（线程无登录上下文，如定时任务/内部调用）。
     * 不存在「返回空集合」这种结果：全部列无权时 {@link ColumnPermGuard} 直接抛权限异常（fail-closed），
     * 因为空 aliasMap 会被下游 SQL 构建器兜底成 {@code SELECT *}，反而把整表全列吐出去。
     * </p>
     *
     * @param resourceType 列级授权的资源类型
     * @param columnIds    待判定的列配置 id
     * @return 有权的列 id 集合；null 表示跳过过滤
     */
    default Set<String> retainPermittedColumnIds(String resourceType, List<Long> columnIds) {
        ColumnPermGuard columnPermGuard = BeanUtil.getBean(ColumnPermGuard.class);
        if (columnPermGuard == null) {
            return null;
        }
        return columnPermGuard.retainAccessibleColumns(resourceType, columnIds);
    }

    /**
     * 剔除当前用户无权的<b>数据集列</b>配置
     * <p>
     * 过滤发生在列元数据层面，因此别名映射、内层子查询 SELECT 与统计透视都只能看到有权列：
     * 无权列在生成的 SQL 里根本不存在（报 Unknown column），而无法被“绕过别名直传真实列名”读出。
     * </p>
     */
    default List<SysDatasetColumn> retainPermittedDatasetColumns(List<SysDatasetColumn> columns) {
        if (FuncUtil.isEmpty(columns)) {
            return columns;
        }
        Set<String> permittedColumnIds = retainPermittedColumnIds(ColumnPermGuard.RESOURCE_TYPE_DATASET_COLUMN, columns.stream()
                .map(SysDatasetColumn::getId).collect(Collectors.toList()));
        if (permittedColumnIds == null) {
            return columns;
        }
        return columns.stream()
                .filter(column -> permittedColumnIds.contains(String.valueOf(column.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 剔除当前用户无权的<b>矩阵列</b>配置（口径同 {@link #retainPermittedDatasetColumns}）
     */
    default List<SysMatrixColumn> retainPermittedMatrixColumns(List<SysMatrixColumn> columns) {
        if (FuncUtil.isEmpty(columns)) {
            return columns;
        }
        Set<String> permittedColumnIds = retainPermittedColumnIds(ColumnPermGuard.RESOURCE_TYPE_MATRIX_COLUMN, columns.stream()
                .map(SysMatrixColumn::getId).collect(Collectors.toList()));
        if (permittedColumnIds == null) {
            return columns;
        }
        return columns.stream()
                .filter(column -> permittedColumnIds.contains(String.valueOf(column.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 根据数据库列名查找VO字段名
     */
    default String findVoColumnName(String columnName, Map<String, String> aliasMap) {
        for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
            if (entry.getValue().equals(columnName)) {
                return entry.getKey();
            }
        }
        return columnName;
    }

    /**
     * 为统计查询构建别名映射：key=columnAlias(如userStatus)，value=columnAlias(如userStatus)
     * 在统计查询中，需要引用子查询中定义的列别名，而不是原始数据库列名
     */
    default Map<String, String> buildStatisticAliasMap(List<SysDatasetColumn> columns) {
        Map<String, String> aliasMap = new LinkedHashMap<>();
        if (FuncUtil.isEmpty(columns)) {
            return aliasMap;
        }

        for (SysDatasetColumn column : columns) {
            if (FuncUtil.isNotEmpty(column.getColumnAlias())) {
                String alias = SqlIdentifierUtil.sanitizeQuotedIdentifier(column.getColumnAlias());
                if (FuncUtil.isNotEmpty(alias)) {
                    // 在统计查询中，字段名映射到其在子查询中使用的别名
                    aliasMap.put(alias, alias);
                }
            }
        }
        return aliasMap;
    }
}
