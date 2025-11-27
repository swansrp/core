package com.bidr.forge.service.martix;

import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.admin.dao.repository.SysPortalColumnService;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.constant.dict.MatrixStatusDict;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixChangeLog;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixChangeLogService;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.engine.PortalDataMode;
import com.bidr.forge.vo.matrix.SysMatrixVO;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 矩阵配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMatrixPortalService extends BasePortalService<SysMatrix, SysMatrixVO> {

    private final SysMatrixService sysMatrixService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final SysMatrixChangeLogService sysMatrixChangeLogService;
    private final JdbcConnectService jdbcConnectService;
    private final SysMatrixDDLSerivce sysMatrixDDLSerivce;
    private final SysPortalService sysPortalService;
    private final SysPortalColumnService sysPortalColumnService;

    // 用于暂存更新前的表注释
    private final ThreadLocal<String> oldTableCommentHolder = new ThreadLocal<>();

    /**
     * 新增矩阵前，检查表名是否可用
     */
    @Override
    public void beforeAdd(SysMatrix sysMatrix) {
        sysMatrixDDLSerivce.checkTableNameAvailability(sysMatrix.getTableName(), sysMatrix.getDataSource());
    }

    /**
     * 更新矩阵前，保存旧的表注释
     */
    @Override
    public void beforeUpdate(SysMatrix sysMatrix) {
        super.beforeUpdate(sysMatrix);

        // 查询更新前的数据，保存旧注释
        SysMatrix oldMatrix = sysMatrixService.getById(sysMatrix.getId());
        if (oldMatrix != null) {
            oldTableCommentHolder.set(oldMatrix.getTableComment());
        }
    }

    /**
     * 新增矩阵后，自动创建ID主键字段
     */
    @Override
    public void afterAdd(SysMatrix sysMatrix) {
        super.afterAdd(sysMatrix);

        // 自动创建ID主键字段
        SysMatrixColumn idColumn = new SysMatrixColumn();
        idColumn.setMatrixId(sysMatrix.getId());
        idColumn.setColumnName("id");
        idColumn.setColumnComment("主键ID");
        idColumn.setColumnType("BIGINT");
        idColumn.setColumnLength(20);
        idColumn.setFieldType(PortalFieldDict.NUMBER.getValue());
        idColumn.setIsNullable(CommonConst.NO);
        idColumn.setIsPrimaryKey(CommonConst.YES);
        idColumn.setIsIndex(CommonConst.NO);
        idColumn.setIsUnique(CommonConst.NO);
        idColumn.setSequence("AUTO_INCREMENT");  // 设置自增序列
        idColumn.setSort(0);

        sysMatrixColumnService.save(idColumn);
    }

    /**
     * 更新矩阵后，处理表注释修改
     */
    @Override
    public void afterUpdate(SysMatrix sysMatrix) {
        super.afterUpdate(sysMatrix);

        try {
            // 从 ThreadLocal 获取旧注释
            String oldTableComment = oldTableCommentHolder.get();
            String newTableComment = sysMatrix.getTableComment();

            // 只在表已创建且注释发生变化时执行DDL
            if ((MatrixStatusDict.CREATED.getValue().equals(sysMatrix.getStatus()) ||
                    MatrixStatusDict.SYNCED.getValue().equals(sysMatrix.getStatus()) ||
                    MatrixStatusDict.PENDING_SYNC.getValue().equals(sysMatrix.getStatus())) &&
                    !java.util.Objects.equals(oldTableComment, newTableComment)) {

                // 获取版本号
                Integer version = getNextVersion(sysMatrix.getId());

                // 构建DDL语句
                String ddl = "ALTER TABLE `" + sysMatrix.getTableName() + "` COMMENT '" +
                        (newTableComment != null ? newTableComment : "") + "';";

                // 切换数据源（如果配置了）
                if (sysMatrix.getDataSource() != null && !sysMatrix.getDataSource().isEmpty()) {
                    jdbcConnectService.switchDataSource(sysMatrix.getDataSource());
                }

                try {
                    // 执行DDL
                    jdbcConnectService.executeUpdate(ddl, new HashMap<>(0));

                    // 记录成功日志
                    logChange(sysMatrix.getId(), version, "7",
                            "修改表注释: " + oldTableComment + " -> " + newTableComment,
                            ddl, null, "1", null);

                } catch (Exception e) {
                    // 记录失败日志
                    logChange(sysMatrix.getId(), version, "7",
                            "修改表注释: " + oldTableComment + " -> " + newTableComment,
                            ddl, null, "0", e.getMessage());
                    throw new RuntimeException("修改表注释失败: " + e.getMessage(), e);
                } finally {
                    // 重置数据源
                    if (sysMatrix.getDataSource() != null && !sysMatrix.getDataSource().isEmpty()) {
                        jdbcConnectService.resetToDefaultDataSource();
                    }
                }
            }
        } finally {
            // 清理 ThreadLocal 防止内存泄漏
            oldTableCommentHolder.remove();
        }
    }

    /**
     * 删除矩阵前检查数据
     */
    @Override
    public void beforeDelete(IdReqVO vo) {
        // 查询要删除的矩阵
        SysMatrix matrix = getRepo().selectById(vo.getId());

        // 只有表已创建的情况才检查数据
        if (MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) ||
                MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus())) {
            // 切换数据源（如果配置了）
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.switchDataSource(matrix.getDataSource());
            }

            try {
                // 检查表中是否有数据
                String checkSql = "SELECT COUNT(*) as count FROM `" + matrix.getTableName() + "`";
                List<Map<String, Object>> result = jdbcConnectService.executeQuery(checkSql, new HashMap<>(0));

                if (!result.isEmpty()) {
                    long count = ((Number) result.get(0).get("count")).longValue();
                    Validator.assertTrue(count == 0, ErrCodeSys.SYS_ERR_MSG, "表 [" + matrix.getTableName() + "] 中存在数据（" + count + " 条），无法删除矩阵配置，请先清空表数据");
                }
            } catch (Exception e) {
                // 如果查询失败（比如表不存在），允许删除
                // 不做任何处理
            } finally {
                // 重置数据源
                if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                    jdbcConnectService.resetToDefaultDataSource();
                }
            }
        }

    }

    /**
     * 删除矩阵后删除物理表和关联的Portal配置
     */
    @Override
    public void afterDelete(IdReqVO vo) {
        // 查询要删除的矩阵（此时valid已为0，需要直接查询）
        SysMatrix matrix = getRepo().getById(vo.getId());

        if (matrix == null) {
            return;
        }

        // 删除关联的Portal配置
        try {
            List<Long> deletedPortalIds = sysPortalService.deleteByDataModeAndReferenceId(
                    PortalDataMode.MATRIX.name(),
                    matrix.getId()
            );
            if (FuncUtil.isNotEmpty(deletedPortalIds)) {
                sysPortalColumnService.deleteByPortalIds(deletedPortalIds);
                log.info("删除Matrix[id={}]时，同步删除了 {} 个Portal配置", matrix.getId(), deletedPortalIds.size());
            }
        } catch (Exception e) {
            log.error("删除Matrix[id={}]关联的Portal配置失败", matrix.getId(), e);
        }

        // 只有表已创建的情况才删除物理表
        if (MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) ||
                MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus())) {

            // 切换数据源（如果配置了）
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.switchDataSource(matrix.getDataSource());
            }

            try {
                // 删除物理表
                String dropSql = "DROP TABLE IF EXISTS `" + matrix.getTableName() + "`";
                jdbcConnectService.executeUpdate(dropSql, new HashMap<>(0));
            } catch (Exception e) {
                // 如果删除失败（比如表不存在），忽略错误
                // 错误信息已由JdbcConnectService记录
            } finally {
                // 重置数据源
                if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                    jdbcConnectService.resetToDefaultDataSource();
                }
            }
        }

        super.afterDelete(vo);
    }

    /**
     * 清空表数据（只有超级管理员才能执行）
     *
     * @param matrixId 矩阵ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void truncateTable(Long matrixId) {
        // 检查是否为超级管理员
        Validator.assertTrue(isAdmin(), ErrCodeSys.SYS_ERR_MSG, "只有超级管理员才能执行清空表数据操作");

        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建，无法清空数据");
        }

        // 切换数据源（如果配置了）
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            // 执行 TRUNCATE TABLE
            String truncateSql = "TRUNCATE TABLE `" + matrix.getTableName() + "`";
            jdbcConnectService.executeUpdate(truncateSql, new HashMap<>(0));
        } finally {
            // 重置数据源
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

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

        if (MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) ||
                MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表已创建，无法重复创建");
        }

        // 检查表名是否已存在于数据库中
        // 切换数据源（如果配置了）
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            Validator.assertTrue(!isTableExists(matrix.getTableName()), ErrCodeSys.SYS_ERR_MSG,
                    "表 [" + matrix.getTableName() + "] 已存在于数据库中，无法创建");
        } finally {
            // 重置数据源
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }

        // 查询字段配置
        MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
        List<SysMatrixColumn> columns = matrixColumns.getColumns();

        if (columns.isEmpty()) {
            throw new RuntimeException("请先配置表字段");
        }

        // 获取当前版本号
        Integer version = getNextVersion(matrixId);

        // 构建CREATE TABLE DDL
        String ddl = sysMatrixDDLSerivce.buildCreateTableDDL(matrix, columns);

        // 切换数据源（如果配置了）
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            // 执行建表语句
            jdbcConnectService.executeUpdate(ddl, new HashMap<>(0));

            // 记录成功日志
            logChange(matrixId, version, "1", "创建表 " + matrix.getTableName(),
                    ddl, null, "1", null);

            // 更新状态为已创建
            matrix.setStatus(MatrixStatusDict.CREATED.getValue());
            sysMatrixService.updateById(matrix);
        } catch (Exception e) {
            // 记录失败日志
            logChange(matrixId, version, "1", "创建表 " + matrix.getTableName(),
                    ddl, null, "0", e.getMessage());
            throw e;
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

        if (!MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus()) &&
                !MatrixStatusDict.PENDING_SYNC.getValue().equals(matrix.getStatus())) {
            throw new RuntimeException("表未创建，请先创建表");
        }

        // 查询字段配置
        MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
        List<SysMatrixColumn> columns = matrixColumns.getColumns();

        // 获取当前版本号
        Integer version = getNextVersion(matrixId);

        // 切换数据源（如果配置了）
        if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(matrix.getDataSource());
        }

        try {
            // 获取表中已存在的字段（按实际顺序）
            List<String> existingColumns = getExistingColumns(matrix.getTableName());

            // 获取配置的字段名列表
            List<String> configuredColumns = columns.stream()
                    .map(SysMatrixColumn::getColumnName)
                    .collect(java.util.stream.Collectors.toList());

            // 记录本次同步中新添加的字段，避免对新添加的字段再次调整位置
            Set<String> newlyAddedColumns = new java.util.HashSet<>();

            // 处理字段：添加新字段并调整顺序
            for (int i = 0; i < columns.size(); i++) {
                SysMatrixColumn column = columns.get(i);
                String columnName = column.getColumnName();

                if (!existingColumns.contains(columnName)) {
                    // 新字段：添加字段（ADD COLUMN 已包含位置信息）
                    String addColumnDDL = buildAlterTableAddColumnDDL(matrix, column, columns, i);
                    try {
                        jdbcConnectService.executeUpdate(addColumnDDL, new HashMap<>(0));
                        // 记录成功日志
                        logChange(matrixId, version, "2", "添加字段 " + columnName,
                                addColumnDDL, columnName, "1", null);
                        // 标记为新添加的字段
                        newlyAddedColumns.add(columnName);
                    } catch (Exception e) {
                        // 记录失败日志
                        logChange(matrixId, version, "2", "添加字段 " + columnName,
                                addColumnDDL, columnName, "0", e.getMessage());
                    }
                } else if (!newlyAddedColumns.contains(columnName)) {
                    // 已存在字段且不是本次新添加的：检查位置是否需要调整
                    // 计算期望位置：当前字段在配置中的索引 i
                    // 计算实际位置：在 existingColumns 中的索引
                    int actualPosition = existingColumns.indexOf(columnName);
                    int expectedPosition = i;

                    // 只有位置不一致时才调整
                    if (actualPosition != expectedPosition) {
                        String modifyColumnDDL = buildAlterTableModifyColumnDDL(matrix, column, columns, i);
                        try {
                            jdbcConnectService.executeUpdate(modifyColumnDDL, new HashMap<>(0));
                            // 记录成功日志
                            logChange(matrixId, version, "3", "调整字段顺序 " + columnName,
                                    modifyColumnDDL, columnName, "1", null);
                            // 更新 existingColumns 中的位置，以便后续字段比较
                            existingColumns.remove(actualPosition);
                            existingColumns.add(expectedPosition, columnName);
                        } catch (Exception e) {
                            // 字段位置可能已正确，继续执行
                            // 不记录日志，因为这是预期的失败
                        }
                    }
                }
            }

            // 处理需要删除的字段（存在于表中但不在配置中）
            for (String existingColumn : existingColumns) {
                if (!configuredColumns.contains(existingColumn)) {
                    // 需要删除的字段
                    String dropColumnDDL = "ALTER TABLE `" + matrix.getTableName() + "` DROP COLUMN `" + existingColumn + "`";
                    try {
                        jdbcConnectService.executeUpdate(dropColumnDDL, new HashMap<>(0));
                        // 记录成功日志
                        logChange(matrixId, version, "6", "删除字段 " + existingColumn,
                                dropColumnDDL, existingColumn, "1", null);
                    } catch (Exception e) {
                        // 记录失败日志
                        logChange(matrixId, version, "6", "删除字段 " + existingColumn,
                                dropColumnDDL, existingColumn, "0", e.getMessage());
                    }
                }
            }

            // 同步索引
            syncIndexes(matrix, columns);

            // 更新状态为已同步
            matrix.setStatus(MatrixStatusDict.SYNCED.getValue());
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
        // 委托给 SysMatrixDDLSerivce 处理
        return sysMatrixDDLSerivce.buildCreateTableDDL(matrix, columns);
    }

    /**
     * 检查表是否存在
     *
     * @param tableName 表名
     * @return true-存在，false-不存在
     */
    private boolean isTableExists(String tableName) {
        String sql = "SELECT COUNT(*) as count FROM INFORMATION_SCHEMA.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "'";
        List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql, new HashMap<>(0));
        if (!result.isEmpty()) {
            long count = ((Number) result.get(0).get("count")).longValue();
            return count > 0;
        }
        return false;
    }

    /**
     * 获取表中已存在的字段列表（排除审计字段）
     */
    private List<String> getExistingColumns(String tableName) {
        String sql = "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' ORDER BY " +
                "ORDINAL_POSITION";
        List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql, new HashMap<>(0));
        // 定义审计字段，这些字段不参与同步
        Set<String> auditFields = new java.util.HashSet<>(java.util.Arrays.asList(
                "create_by", "create_at", "update_by", "update_at", "valid"
        ));
        return result.stream()
                .map(row -> (String) row.get("COLUMN_NAME"))
                .filter(columnName -> !auditFields.contains(columnName))  // 过滤掉审计字段
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 同步索引
     */
    private void syncIndexes(SysMatrix matrix, List<SysMatrixColumn> columns) {
        // 获取当前版本号
        Integer version = getNextVersion(matrix.getId());

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
                        jdbcConnectService.executeUpdate(createIndexSQL, new HashMap<>(0));
                        // 记录成功日志
                        logChange(matrix.getId(), version, "4", "添加索引 " + indexName,
                                createIndexSQL, columnName, "1", null);
                    } catch (Exception e) {
                        // 记录失败日志
                        logChange(matrix.getId(), version, "4", "添加索引 " + indexName,
                                createIndexSQL, columnName, "0", e.getMessage());
                    }
                }
            } else {
                // 如果字段不再需要索引，删除索引
                String indexName = "idx_" + columnName;
                if (existingIndexes.containsKey(indexName)) {
                    String dropIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` DROP INDEX `" + indexName + "`;";
                    try {
                        jdbcConnectService.executeUpdate(dropIndexSQL, new HashMap<>(0));
                        // 记录成功日志
                        logChange(matrix.getId(), version, "5", "删除索引 " + indexName,
                                dropIndexSQL, columnName, "1", null);
                    } catch (Exception e) {
                        // 记录失败日志
                        logChange(matrix.getId(), version, "5", "删除索引 " + indexName,
                                dropIndexSQL, columnName, "0", e.getMessage());
                    }
                }
            }

            // 处理唯一索引
            if ("1".equals(column.getIsUnique())) {
                String indexName = "uk_" + columnName;
                if (!existingIndexes.containsKey(indexName)) {
                    String createIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` ADD UNIQUE INDEX `" + indexName + "` (`" + columnName + "`);";
                    try {
                        jdbcConnectService.executeUpdate(createIndexSQL, new HashMap<>(0));
                        // 记录成功日志
                        logChange(matrix.getId(), version, "4", "添加唯一索引 " + indexName,
                                createIndexSQL, columnName, "1", null);
                    } catch (Exception e) {
                        // 记录失败日志
                        logChange(matrix.getId(), version, "4", "添加唯一索引 " + indexName,
                                createIndexSQL, columnName, "0", e.getMessage());
                    }
                }
            } else {
                // 如果字段不再需要唯一索引，删除索引
                String indexName = "uk_" + columnName;
                if (existingIndexes.containsKey(indexName)) {
                    String dropIndexSQL = "ALTER TABLE `" + matrix.getTableName() + "` DROP INDEX `" + indexName + "`;";
                    try {
                        jdbcConnectService.executeUpdate(dropIndexSQL, new HashMap<>(0));
                        // 记录成功日志
                        logChange(matrix.getId(), version, "5", "删除唯一索引 " + indexName,
                                dropIndexSQL, columnName, "1", null);
                    } catch (Exception e) {
                        // 记录失败日志
                        logChange(matrix.getId(), version, "5", "删除唯一索引 " + indexName,
                                dropIndexSQL, columnName, "0", e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * 获取表中已存在的索引
     *
     * @return Map<索引名, 索引类型>
     */
    private Map<String, String> getExistingIndexes(String tableName) {
        String sql = "SELECT INDEX_NAME, NON_UNIQUE FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '" + tableName + "' " +
                "AND INDEX_NAME != 'PRIMARY'";
        List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql, new HashMap<>(0));
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
        // 统计主键字段数量
        long primaryKeyCount = allColumns.stream()
                .filter(col -> "1".equals(col.getIsPrimaryKey()))
                .count();

        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE `").append(matrix.getTableName()).append("` ADD COLUMN ");
        ddl.append(buildColumnDefinition(column, primaryKeyCount));

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
        // 统计主键字段数量
        long primaryKeyCount = allColumns.stream()
                .filter(col -> "1".equals(col.getIsPrimaryKey()))
                .count();

        StringBuilder ddl = new StringBuilder();
        ddl.append("ALTER TABLE `").append(matrix.getTableName()).append("` MODIFY COLUMN ");
        ddl.append(buildColumnDefinition(column, primaryKeyCount));

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
     *
     * @param column          字段信息
     * @param primaryKeyCount 主键字段总数
     */
    private String buildColumnDefinition(SysMatrixColumn column, long primaryKeyCount) {
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

        // 主键处理：只有单主键且是数字类型才添加 AUTO_INCREMENT
        // 联合主键不添加 AUTO_INCREMENT
        if ("1".equals(column.getIsPrimaryKey()) && primaryKeyCount == 1) {
            String columnType = column.getColumnType().toUpperCase();
            if (columnType.contains("INT") || columnType.contains("BIGINT") ||
                    columnType.contains("SMALLINT") || columnType.contains("TINYINT")) {
                def.append(" AUTO_INCREMENT");
            }
        }

        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            // 主键不设置默认值
            if (!"1".equals(column.getIsPrimaryKey())) {
                def.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
            }
        }

        if (column.getColumnComment() != null && !column.getColumnComment().isEmpty()) {
            def.append(" COMMENT '").append(column.getColumnComment()).append("'");
        }

        return def.toString();
    }

    /**
     * 获取下一个版本号
     */
    private Integer getNextVersion(Long matrixId) {
        SysMatrixChangeLog lastLog = sysMatrixChangeLogService.lambdaQuery()
                .eq(SysMatrixChangeLog::getMatrixId, matrixId)
                .orderByDesc(SysMatrixChangeLog::getVersion)
                .last("LIMIT 1")
                .one();
        return lastLog == null ? 1 : lastLog.getVersion() + 1;
    }

    /**
     * 记录变更日志（去重检查）
     */
    private void logChange(Long matrixId, Integer version, String changeType, String changeDesc,
                           String ddlStatement, String affectedColumn, String executeStatus, String errorMsg) {
        // 检查是否已存在相同DDL语句的成功记录
        SysMatrixChangeLog existingLog = sysMatrixChangeLogService.lambdaQuery()
                .eq(SysMatrixChangeLog::getMatrixId, matrixId)
                .eq(SysMatrixChangeLog::getDdlStatement, ddlStatement)
                .eq(SysMatrixChangeLog::getExecuteStatus, "1")  // 只检查成功的记录
                .last("LIMIT 1")
                .one();

        // 如果已存在相同的成功DDL记录，且当前也是成功的，则不重复记录
        if (existingLog != null && "1".equals(executeStatus)) {
            log.debug("跳过重复DDL记录: matrixId={}, ddl={}", matrixId, ddlStatement);
            return;
        }

        SysMatrixChangeLog changeLog = new SysMatrixChangeLog();
        changeLog.setMatrixId(matrixId);
        changeLog.setVersion(version);
        changeLog.setChangeType(changeType);
        changeLog.setChangeDesc(changeDesc);
        changeLog.setDdlStatement(ddlStatement);
        changeLog.setAffectedColumn(affectedColumn);
        changeLog.setExecuteStatus(executeStatus);
        changeLog.setErrorMsg(errorMsg);
        changeLog.setSort(version);
        sysMatrixChangeLogService.save(changeLog);
    }

    /**
     * 导出表的DDL语句
     *
     * @param matrixId 矩阵ID
     * @return DDL语句
     */
    public String exportDDL(Long matrixId) {
        return sysMatrixDDLSerivce.exportDDL(matrixId);
    }

    /**
     * 通过DDL语句导入表结构
     *
     * @param ddl DDL语句
     * @return 矩阵ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long importDDL(String ddl) {
        return sysMatrixDDLSerivce.importDDL(ddl);
    }
}
