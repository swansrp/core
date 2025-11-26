package com.bidr.forge.service.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysDatasetTableService;
import com.bidr.forge.service.driver.builder.DatasetSqlBuilder;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
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
public class DatasetDriver implements PortalDataDriver<Map<String, Object>> {

    private final SysPortalService sysPortalService;
    private final SysDatasetService sysDatasetService;
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
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
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
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return new Page<>(req.getCurrentPage(), req.getPageSize());
        }

        DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        String dataSource = getDatasetDataSource(datasetId);

        // 使用接口默认方法（内部调用 buildSqlParts 一次解析）
        return defaultQueryPage(req, aliasMap, builder, jdbcConnectService, dataSource);
    }

    @Override
    public List<Map<String, Object>> queryList(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return Collections.emptyList();
        }

        DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        String dataSource = getDatasetDataSource(datasetId);

        // 使用接口默认方法
        return defaultQueryList(req, aliasMap, builder, jdbcConnectService, dataSource);
    }

    @Override
    public Map<String, Object> queryOne(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return null;
        }

        DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        String dataSource = getDatasetDataSource(datasetId);

        // 使用接口默认方法
        return defaultQueryOne(req, aliasMap, builder, jdbcConnectService, dataSource);
    }

    @Override
    public Long count(AdvancedQueryReq req, String portalName, Long roleId) {
        Long datasetId = getDatasetIdFromPortal(portalName, roleId);
        List<SysDatasetTable> datasets = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        if (datasets.isEmpty()) {
            return 0L;
        }

        DatasetSqlBuilder builder = new DatasetSqlBuilder(datasetId, datasets, columns);
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        String dataSource = getDatasetDataSource(datasetId);

        // 使用接口默认方法
        return defaultCount(req, aliasMap, builder, jdbcConnectService, dataSource);
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

    /**
     * 从SysPortal获取referenceId作为datasetId
     *
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return datasetId
     */
    private Long getDatasetIdFromPortal(String portalName, Long roleId) {
        SysPortal portal = sysPortalService.getByName(portalName, roleId);
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

    /**
     * 获取Dataset的数据源配置
     *
     * @param datasetId 数据集ID
     * @return 数据源名称
     */
    private String getDatasetDataSource(Long datasetId) {
        SysDataset dataset = sysDatasetService.selectById(datasetId);
        if (dataset == null) {
            log.warn("未找到Dataset配置，使用默认数据源，datasetId: {}", datasetId);
            return null;
        }
        return dataset.getDataSource();
    }
}
