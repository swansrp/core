package com.bidr.forge.engine.driver;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.engine.DriverCapability;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.engine.builder.MatrixSqlBuilder;
import com.bidr.forge.engine.builder.SqlBuilder;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdPidReqVO;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.kernel.vo.common.TreeDataItemVO;
import com.bidr.kernel.vo.common.TreeDataResVO;
import com.bidr.kernel.vo.portal.AdvancedQuery;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.statistic.AdvancedStatisticReq;
import com.bidr.kernel.vo.portal.statistic.AdvancedSummaryReq;
import com.bidr.kernel.vo.portal.statistic.StatisticRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
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
public class MatrixDriver implements PortalDriver<Map<String, Object>> {

    private final SysMatrixService sysMatrixService;
    private final JdbcConnectService jdbcConnectService;
    private final SysPortalService sysPortalService;

    @Override
    public DriverCapability getCapability() {
        return DriverCapability.fullSupport();
    }

    @Override
    public PortalDataMode getDataMode() {
        return PortalDataMode.MATRIX;
    }

    /**
     * 构建别名映射<VO字段名, 数据库列名>
     * @param portalName Portal名称
     * @param roleId     角色ID
     * @return 别名映射
     */


    @Override
    public SqlBuilder getSqlBuilder(String portalName, Long roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");
        return new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
    }

    @Override
    public JdbcConnectService getJdbcConnectService() {
        return jdbcConnectService;
    }

    @Override
    public String getDataSource(String portalName, Long roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        return matrixColumns != null ? matrixColumns.getDataSource() : null;
    }

    @Override
    public Page<Map<String, Object>> queryPage(AdvancedQueryReq req, String portalName, Long roleId) {
        return doQueryPage(req, portalName, roleId);
    }

    @Override
    public List<Map<String, Object>> queryList(AdvancedQueryReq req, String portalName, Long roleId) {
        return doQueryList(req, portalName, roleId);
    }

    @Override
    public Map<String, Object> queryOne(AdvancedQueryReq req, String portalName, Long roleId) {
        return doQueryOne(req, portalName, roleId);
    }

    @Override
    public Map<String, Object> selectById(Object id, String portalName, Long roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");

        // 构建查询条件（主键 AND 连接）
        AdvancedQueryReq req = new AdvancedQueryReq();
        AdvancedQuery condition = new AdvancedQuery();
        condition.setAndOr(AdvancedQuery.AND);

        List<SysMatrixColumn> pkColumns = matrixColumns.getColumns().stream()
                .filter(col -> CommonConst.YES.equals(col.getIsPrimaryKey()))
                .collect(java.util.stream.Collectors.toList());
        Validator.assertTrue(!pkColumns.isEmpty(), ErrCodeSys.SYS_ERR_MSG, "未定义主键，无法按ID查询");

        if (pkColumns.size() == 1) {
            // 单主键
            String pkName = pkColumns.get(0).getColumnName();
            condition.addCondition(new ConditionVO(pkName, id));
        } else {
            // 复合主键：要求传入 Map<String, Object>
            Validator.assertTrue(id instanceof Map, ErrCodeSys.SYS_ERR_MSG, "联合主键查询需要传入Map类型ID");
            @SuppressWarnings("unchecked")
            Map<String, Object> pkMap = (Map<String, Object>) id;
            for (SysMatrixColumn pkCol : pkColumns) {
                String pkName = pkCol.getColumnName();
                Object pkValue = pkMap.get(pkName);
                Validator.assertTrue(FuncUtil.isNotEmpty(pkValue), ErrCodeSys.SYS_ERR_MSG,
                        "联合主键缺少字段: " + pkName);
                condition.addCondition(new ConditionVO(pkName, pkValue));
            }
        }
        req.setCondition(condition);

        // 使用统一默认方法执行（含数据源切换、SQL构建与查询）
        return doQueryOne(req, portalName, roleId);
    }

    @Override
    public List<Map<String, Object>> getAllData(String portalName, Long roleId) {
        AdvancedQueryReq req = new AdvancedQueryReq();
        // 添加valid=YES条件
        return queryList(req, portalName, roleId);
    }

    @Override
    public int insert(Map<String, Object> data, String portalName, Long roleId) {
        MatrixColumns matrixColumns = getMatrixColumns(portalName);
        Validator.assertNotNull(matrixColumns, ErrCodeSys.PA_DATA_NOT_EXIST, "矩阵配置");
        Map<String, String> aliasMap = buildAliasMap(portalName, roleId);
        // 数据校验
        validateInsertData(data,aliasMap, matrixColumns.getColumns());

        // 切换数据源
        if (FuncUtil.isNotEmpty(matrixColumns.getDataSource())) {
            jdbcConnectService.switchDataSource(matrixColumns.getDataSource());
        }

        try {
            Map<String, Object> parameters = new HashMap<>();
            MatrixSqlBuilder builder = new MatrixSqlBuilder(matrixColumns, matrixColumns.getColumns());
            String insertSql = builder.buildInsert(data, aliasMap, parameters);
            return jdbcConnectService.update(insertSql, parameters);
        } finally {
            jdbcConnectService.resetToDefaultDataSource();
        }
    }

    @Override
    public int batchInsert(List<Map<String, Object>> dataList, String portalName, Long roleId) {
        int totalAffected = 0;
        for (Map<String, Object> data : dataList) {
            totalAffected += insert(data, portalName, roleId);
        }
        return totalAffected;
    }

    @Override
    public int update(Map<String, Object> data, String portalName, Long roleId) {
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
    public int batchUpdate(List<Map<String, Object>> dataList, String portalName, Long roleId) {
        int totalAffected = 0;
        for (Map<String, Object> data : dataList) {
            totalAffected += update(data, portalName, roleId);
        }
        return totalAffected;
    }

    @Override
    public int delete(Object id, String portalName, Long roleId) {
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
    public int batchDelete(List<Object> ids, String portalName, Long roleId) {
        int totalAffected = 0;
        for (Object id : ids) {
            totalAffected += delete(id, portalName, roleId);
        }
        return totalAffected;
    }

    // ========== 统计方法（暂不支持） ==========


    @Override
    public Long count(AdvancedQueryReq req, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Matrix模式暂不支持统计计数操作，请使用count方法");
    }

    @Override
    public Map<String, Object> summary(AdvancedSummaryReq req, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Matrix模式暂不支持汇总统计操作");
    }

    @Override
    public List<StatisticRes> statistic(AdvancedStatisticReq req, String portalName, Long roleId) {
        throw new UnsupportedOperationException("Matrix模式暂不支持指标统计操作");
    }

    /**
     * 获取Matrix配置和列
     */
    private MatrixColumns getMatrixColumns(String portalName) {
        // 尝试按tableName查询
        return sysMatrixService.getMatrixColumnsByPortalName(portalName);
    }



    /**
     * 校验插入数据
     */
    private void validateInsertData(Map<String, Object> data,Map<String, String> aliasMap, List<SysMatrixColumn> columns) {
        for (SysMatrixColumn column : columns) {
            String columnName = column.getColumnName();
            columnName = findVoColumnName(columnName, aliasMap);
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

    @Override
    public List<TreeDataResVO> getTreeData(String portalName, Long roleId) {
        return Collections.emptyList();
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
