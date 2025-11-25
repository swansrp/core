package com.bidr.forge.service.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.repository.SysDatasetTableService;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.service.driver.builder.DatasetSqlBuilder;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

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
public class DatasetDriver implements PortalDataDriver<Map<String, Object>> {

    private final SysDatasetTableService sysDatasetTableService;
    private final SysDatasetColumnService sysDatasetColumnService;
    private final JdbcConnectService jdbcConnectService;

    @Override
    public DriverCapability getCapability() {
        return DriverCapability.readOnly();
    }

    @Override
    public PortalDataMode getDataMode() {
        return PortalDataMode.DATASET;
    }

    @Override
    public Map<String, String> buildAliasMap(String portalName, Long roleId) {
        // portalName在Dataset模式下即为datasetId（需要转为Long）
        Long datasetId = Long.parseLong(portalName);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        Map<String, String> aliasMap = new LinkedHashMap<>();
        for (SysDatasetColumn column : columns) {
            if (CommonConst.YES.equals(column.getIsVisible())) {
                String columnAlias = column.getColumnAlias();
                String columnSql = column.getColumnSql();
                if (FuncUtil.isNotEmpty(columnAlias)) {
                    // VO字段名 -> SQL表达式
                    aliasMap.put(columnAlias, columnSql);
                }
            }
        }

        return aliasMap;
    }

    @Override
    public Page<Map<String, Object>> queryPage(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = Long.parseLong(portalName);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return new Page<>(req.getCurrentPage(), req.getPageSize());
        }

        // 切换数据源
        SysDatasetTable firstDataset = datasets.get(0);
        if (FuncUtil.isNotEmpty(firstDataset.getDataSource())) {
            jdbcConnectService.switchDataSource(firstDataset.getDataSource());
        }

        try {
            DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
            Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
            Map<String, Object> parameters = new HashMap<>();

            // 查询总数
            String countSql = builder.buildCount(req, aliasMap, new HashMap<>(parameters));
            Long total = jdbcConnectService.queryForObject(countSql, parameters, Long.class);

            // 查询数据
            String selectSql = builder.buildSelect(req, aliasMap, parameters);
            List<Map<String, Object>> records = jdbcConnectService.query(selectSql, parameters);

            Page<Map<String, Object>> page = new Page<>(req.getCurrentPage(), req.getPageSize());
            page.setTotal(total == null ? 0 : total);
            page.setRecords(records);

            return page;
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    @Override
    public List<Map<String, Object>> queryList(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = Long.parseLong(portalName);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return Collections.emptyList();
        }

        // 切换数据源
        SysDatasetTable firstDataset = datasets.get(0);
        if (FuncUtil.isNotEmpty(firstDataset.getDataSource())) {
            jdbcConnectService.switchDataSource(firstDataset.getDataSource());
        }

        try {
            DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
            Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
            Map<String, Object> parameters = new HashMap<>();

            // 构造不分页的查询（移除分页参数）
            AdvancedQueryReq noPagingReq = new AdvancedQueryReq();
            ReflectionUtil.copyProperties(req, noPagingReq);
            noPagingReq.setCurrentPage(null);
            noPagingReq.setPageSize(null);

            String selectSql = builder.buildSelect(noPagingReq, aliasMap, parameters);
            return jdbcConnectService.query(selectSql, parameters);
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    @Override
    public Map<String, Object> queryOne(AdvancedQueryReq req, String portalName, Long roleId) {
        // 限制查询1条
        req.setCurrentPage(1L);
        req.setPageSize(1L);

        List<Map<String, Object>> list = queryList(req, portalName, roleId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long count(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = Long.parseLong(portalName);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return 0L;
        }

        // 切换数据源
        SysDatasetTable firstDataset = datasets.get(0);
        if (FuncUtil.isNotEmpty(firstDataset.getDataSource())) {
            jdbcConnectService.switchDataSource(firstDataset.getDataSource());
        }

        try {
            DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
            Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
            Map<String, Object> parameters = new HashMap<>();

            String countSql = builder.buildCount(req, aliasMap, parameters);
            Long result = jdbcConnectService.queryForObject(countSql, parameters, Long.class);
            return result == null ? 0L : result;
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
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
}
