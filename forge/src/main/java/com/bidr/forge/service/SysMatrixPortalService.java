package com.bidr.forge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.vo.SysMatrixVO;
import com.bidr.kernel.service.JdbcConnectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 矩阵配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
@RequiredArgsConstructor
public class SysMatrixPortalService extends BasePortalService<SysMatrix, SysMatrixVO> {

    private final SysMatrixService sysMatrixService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 创建物理表
     *
     * @param matrixId 矩阵ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void createPhysicalTable(Long matrixId) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if ("1".equals(matrix.getStatus()) || "2".equals(matrix.getStatus())) {
            throw new RuntimeException("表已创建，无法重复创建");
        }

        // 查询字段配置
        List<SysMatrixColumn> columns = sysMatrixColumnService.list(
                new LambdaQueryWrapper<SysMatrixColumn>()
                        .eq(SysMatrixColumn::getMatrixId, matrixId)
                        .orderByAsc(SysMatrixColumn::getSort)
        );

        if (columns.isEmpty()) {
            throw new RuntimeException("请先配置表字段");
        }

        // 构建CREATE TABLE DDL
        String ddl = buildCreateTableDDL(matrix, columns);

        // 切换数据源（如果配置了）
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            // 执行建表语句
            jdbcConnectService.executeUpdate(ddl);

            // 更新状态为已创建
            matrix.setStatus("1");
            sysMatrixService.updateById(matrix);
        } finally {
            // 重置数据源
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 同步表结构
     *
     * @param matrixId 矩阵ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncTableStructure(Long matrixId) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!"1".equals(matrix.getStatus()) && !"2".equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建，请先创建表");
        }

        // 查询字段配置
        List<SysMatrixColumn> columns = sysMatrixColumnService.list(
                new LambdaQueryWrapper<SysMatrixColumn>()
                        .eq(SysMatrixColumn::getMatrixId, matrixId)
                        .orderByAsc(SysMatrixColumn::getSort)
        );

        // 切换数据源（如果配置了）
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            // 获取表中已存在的字段
            List<String> existingColumns = getExistingColumns(matrix.getTableName());

            // 处理字段：添加新字段并调整顺序
            for (int i = 0; i < columns.size(); i++) {
                SysMatrixColumn column = columns.get(i);
                String columnName = column.getColumnName();

                if (!existingColumns.contains(columnName)) {
                    // 新字段：添加字段
                    String addColumnDDL = buildAlterTableAddColumnDDL(matrix, column, columns, i);
                    jdbcConnectService.executeUpdate(addColumnDDL);
                } else {
                    // 已存在字段：调整位置
                    String modifyColumnDDL = buildAlterTableModifyColumnDDL(matrix, column, columns, i);
                    try {
                        jdbcConnectService.executeUpdate(modifyColumnDDL);
                    } catch (Exception e) {
                        // 字段位置可能已正确，继续执行
                    }
                }
            }

            // 同步索引
            syncIndexes(matrix, columns);

            // 更新状态为已同步
            matrix.setStatus("2");
            sysMatrixService.updateById(matrix);
        } finally {
            // 重置数据源
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 构建CREATE TABLE DDL
     */
    private String buildCreateTableDDL(SysMatrix matrix, List<SysMatrixColumn> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(matrix.getTableName()).append("` (\n");

        // 字段定义
        for (int i = 0; i < columns.size(); i++) {
            SysMatrixColumn column = columns.get(i);
            ddl.append("  `").append(column.getColumnName()).append("` ");
            ddl.append(column.getColumnType());

            if (column.getColumnLength() != null && column.getColumnLength() > 0) {
                ddl.append("(").append(column.getColumnLength());
                if (column.getDecimalPlaces() != null && column.getDecimalPlaces() > 0) {
                    ddl.append(",").append(column.getDecimalPlaces());
                }
                ddl.append(")");
            }

            if (!"1".equals(column.getIsNullable())) {
                ddl.append(" NOT NULL");
            }

            if ("1".equals(column.getIsPrimaryKey())) {
                ddl.append(" AUTO_INCREMENT");
            }

            if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
                ddl.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
            }

            if (column.getColumnComment() != null && !column.getColumnComment().isEmpty()) {
                ddl.append(" COMMENT '").append(column.getColumnComment()).append("'");
            }

            if (i < columns.size() - 1) {
                ddl.append(",\n");
            }
        }

        // 主键
        String primaryKey = matrix.getPrimaryKey() != null ? matrix.getPrimaryKey() : "id";
        ddl.append(",\n  PRIMARY KEY (`").append(primaryKey).append("`)");

        // 索引
        for (SysMatrixColumn column : columns) {
            if ("1".equals(column.getIsIndex())) {
                ddl.append(",\n  KEY `idx_").append(column.getColumnName()).append("` (`").append(column.getColumnName()).append("`)");
            }
            if ("1".equals(column.getIsUnique())) {
                ddl.append(",\n  UNIQUE KEY `uk_").append(column.getColumnName()).append("` (`").append(column.getColumnName()).append("`)");
            }
        }

        ddl.append("\n) ENGINE=").append(matrix.getEngine() != null ? matrix.getEngine() : "InnoDB");
        ddl.append(" DEFAULT CHARSET=").append(matrix.getCharset() != null ? matrix.getCharset() : "utf8mb4");

        if (matrix.getTableComment() != null && !matrix.getTableComment().isEmpty()) {
            ddl.append(" COMMENT='").append(matrix.getTableComment()).append("'");
        }

        ddl.append(";");
        return ddl.toString();
    }

    /**
     * 获取表中已存在的字段列表
     */
    private List<String> getExistingColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "'";
        List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql);
        return result.stream()
                .map(row -> (String) row.get("COLUMN_NAME"))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 同步索引
     */
    private void syncIndexes(SysMatrix matrix, List<SysMatrixColumn> columns) {
        // 获取已存在的索引
        Map<String, String> existingIndexes = getExistingIndexes(matrix.getTableName());
        
        // 需要创建的索引
        for (SysMatrixColumn column : columns) {
            String columnName = column.getColumnName();
            
            // 处理普通索引
            if ("1".equals(column.getIsIndex())) {
                String indexName = "idx_" + columnName;
                if (!existingIndexes.containsKey(indexName)) {
                    String createIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` ADD INDEX `" + indexName + "` (`" + columnName + "`);";
                    try {
                        jdbcConnectService.executeUpdate(createIndexSQL);
                    } catch (Exception e) {
                        // 索引可能已存在
                    }
                }
            } else {
                // 如果字段不再需要索引，删除索引
                String indexName = "idx_" + columnName;
                if (existingIndexes.containsKey(indexName)) {
                    String dropIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` DROP INDEX `" + indexName + "`;";
                    try {
                        jdbcConnectService.executeUpdate(dropIndexSQL);
                    } catch (Exception e) {
                        // 索引可能已不存在
                    }
                }
            }
            
            // 处理唯一索引
            if ("1".equals(column.getIsUnique())) {
                String indexName = "uk_" + columnName;
                if (!existingIndexes.containsKey(indexName)) {
                    String createIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` ADD UNIQUE INDEX `" + indexName + "` (`" + columnName + "`);";
                    try {
                        jdbcConnectService.executeUpdate(createIndexSQL);
                    } catch (Exception e) {
                        // 索引可能已存在
                    }
                }
            } else {
                // 如果字段不再需要唯一索引，删除索引
                String indexName = "uk_" + columnName;
                if (existingIndexes.containsKey(indexName)) {
                    String dropIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` DROP INDEX `" + indexName + "`;";
                    try {
                        jdbcConnectService.executeUpdate(dropIndexSQL);
                    } catch (Exception e) {
                        // 索引可能已不存在
                    }
                }
            }
        }
    }

    /**
     * 获取表中已存在的索引
     * @return Map<索引名, 索引类型>
     */
    private Map<String, String> getExistingIndexes(String tableName) {
        String sql = "SELECT INDEX_NAME, NON_UNIQUE FROM INFORMATION_SCHEMA.STATISTICS " +
                     "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                     "AND INDEX_NAME != 'PRIMARY'";
        List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql);
        Map<String, String> indexes = new java.util.HashMap<>();
        for (Map<String, Object> row : result) {
            String indexName = (String) row.get("INDEX_NAME");
            Long nonUnique = (Long) row.get("NON_UNIQUE");
            indexes.put(indexName, nonUnique == 1 ? "INDEX" : "UNIQUE");
        }
        return indexes;
    }

    /**
     * 构建ALTER TABLE ADD COLUMN DDL（带位置）
     */
    private String buildAlterTableAddColumnDDL(SysMatrix matrix, SysMatrixColumn column, List<SysMatrixColumn> allColumns, int index) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE `").append(matrix.getTableName()).append("` ADD COLUMN ");
        ddl.append(buildColumnDefinition(column));

        // 添加位置信息
        if (index == 0) {
            ddl.append(" FIRST");
        } else {
            // 放在前一个字段后面
            SysMatrixColumn prevColumn = allColumns.get(index - 1);
            ddl.append(" AFTER `").append(prevColumn.getColumnName()).append("`");
        }

        ddl.append(";");
        return ddl.toString();
    }

    /**
     * 构建ALTER TABLE MODIFY COLUMN DDL（调整位置）
     */
    private String buildAlterTableModifyColumnDDL(SysMatrix matrix, SysMatrixColumn column, List<SysMatrixColumn> allColumns, int index) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE `").append(matrix.getTableName()).append("` MODIFY COLUMN ");
        ddl.append(buildColumnDefinition(column));

        // 添加位置信息
        if (index == 0) {
            ddl.append(" FIRST");
        } else {
            // 放在前一个字段后面
            SysMatrixColumn prevColumn = allColumns.get(index - 1);
            ddl.append(" AFTER `").append(prevColumn.getColumnName()).append("`");
        }

        ddl.append(";");
        return ddl.toString();
    }

    /**
     * 构建字段定义部分（复用代码）
     */
    private String buildColumnDefinition(SysMatrixColumn column) {
        StringBuilder def = new StringBuilder();
        def.append("`").append(column.getColumnName()).append("` ");
        def.append(column.getColumnType());

        if (column.getColumnLength() != null && column.getColumnLength() > 0) {
            def.append("(").append(column.getColumnLength());
            if (column.getDecimalPlaces() != null && column.getDecimalPlaces() > 0) {
                def.append(",").append(column.getDecimalPlaces());
            }
            def.append(")");
        }

        if (!"1".equals(column.getIsNullable())) {
            def.append(" NOT NULL");
        }

        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            def.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
        }

        if (column.getColumnComment() != null && !column.getColumnComment().isEmpty()) {
            def.append(" COMMENT '").append(column.getColumnComment()).append("'");
        }

        return def.toString();
    }
}
