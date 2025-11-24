package com.bidr.forge.service.martix;

import com.bidr.admin.constant.dict.PortalFieldDict;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.constant.dict.MatrixStatusDict;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Title: SysMatrixDDLService
 * Description: 矩阵DDL导入导出服务
 * Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/11/22 16:29
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMatrixDDLSerivce {

    private final SysMatrixService sysMatrixService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 导出表的DDL语句
     *
     * @param matrixId 矩阵ID
     * @return DDL语句
     */
    public String exportDDL(Long matrixId) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix == null) {
            throw new RuntimeException("矩阵配置不存在");
        }

        // 查询字段配置
        MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
        List<SysMatrixColumn> columns = matrixColumns.getColumns();

        if (columns.isEmpty()) {
            throw new RuntimeException("表没有配置字段");
        }

        // 构建DDL
        return buildCreateTableDDL(matrix, columns);
    }

    /**
     * 通过DDL语句导入表结构
     *
     * @param ddl DDL语句
     * @return 矩阵ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long importDDL(String ddl) {
        if (ddl == null || ddl.trim().isEmpty()) {
            throw new RuntimeException("DDL语句不能为空");
        }

        // 解析DDL语句
        DDLParser parser = new DDLParser(ddl);

        // 导入前进行表名存在性检查
        checkTableNameAvailability(parser.getTableName(), null);

        // 创建矩阵配置
        SysMatrix matrix = new SysMatrix();
        matrix.setTableName(parser.getTableName());
        matrix.setTableComment(parser.getTableComment());
        matrix.setEngine(parser.getEngine());
        matrix.setCharset(parser.getCharset());
        matrix.setStatus(MatrixStatusDict.NOT_CREATED.getValue());
        matrix.setSort(0);
        sysMatrixService.save(matrix);

        // 创建字段配置
        List<DDLParser.ColumnInfo> columnInfos = parser.getColumns();

        for (int i = 0; i < columnInfos.size(); i++) {
            DDLParser.ColumnInfo columnInfo = columnInfos.get(i);

            SysMatrixColumn column = new SysMatrixColumn();
            column.setMatrixId(matrix.getId());
            column.setColumnName(columnInfo.getName());
            column.setColumnComment(columnInfo.getComment());
            column.setColumnType(columnInfo.getType());
            column.setColumnLength(columnInfo.getLength());
            column.setDecimalPlaces(columnInfo.getDecimalPlaces());
            column.setIsNullable(columnInfo.getNullable() ? CommonConst.YES : CommonConst.NO);
            column.setDefaultValue(columnInfo.getDefaultValue());
            column.setIsPrimaryKey(columnInfo.getPrimaryKey() ? CommonConst.YES : CommonConst.NO);
            column.setIsIndex(columnInfo.getHasIndex() ? CommonConst.YES : CommonConst.NO);
            column.setIsUnique(columnInfo.getUnique() ? CommonConst.YES : CommonConst.NO);
            column.setSort(i + 1);

            // 设置自增序列
            if (columnInfo.getAutoIncrement()) {
                column.setSequence("AUTO_INCREMENT");
                log.info("从DDL导入时检测到字段 [{}] 设置了 AUTO_INCREMENT，已配置到 sequence 字段", columnInfo.getName());
            }

            // 推断表单字段类型
            column.setFieldType(inferFieldType(columnInfo.getType()));

            sysMatrixColumnService.save(column);
        }

        return matrix.getId();
    }

    /**
     * 构建CREATE TABLE DDL
     */
    public String buildCreateTableDDL(SysMatrix matrix, List<SysMatrixColumn> columns) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS `").append(matrix.getTableName()).append("` (\n");

        // 先统计主键字段数量
        long primaryKeyCount = columns.stream()
                .filter(col -> "1".equals(col.getIsPrimaryKey()))
                .count();

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

            // 主键处理：只有单主键且是数字类型才添加 AUTO_INCREMENT
            // 联合主键不添加 AUTO_INCREMENT
            if ("1".equals(column.getIsPrimaryKey()) && primaryKeyCount == 1) {
                String columnType = column.getColumnType().toUpperCase();
                if (columnType.contains("INT") || columnType.contains("BIGINT") ||
                        columnType.contains("SMALLINT") || columnType.contains("TINYINT")) {
                    ddl.append(" AUTO_INCREMENT");
                }
            }

            // 默认值处理：主键如果是序列类型（字符串），不设置默认值
            if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
                if (!"1".equals(column.getIsPrimaryKey())) {
                    ddl.append(" DEFAULT '").append(column.getDefaultValue()).append("'");
                }
                // 如果是主键且是序列，defaultValue 存储的是序列名，在插入数据时使用
            }

            if (column.getColumnComment() != null && !column.getColumnComment().isEmpty()) {
                ddl.append(" COMMENT '").append(column.getColumnComment()).append("'");
            }

            ddl.append(",\n");
        }

        // 添加默认的审计字段（不存入MatrixColumn表）
        ddl.append("  `create_by` varchar(50) DEFAULT NULL COMMENT '创建者',\n");
        ddl.append("  `create_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',\n");
        ddl.append("  `update_by` varchar(50) DEFAULT NULL COMMENT '更新者',\n");
        ddl.append("  `update_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',\n");
        ddl.append("  `valid` char(1) DEFAULT '1' COMMENT '有效性'");

        // 主键（从字段配置中获取）
        List<String> primaryKeys = new java.util.ArrayList<>();
        for (SysMatrixColumn column : columns) {
            if ("1".equals(column.getIsPrimaryKey())) {
                primaryKeys.add(column.getColumnName());
            }
        }

        if (!primaryKeys.isEmpty()) {
            ddl.append(",\n  PRIMARY KEY (");
            for (int i = 0; i < primaryKeys.size(); i++) {
                if (i > 0) {
                    ddl.append(",");
                }
                ddl.append("`").append(primaryKeys.get(i)).append("`");
            }
            ddl.append(")");
        }

        // 为 update_at 添加默认索引
        ddl.append(",\n  KEY `idx_update_at` (`update_at`)");

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
     * 检查表名是否可用（统一校验方法）
     * 1. 检查 sys_matrix 表中是否已存在有效的同名配置
     * 2. 检查数据库中是否已存在同名物理表
     *
     * @param tableName  表名
     * @param dataSource 数据源名称
     */
    public void checkTableNameAvailability(String tableName, String dataSource) {
        // 1. 检查 sys_matrix 表中是否已存在有效的同名配置
        long count = sysMatrixService.lambdaQuery()
                .eq(SysMatrix::getTableName, tableName)
                .eq(SysMatrix::getValid, CommonConst.YES)
                .count();
        Validator.assertTrue(count == 0, ErrCodeSys.SYS_ERR_MSG,
                "矩阵配置表中已存在名为 [" + tableName + "] 的有效配置，无法创建重复配置");

        // 2. 检查数据库中是否已存在同名物理表
        // 切换数据源（如果配置了）
        if (dataSource != null && !dataSource.isEmpty()) {
            jdbcConnectService.switchDataSource(dataSource);
        }

        try {
            Validator.assertTrue(!isTableExists(tableName), ErrCodeSys.SYS_ERR_MSG,
                    "数据库中已存在名为 [" + tableName + "] 的物理表，无法创建");
        } finally {
            // 重置数据源
            if (dataSource != null && !dataSource.isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
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
        List<Map<String, Object>> result = jdbcConnectService.executeQuery(sql);
        if (!result.isEmpty()) {
            long count = ((Number) result.get(0).get("count")).longValue();
            return count > 0;
        }
        return false;
    }

    /**
     * 根据数据库类型推断表单字段类型
     */
    private String inferFieldType(String columnType) {
        String upperType = columnType.toUpperCase();

        if (upperType.contains("INT") || upperType.contains("BIGINT")) {
            return PortalFieldDict.NUMBER.getValue();
        } else if (upperType.contains("DECIMAL") || upperType.contains("DOUBLE") || upperType.contains("FLOAT")) {
            return PortalFieldDict.MONEY.getValue();
        } else if (upperType.contains("DATE") && upperType.contains("TIME")) {
            return PortalFieldDict.DATETIME.getValue();
        } else if (upperType.contains("DATE")) {
            return PortalFieldDict.DATE.getValue();
        } else if (upperType.contains("TEXT")) {
            return PortalFieldDict.TEXT.getValue();
        } else {
            return PortalFieldDict.STRING.getValue();
        }
    }

    /**
     * DDL解析器内部类
     */
    @lombok.Data
    private static class DDLParser {
        private final String ddl;
        private String tableName;
        private String tableComment;
        private List<String> primaryKeys = new java.util.ArrayList<>();  // 支持联合主键
        private String engine = "InnoDB";
        private String charset = "utf8mb4";
        private final List<ColumnInfo> columns = new java.util.ArrayList<>();
        private final Map<String, Boolean> indexes = new java.util.HashMap<>();
        private final Map<String, Boolean> uniqueIndexes = new java.util.HashMap<>();

        public DDLParser(String ddl) {
            this.ddl = ddl;
            parse();
        }

        private void parse() {
            // 预处理DDL：移除注释和无关语句
            String cleanedDDL = preprocessDDL(ddl);

            // 解析表名
            java.util.regex.Pattern tablePattern = java.util.regex.Pattern.compile(
                    "CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`?([a-zA-Z0-9_]+)`?",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher tableMatcher = tablePattern.matcher(cleanedDDL);
            if (tableMatcher.find()) {
                tableName = tableMatcher.group(1);
            }

            // 解析表注释
            java.util.regex.Pattern commentPattern = java.util.regex.Pattern.compile(
                    "COMMENT\\s*=\\s*'([^']+)'",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher commentMatcher = commentPattern.matcher(cleanedDDL);
            if (commentMatcher.find()) {
                tableComment = commentMatcher.group(1);
            }

            // 解析引擎
            java.util.regex.Pattern enginePattern = java.util.regex.Pattern.compile(
                    "ENGINE\\s*=\\s*([a-zA-Z0-9]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher engineMatcher = enginePattern.matcher(cleanedDDL);
            if (engineMatcher.find()) {
                engine = engineMatcher.group(1);
            }

            // 解析字符集
            java.util.regex.Pattern charsetPattern = java.util.regex.Pattern.compile(
                    "CHARSET\\s*=\\s*([a-zA-Z0-9_]+)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher charsetMatcher = charsetPattern.matcher(cleanedDDL);
            if (charsetMatcher.find()) {
                charset = charsetMatcher.group(1);
            }

            // 解析主键（支持联合主键）
            java.util.regex.Pattern pkPattern = java.util.regex.Pattern.compile(
                    "PRIMARY\\s+KEY\\s*\\(([^)]+)\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher pkMatcher = pkPattern.matcher(cleanedDDL);
            if (pkMatcher.find()) {
                String pkFields = pkMatcher.group(1);
                // 解析多个主键字段
                String[] fields = pkFields.split(",");
                for (String field : fields) {
                    String cleanField = field.trim().replaceAll("`", "");
                    primaryKeys.add(cleanField);
                }
            }

            // 解析索引
            java.util.regex.Pattern indexPattern = java.util.regex.Pattern.compile(
                    "(?:KEY|INDEX)\\s+`?idx_([a-zA-Z0-9_]+)`?\\s*\\(\\s*`?([a-zA-Z0-9_]+)`?\\s*\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher indexMatcher = indexPattern.matcher(cleanedDDL);
            while (indexMatcher.find()) {
                String columnName = indexMatcher.group(2);
                indexes.put(columnName, true);
            }

            // 解析唯一索引
            java.util.regex.Pattern uniquePattern = java.util.regex.Pattern.compile(
                    "UNIQUE\\s+(?:KEY|INDEX)\\s+`?uk_([a-zA-Z0-9_]+)`?\\s*\\(\\s*`?([a-zA-Z0-9_]+)`?\\s*\\)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher uniqueMatcher = uniquePattern.matcher(cleanedDDL);
            while (uniqueMatcher.find()) {
                String columnName = uniqueMatcher.group(2);
                uniqueIndexes.put(columnName, true);
            }

            // 解析字段定义（支持多种换行符：\r\n、\n、\r）
            String[] lines = cleanedDDL.split("\\r?\\n|\\r");
            boolean inColumns = false;

            for (String line : lines) {
                line = line.trim();

                if (line.toUpperCase().startsWith("CREATE TABLE")) {
                    inColumns = true;
                    continue;
                }

                if (!inColumns || line.isEmpty()) {
                    continue;
                }

                // 跳过审计字段（这些字段不存入 MatrixColumn 表）
                if (line.contains("`create_by`") || line.contains("`create_at`") ||
                        line.contains("`update_by`") || line.contains("`update_at`") ||
                        line.contains("`valid`")) {
                    continue;
                }

                // 跳过主键、索引定义行
                if (line.toUpperCase().contains("PRIMARY KEY") ||
                        line.toUpperCase().startsWith("KEY ") ||
                        line.toUpperCase().startsWith("INDEX ") ||
                        line.toUpperCase().startsWith("UNIQUE") ||
                        line.startsWith(")")) {
                    continue;
                }

                // 解析字段定义
                ColumnInfo columnInfo = parseColumnLine(line);
                if (columnInfo != null) {
                    columns.add(columnInfo);
                }
            }
        }

        /**
         * 预处理DDL：移除注释和无关语句
         */
        private String preprocessDDL(String ddl) {
            StringBuilder result = new StringBuilder();
            String[] lines = ddl.split("\\r?\\n|\\r");

            for (String line : lines) {
                String trimmedLine = line.trim();

                // 跳过空行
                if (trimmedLine.isEmpty()) {
                    continue;
                }

                // 跳过单行注释 -- ...
                if (trimmedLine.startsWith("--")) {
                    continue;
                }

                // 跳过 MySQL 特殊注释 /*! ... */
                if (trimmedLine.startsWith("/*!")) {
                    continue;
                }

                // 跳过普通注释 /* ... */
                if (trimmedLine.startsWith("/*")) {
                    continue;
                }

                // 跳过 DML 语句
                String upperLine = trimmedLine.toUpperCase();
                if (upperLine.startsWith("DELETE ") ||
                        upperLine.startsWith("INSERT ") ||
                        upperLine.startsWith("UPDATE ") ||
                        upperLine.startsWith("TRUNCATE ")) {
                    continue;
                }

                // 保留其他所有行（包括CREATE TABLE、字段定义、PRIMARY KEY、索引等）
                result.append(line).append("\n");
            }

            return result.toString();
        }

        private ColumnInfo parseColumnLine(String line) {
            // 移除末尾的逗号
            line = line.replaceAll(",$", "").trim();

            // 字段定义正则：`column_name` type(length) [NULL|NOT NULL] [DEFAULT value] [AUTO_INCREMENT] [COMMENT 'xxx']
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "`?([a-zA-Z0-9_]+)`?\\s+([a-zA-Z0-9]+)(?:\\(([0-9,]+)\\))?\\s*(.*)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );

            java.util.regex.Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                return null;
            }

            ColumnInfo info = new ColumnInfo();
            info.setName(matcher.group(1));
            info.setType(matcher.group(2).toUpperCase());

            // 解析长度和小数位
            String lengthPart = matcher.group(3);
            if (lengthPart != null) {
                String[] parts = lengthPart.split(",");
                info.setLength(Integer.parseInt(parts[0].trim()));
                if (parts.length > 1) {
                    info.setDecimalPlaces(Integer.parseInt(parts[1].trim()));
                }
            }

            // 解析其他属性
            String attributes = matcher.group(4);
            info.setNullable(!attributes.toUpperCase().contains("NOT NULL"));

            // 解析默认值
            java.util.regex.Pattern defaultPattern = java.util.regex.Pattern.compile(
                    "DEFAULT\\s+'?([^'\\s,]+)'?",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher defaultMatcher = defaultPattern.matcher(attributes);
            if (defaultMatcher.find()) {
                String defaultValue = defaultMatcher.group(1);
                if (!defaultValue.toUpperCase().contains("CURRENT_TIMESTAMP")) {
                    info.setDefaultValue(defaultValue);
                }
            }

            // 解析注释
            java.util.regex.Pattern commentPattern = java.util.regex.Pattern.compile(
                    "COMMENT\\s+'([^']+)'",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
            java.util.regex.Matcher commentMatcher = commentPattern.matcher(attributes);
            if (commentMatcher.find()) {
                info.setComment(commentMatcher.group(1));
            }

            // 解析自增属性
            if (attributes.toUpperCase().contains("AUTO_INCREMENT")) {
                info.setAutoIncrement(true);
            }

            // 设置主键标记（支持联合主键）
            if (primaryKeys.contains(info.getName())) {
                info.setPrimaryKey(true);
            }

            // 设置索引标记
            if (indexes.containsKey(info.getName())) {
                info.setHasIndex(true);
            }

            // 设置唯一标记
            if (uniqueIndexes.containsKey(info.getName())) {
                info.setUnique(true);
            }

            return info;
        }

        @lombok.Data
        static class ColumnInfo {
            private String name;
            private String comment;
            private String type;
            private Integer length;
            private Integer decimalPlaces;
            private Boolean nullable = true;
            private String defaultValue;
            private Boolean primaryKey = false;
            private Boolean hasIndex = false;
            private Boolean unique = false;
            private Boolean autoIncrement = false;  // 是否自增
        }
    }
}