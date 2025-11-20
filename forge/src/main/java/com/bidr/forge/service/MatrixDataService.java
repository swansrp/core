package com.bidr.forge.service;

import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.kernel.service.JdbcConnectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * @param data    数据
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int insert(Long matrixId, Map<String, Object> data) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
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

            return jdbcConnectService.executeUpdate(sql.toString());
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
     * @param id      主键ID
     * @param data    数据
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int update(Long matrixId, Object id, Map<String, Object> data) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
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

            String primaryKey = matrix.getPrimaryKey() != null ? matrix.getPrimaryKey() : "id";
            sql.append(" WHERE `").append(primaryKey).append("` = ");
            if (id instanceof Number) {
                sql.append(id);
            } else {
                sql.append("'").append(id.toString().replace("'", "''")).append("'");
            }

            return jdbcConnectService.executeUpdate(sql.toString());
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
     * @param id      主键ID
     * @return 影响行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long matrixId, Object id) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("DELETE FROM `").append(matrix.getTableName()).append("`");

            String primaryKey = matrix.getPrimaryKey() != null ? matrix.getPrimaryKey() : "id";
            sql.append(" WHERE `").append(primaryKey).append("` = ");
            if (id instanceof Number) {
                sql.append(id);
            } else {
                sql.append("'").append(id.toString().replace("'", "''")).append("'");
            }

            return jdbcConnectService.executeUpdate(sql.toString());
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
     * @param id      主键ID
     * @return 数据
     */
    public Map<String, Object> selectById(Long matrixId, Object id) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT * FROM `").append(matrix.getTableName()).append("`");

            String primaryKey = matrix.getPrimaryKey() != null ? matrix.getPrimaryKey() : "id";
            sql.append(" WHERE `").append(primaryKey).append("` = ");
            if (id instanceof Number) {
                sql.append(id);
            } else {
                sql.append("'").append(id.toString().replace("'", "''")).append("'");
            }

            return jdbcConnectService.executeQueryOne(sql.toString());
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

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建");
        }

        // 切换数据源
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            String sql = "SELECT * FROM `" + matrix.getTableName() + "`";
            return jdbcConnectService.executeQuery(sql);
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 根据条件查询
     *
     * @param matrixId   矩阵ID
     * @param condition 查询条件
     * @return 数据列表
     */
    public List<Map<String, Object>> selectByCondition(Long matrixId, Map<String, Object> condition) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
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

            return jdbcConnectService.executeQuery(sql.toString());
        } finally {
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }
}
