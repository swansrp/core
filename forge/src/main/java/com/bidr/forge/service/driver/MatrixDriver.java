package com.bidr.forge.service.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.service.driver.builder.MatrixSqlBuilder;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Matrix驱动实现
 * 支持单表完整CRUD、树结构查询
 *
 * @author Sharp
 * @since 2025-11-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatrixDriver implements PortalDataDriver<Map<String, Object>> {

    private final SysMatrixService sysMatrixService;
    private final JdbcConnectService jdbcConnectService;

    @Override
    public DriverCapability getCapability() {
        return DriverCapability.fullSupport();
    }

    @Override
    public PortalDataMode getDataMode() {
        return PortalDataMode.MATRIX;
    }

    @Override
    public Map<String, String> buildAliasMap(String portalName, String roleId) {
        // portalName在Matrix模式下可能是tableName或matrixId
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        if (matrixColumns == null || matrixColumns.getColumns() == null) {
            return new LinkedHashMap<>();
        }

        Map<String, String> aliasMap = new LinkedHashMap<>();
        for (SysMatrixColumn column : matrixColumns.getColumns()) {
            // VO字段名 -> 数据库列名
            String columnName = column.getColumnName();
            aliasMap.put(columnName, columnName);
        }

        return aliasMap;
    }

    @Override
    public Page<Map<String, Object>> queryPage(AdvancedQueryReq req, String portalName, String roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
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
    public List<Map<String, Object>> queryList(AdvancedQueryReq req, String portalName, String roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
            Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
            Map<String, Object> parameters = new HashMap<>();

            // 构造不分页的查询
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
    public Map<String, Object> queryOne(AdvancedQueryReq req, String portalName, String roleId) {
        req.setCurrentPage(1L);
        req.setPageSize(1L);

        List<Map<String, Object>> list = queryList(req, portalName, roleId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public Long count(AdvancedQueryReq req, String portalName, String roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
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
    public List<Map<String, Object>> getAllData(String portalName, String roleId) {
        AdvancedQueryReq req = new AdvancedQueryReq();
        // 添加valid=YES条件
        return queryList(req, portalName, roleId);
    }

    @Override
    public int insert(Map<String, Object> data, String portalName, String roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 数据校验
        validateInsertData(data, matrixColumns.getColumns());

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
            Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
            Map<String, Object> parameters = new HashMap<>();

            String insertSql = builder.buildInsert(data, aliasMap, parameters);
            return jdbcConnectService.update(insertSql, parameters);
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    @Override
    public int batchInsert(List<Map<String, Object>> dataList, String portalName, String roleId) {
        int totalAffected = 0;
        for (Map<String, Object> data : dataList) {
            totalAffected += insert(data, portalName, roleId);
        }
        return totalAffected;
    }

    @Override
    public int update(Map<String, Object> data, String portalName, String roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 数据校验
        validateUpdateData(data, matrixColumns.getColumns());

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
            Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
            Map<String, Object> parameters = new HashMap<>();

            String updateSql = builder.buildUpdate(data, aliasMap, parameters);
            return jdbcConnectService.update(updateSql, parameters);
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    @Override
    public int batchUpdate(List<Map<String, Object>> dataList, String portalName, String roleId) {
        int totalAffected = 0;
        for (Map<String, Object> data : dataList) {
            totalAffected += update(data, portalName, roleId);
        }
        return totalAffected;
    }

    @Override
    public int delete(Object id, String portalName, String roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
            Map<String, Object> parameters = new HashMap<>();

            String deleteSql = builder.buildDelete(id, parameters);
            return jdbcConnectService.update(deleteSql, parameters);
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    @Override
    public int batchDelete(List<Object> ids, String portalName, String roleId) {
        int totalAffected = 0;
        for (Object id : ids) {
            totalAffected += delete(id, portalName, roleId);
        }
        return totalAffected;
    }

    /**
     * 获取Matrix配置和列
     */
    private MatrixColumns getMatrixColumns(String portalName) {
        // 尝试按tableName查询
        MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(portalName);
        if (matrixColumns == null) {
            // 尝试按ID查询
            try {
                Long matrixId = Long.parseLong(portalName);
                matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return matrixColumns;
    }

    /**
     * 校验插入数据
     */
    private void validateInsertData(Map<String, Object> data, List<SysMatrixColumn> columns) {
        for (SysMatrixColumn column : columns) {
            String columnName = column.getColumnName();
            Object value = data.get(columnName);

            // 必填校验（排除主键）
            if (CommonConst.NO.equals(column.getIsNullable())
                    && !CommonConst.YES.equals(column.getIsPrimaryKey())
                    && FuncUtil.isEmpty(value)) {
                Validator.assertTrue(false, ErrCodeSys.SYS_ERR_MSG,
                        "字段[" + column.getColumnComment() + "]不能为空");
            }

            // 长度校验
            if (FuncUtil.isNotEmpty(value) && value instanceof String) {
                String strValue = (String) value;
                if (column.getColumnLength() != null && strValue.length() > column.getColumnLength()) {
                    Validator.assertTrue(false, ErrCodeSys.SYS_ERR_MSG,
                            "字段[" + column.getColumnComment() + "]长度不能超过" + column.getColumnLength());
                }
            }
        }
    }

    /**
     * 校验更新数据
     */
    private void validateUpdateData(Map<String, Object> data, List<SysMatrixColumn> columns) {
        // 确保包含主键
        List<SysMatrixColumn> primaryKeys = columns.stream()
                .filter(col -> CommonConst.YES.equals(col.getIsPrimaryKey()))
                .collect(Collectors.toList());

        boolean hasPrimaryKey = false;
        for (SysMatrixColumn pkColumn : primaryKeys) {
            if (data.containsKey(pkColumn.getColumnName())) {
                hasPrimaryKey = true;
                break;
            }
        }

        Validator.assertTrue(hasPrimaryKey, ErrCodeSys.SYS_ERR_MSG, "更新数据必须包含主键");

        // 长度校验
        for (SysMatrixColumn column : columns) {
            String columnName = column.getColumnName();
            Object value = data.get(columnName);

            if (FuncUtil.isNotEmpty(value) && value instanceof String) {
                String strValue = (String) value;
                if (column.getColumnLength() != null && strValue.length() > column.getColumnLength()) {
                    Validator.assertTrue(false, ErrCodeSys.SYS_ERR_MSG,
                            "字段[" + column.getColumnComment() + "]长度不能超过" + column.getColumnLength());
                }
            }
        }
    }
}
