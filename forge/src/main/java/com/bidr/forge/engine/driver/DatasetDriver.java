package com.bidr.forge.engine.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.bo.DatasetColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysDatasetTableService;
import com.bidr.forge.engine.DriverCapability;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.engine.builder.DatasetSqlBuilder;
import com.bidr.forge.engine.builder.SqlBuilder;
import com.bidr.forge.service.statistic.DatasetStatisticQueryContext;
import com.bidr.forge.service.statistic.DriverStatisticSupportService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.IdPidReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.TreeDataItemVO;
import com.bidr.kernel.vo.common.TreeDataResVO;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedSummaryReq;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Dataset驱动实现
 * 支持多表联接视图的只读查询，支持复杂表达式（CASE WHEN等）
 *
 * @author Sharp
 * @since 2025-11-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetDriver implements PortalDriver<Map<String, Object>> {

    private final SysDatasetService sysDatasetService;
    private final SysDatasetTableService sysDatasetTableService;
    private final SysDatasetColumnService sysDatasetColumnService;
    private final JdbcConnectService jdbcConnectService;
    private final DriverStatisticSupportService driverStatisticSupportService;

    @Override
    public DriverCapability getCapability() {
        return DriverCapability.readOnly();
    }

    @Override
    public PortalDataMode getDataMode() {
        return PortalDataMode.DATASET;
    }

    @Override
    public SqlBuilder getSqlBuilder(String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);
        return new DatasetSqlBuilder(datasetId, datasets, columns);
    }

    @Override
    public JdbcConnectService getJdbcConnectService() {
        return jdbcConnectService;
    }

    @Override
    public String getDataSource(String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        SysDataset dataset = sysDatasetService.selectById(datasetId);
        if (dataset == null) {
            LoggerFactory.getLogger(getClass()).warn("未找到Dataset配置，使用默认数据源，datasetId: {}", datasetId);
            return null;
        }
        return dataset.getDataSource();
    }

    @Override
    public Page<Map<String, Object>> queryPage(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return new Page<>(req.getCurrentPage(), req.getPageSize());
        }

        return doQueryPage(req, portalName, roleId);
    }

    @Override
    public List<Map<String, Object>> queryList(AdvancedQueryReq req, String portalName, Long roleId) {
        return doQueryList(req, portalName, roleId);
    }

    @Override
    public Map<String, Object> queryOne(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return null;
        }

        return doQueryOne(req, portalName, roleId);
    }

    @Override
    public Map<String, Object> selectById(Object id, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持按主键ID查询操作");
    }

    @Override
    public List<Map<String, Object>> getAllData(String portalName, Long roleId) {
        AdvancedQueryReq req = new AdvancedQueryReq();
        return queryList(req, portalName, roleId);
    }

    @Override
    public int insert(Map<String, Object> data, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持INSERT操作");
    }

    @Override
    public int batchInsert(List<Map<String, Object>> dataList, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持INSERT操作");
    }

    @Override
    public int update(Map<String, Object> data, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持UPDATE操作");
    }

    @Override
    public int batchUpdate(List<Map<String, Object>> dataList, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持UPDATE操作");
    }

    @Override
    public int delete(Object id, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持DELETE操作");
    }

    @Override
    public int batchDelete(List<Object> ids, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式不支持DELETE操作");
    }

    // ========== 统计方法（暂不支持） ==========

    @Override
    public Long count(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return 0L;
        }

        return doCount(req, portalName, roleId);
    }

    @Override
    public Map<String, Object> summary(AdvancedSummaryReq req, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset模式暂不支持汇总统计操作");
    }

    @Override
    public List<StatisticRes> statistic(AdvancedStatisticReq req, String portalName, Long roleId) {
        DatasetColumns datasetColumns = driverStatisticSupportService.getDatasetColumns(portalName);
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);

        // 保存当前线程进入本方法前的数据源（可能为空=默认），用于后续精确恢复
        String prevDataSource = jdbcConnectService.getCurrentDataSourceName();
        log.info("DatasetDriver.statistic 切换数据源前，当前数据源：{}", prevDataSource);

        // 1) 先在“进入方法前的数据源”里读取 Dataset 配置表（这些表一定在 ERP/默认库）
        //    避免因为后续切到 Doris 等统计源导致 MyBatis 去 Doris 查询 sys_dataset_* 从而出现 No database selected。
        List<SysDatasetTable> datasets;
        List<SysDatasetColumn> columns;
        if (FuncUtil.isNotEmpty(prevDataSource)) {
            try (JdbcConnectService.DataSourceScope ignored = jdbcConnectService.switchDataSourceScope(prevDataSource)) {
                datasets = sysDatasetTableService.getByDatasetId(datasetColumns.getId());
                columns = sysDatasetColumnService.getByDatasetId(datasetColumns.getId());
            }
        } else {
            datasets = sysDatasetTableService.getByDatasetId(datasetColumns.getId());
            columns = sysDatasetColumnService.getByDatasetId(datasetColumns.getId());
        }

        // 2) 在 Dataset 指定的数据源中执行统计 SQL（真正访问业务表/维表的阶段）
        if (FuncUtil.isEmpty(datasetColumns.getDataSource())) {
            DatasetStatisticQueryContext ctx = new DatasetStatisticQueryContext(datasetColumns, datasets, columns);
            return driverStatisticSupportService.statistic(jdbcConnectService, req, ctx, aliasMap);
        }

        try (JdbcConnectService.DataSourceScope ignored = jdbcConnectService.switchDataSourceScope(datasetColumns.getDataSource())) {
            DatasetStatisticQueryContext ctx = new DatasetStatisticQueryContext(datasetColumns, datasets, columns);
            return driverStatisticSupportService.statistic(jdbcConnectService, req, ctx, aliasMap);
        } finally {
            // 双保险：确保离开本方法时恢复到进入本方法前的数据源
            jdbcConnectService.restoreDataSource(prevDataSource);
        }
    }

    // ========== 树形结构方法（暂不支持） ==========

    @Override
    public List<TreeDataResVO> getTreeData(String portalName, Long roleId) {
        throw new UnsupportedOperationException("Dataset不支持树形结构");
    }

    @Override
    public List<TreeDataResVO> getAdvancedTreeData(AdvancedQueryReq req, String portalName, Long roleId) {
        return Collections.emptyList();
    }

    @Override
    public TreeDataItemVO getParent(IdReqVO req, String portalName, Long roleId) {
        return null;
    }

    @Override
    public List<TreeDataItemVO> getChildren(IdReqVO req, String portalName, Long roleId) {
        return Collections.emptyList();
    }

    @Override
    public List<TreeDataItemVO> getBrothers(IdReqVO req, String portalName, Long roleId) {
        return Collections.emptyList();
    }

    @Override
    public void updatePid(IdPidReqVO req, String portalName, Long roleId) {

    }
}
