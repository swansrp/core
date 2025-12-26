package com.bidr.forge.engine.driver;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.entity.SysPortalColumn;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.forge.engine.driver.inf.DriverCrudInf;
import com.bidr.forge.engine.driver.inf.DriverQueryOnlyInf;
import com.bidr.forge.engine.driver.inf.DriverTreeInf;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, String> aliasMap = new LinkedHashMap<>();
        List<SysPortalColumn> sysPortalColumnList = getSysPortalService().getColumnsByPortalName(portalName, roleId);
        for (SysPortalColumn sysPortalColumn : sysPortalColumnList) {
            aliasMap.put(sysPortalColumn.getProperty(), sysPortalColumn.getDbField());
        }
        return aliasMap;
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
}
