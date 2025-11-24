package com.bidr.forge.service;

import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.constant.dict.MatrixStatusDict;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 动态矩阵数据操作Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
@RequiredArgsConstructor
public class MatrixDataService {

    private final SysMatrixService sysMatrixService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 插入数据
     *
     * @param matrixId 矩阵ID
     * @param data     数据
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int insert(Long matrixId, Map<String, Object> data) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("INSERT INTO `").append(matrix.getTableName()).append("` (");

            List<String> columns = data.keySet().stream()
                    .map(key -> "`" + key + "`")
                    .collect(Collectors.toList());
            sql.append(String.join(", ", columns));
            sql.append(") VALUES (");

            List<String> values = data.values().stream()
                    .map(value -> {
                        if (value == null) {
                            return "NULL";
                        } else if (value instanceof Number) {
                            return value.toString();
                        } else {
                            return "'" + value.toString().replace("'", "''") + "'";
                        }
                    })
                    .collect(Collectors.toList());
            sql.append(String.join(", ", values));
            sql.append(")");

            return jdbcConnectService.update(sql.toString(), new HashMap<>());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 更新数据
     *
     * @param matrixId 矩阵ID
     * @param id       主键ID
     * @param data     数据
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int update(Long matrixId, Object id, Map<String, Object> data) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("UPDATE `").append(matrix.getTableName()).append("` SET ");

            List<String> sets = data.entrySet().stream()
                    .map(entry -> {
                        String column = "`" + entry.getKey() + "`";
                        Object value = entry.getValue();
                        if (value == null) {
                            return column + " = NULL";
                        } else if (value instanceof Number) {
                            return column + " = " + value;
                        } else {
                            return column + " = '" + value.toString().replace("'", "''") + "'";
                        }
                    })
                    .collect(Collectors.toList());
            sql.append(String.join(", ", sets));

            // 从字段配置获取主键
            MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
            List<SysMatrixColumn> primaryKeyColumns = matrixColumns.getColumns().stream()
                    .filter(col -> "1".equals(col.getIsPrimaryKey()))
                    .collect(Collectors.toList());

            if (primaryKeyColumns.isEmpty()) {
                throw new RuntimeException("表未配置主键字段");
            }

            // 构建 WHERE 条件（支持联合主键）
            sql.append(" WHERE ");
            if (primaryKeyColumns.size() == 1) {
                // 单主键
                String primaryKey = primaryKeyColumns.get(0).getColumnName();
                sql.append("`").append(primaryKey).append("` = ");
                if (id instanceof Number) {
                    sql.append(id);
                } else {
                    sql.append("'").append(id.toString().replace("'", "''")).append("'");
                }
            } else {
                // 联合主键：id 应该是 Map 类型
                if (!(id instanceof Map)) {
                    throw new RuntimeException("联合主键表的 id 参数必须为 Map 类型");
                }
                Map<String, Object> idMap = (Map<String, Object>) id;
                List<String> conditions = new java.util.ArrayList<>();
                for (SysMatrixColumn pkColumn : primaryKeyColumns) {
                    String columnName = pkColumn.getColumnName();
                    Object value = idMap.get(columnName);
                    if (value == null) {
                        throw new RuntimeException("缺少主键字段: " + columnName);
                    }
                    String condition = "`" + columnName + "` = ";
                    if (value instanceof Number) {
                        condition += value;
                    } else {
                        condition += "'" + value.toString().replace("'", "''") + "'";
                    }
                    conditions.add(condition);
                }
                sql.append(String.join(" AND ", conditions));
            }

            return jdbcConnectService.update(sql.toString(), new HashMap<>());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 删除数据
     *
     * @param matrixId 矩阵ID
     * @param id       主键ID
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long matrixId, Object id) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("DELETE FROM `").append(matrix.getTableName()).append("`");

            // 从字段配置获取主键
            MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
            List<SysMatrixColumn> primaryKeyColumns = matrixColumns.getColumns().stream()
                    .filter(col -> "1".equals(col.getIsPrimaryKey()))
                    .collect(Collectors.toList());

            if (primaryKeyColumns.isEmpty()) {
                throw new RuntimeException("表未配置主键字段");
            }

            // 构建 WHERE 条件（支持联合主键）
            sql.append(" WHERE ");
            if (primaryKeyColumns.size() == 1) {
                // 单主键
                String primaryKey = primaryKeyColumns.get(0).getColumnName();
                sql.append("`").append(primaryKey).append("` = ");
                if (id instanceof Number) {
                    sql.append(id);
                } else {
                    sql.append("'").append(id.toString().replace("'", "''")).append("'");
                }
            } else {
                // 联合主键：id 应该是 Map 类型
                if (!(id instanceof Map)) {
                    throw new RuntimeException("联合主键表的 id 参数必须为 Map 类型");
                }
                Map<String, Object> idMap = (Map<String, Object>) id;
                List<String> conditions = new java.util.ArrayList<>();
                for (SysMatrixColumn pkColumn : primaryKeyColumns) {
                    String columnName = pkColumn.getColumnName();
                    Object value = idMap.get(columnName);
                    if (value == null) {
                        throw new RuntimeException("缺少主键字段: " + columnName);
                    }
                    String condition = "`" + columnName + "` = ";
                    if (value instanceof Number) {
                        condition += value;
                    } else {
                        condition += "'" + value.toString().replace("'", "''") + "'";
                    }
                    conditions.add(condition);
                }
                sql.append(String.join(" AND ", conditions));
            }

            return jdbcConnectService.update(sql.toString(), new HashMap<>());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 查询单条数据
     *
     * @param matrixId 矩阵ID
     * @param id       主键ID
     * @return 数据
     */
    public Map<String, Object> selectById(Long matrixId, Object id) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM `").append(matrix.getTableName()).append("`");

            // 从字段配置获取主键
            MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
            List<SysMatrixColumn> primaryKeyColumns = matrixColumns.getColumns().stream()
                    .filter(col -> "1".equals(col.getIsPrimaryKey()))
                    .collect(Collectors.toList());

            if (primaryKeyColumns.isEmpty()) {
                throw new RuntimeException("表未配置主键字段");
            }

            // 构建 WHERE 条件（支持联合主键）
            sql.append(" WHERE ");
            if (primaryKeyColumns.size() == 1) {
                // 单主键
                String primaryKey = primaryKeyColumns.get(0).getColumnName();
                sql.append("`").append(primaryKey).append("` = ");
                if (id instanceof Number) {
                    sql.append(id);
                } else {
                    sql.append("'").append(id.toString().replace("'", "''")).append("'");
                }
            } else {
                // 联合主键：id 应该是 Map 类型
                if (!(id instanceof Map)) {
                    throw new RuntimeException("联合主键表的 id 参数必须为 Map 类型");
                }
                Map<String, Object> idMap = (Map<String, Object>) id;
                List<String> conditions = new java.util.ArrayList<>();
                for (SysMatrixColumn pkColumn : primaryKeyColumns) {
                    String columnName = pkColumn.getColumnName();
                    Object value = idMap.get(columnName);
                    if (value == null) {
                        throw new RuntimeException("缺少主键字段: " + columnName);
                    }
                    String condition = "`" + columnName + "` = ";
                    if (value instanceof Number) {
                        condition += value;
                    } else {
                        condition += "'" + value.toString().replace("'", "''") + "'";
                    }
                    conditions.add(condition);
                }
                sql.append(String.join(" AND ", conditions));
            }

            return jdbcConnectService.queryOne(sql.toString(), new HashMap<>());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 查询列表
     *
     * @param matrixId 矩阵ID
     * @return 数据列表
     */
    public List<Map<String, Object>> selectList(Long matrixId) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            String sql = "SELECT * FROM `" + matrix.getTableName() + "`";
            return jdbcConnectService.query(sql, new HashMap<>());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 根据条件查询
     *
     * @param matrixId  矩阵ID
     * @param condition 查询条件
     * @return 数据列表
     */
    public List<Map<String, Object>> selectByCondition(Long matrixId, Map<String, Object> condition) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM `").append(matrix.getTableName()).append("`");

            if (condition != null && !condition.isEmpty()) {
                sql.append(" WHERE ");
                List<String> conditions = condition.entrySet().stream()
                        .map(entry -> {
                            String column = "`" + entry.getKey() + "`";
                            Object value = entry.getValue();
                            if (value == null) {
                                return column + " IS NULL";
                            } else if (value instanceof Number) {
                                return column + " = " + value;
                            } else {
                                return column + " = '" + value.toString().replace("'", "''") + "'";
                            }
                        })
                        .collect(Collectors.toList());
                sql.append(String.join(" AND ", conditions));
            }

            return jdbcConnectService.query(sql.toString(), new HashMap<>());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }
}
