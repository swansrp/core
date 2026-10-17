package com.bidr.kernel.mybatis.service;

import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 表结构操作服务
 * 提供安全的表级别操作，用于全量数据同步场景
 *
 * <p>核心架构：两表切换架构
 * <pre>
 * 三张固定表名：
 *   - 原表位置(A): 生产环境实际使用的表名，如 t_product
 *   - 影子表位置(B): 用于导入新数据，如 t_product_shadow
 *   - 备份表位置(C): 存放旧数据，如 t_product_backup
 *
 * 同步流程（每次都执行两表切换）：
 *   1. 删除旧备份表（如果存在）
 *   2. 原表(A) → 备份表(C)：旧数据存档
 *   3. 影子表(B) → 原表(A)：新数据上线
 *   4. 影子表位置空出，等待下次导入
 *
 * 优势：
 *   1. 固定表名，不会产生表名膨胀
 *   2. 始终有一次完整备份
 *   3. 零停机切换，原子操作
 *   4. 支持快速回滚
 * </pre>
 *
 * <p>安全措施：
 * <ul>
 *     <li>表名正则校验：只允许字母、数字、下划线</li>
 *     <li>支持 schema.table 格式</li>
 *     <li>可选白名单：只允许预定义的表</li>
 *     <li>元数据验证：确认表在数据库中真实存在</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/02/28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableSyncService {

    private final DataSource dataSource;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 表名合法正则：字母开头，只包含字母、数字、下划线，最长64字符
     */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");

    /**
     * 允许操作的表名白名单（可选）
     * 如果配置了白名单，则只允许白名单内的表进行操作
     */
    private Set<String> allowedTables = null;

    /**
     * 设置允许操作的表名白名单
     *
     * @param tables 表名集合
     */
    public void setAllowedTables(Set<String> tables) {
        this.allowedTables = new HashSet<>(tables);
    }

    /**
     * 校验单个名称（数据库名或表名）
     *
     * @param name 名称
     * @throws IllegalArgumentException 名称不合法
     */
    private void validateSingleName(String name) {
        if (FuncUtil.isEmpty(name)) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (!TABLE_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("非法名称格式: " + name + "，只允许字母开头，包含字母、数字、下划线，最长64字符");
        }
    }

    /**
     * 格式化表名为 SQL 语法格式
     * - table_name -> `table_name`
     * - schema.table_name -> `schema`.`table_name`
     *
     * @param tableName 表名
     * @return 格式化后的表名
     */
    private String formatTableName(String tableName) {
        String[] parts = tableName.split("\\.");
        if (parts.length == 2) {
            return String.format("`%s`.`%s`", parts[0], parts[1]);
        } else {
            return String.format("`%s`", tableName);
        }
    }

    /**
     * 校验表名格式合法性
     * 支持两种格式：
     * - table_name (仅表名)
     * - schema.table_name (数据库名.表名)
     *
     * @param tableName 表名
     * @throws IllegalArgumentException 表名不合法
     */
    public void validateTableName(String tableName) {
        if (FuncUtil.isEmpty(tableName)) {
            throw new IllegalArgumentException("表名不能为空");
        }
        
        // 支持 schema.table 格式
        String[] parts = tableName.split("\\.");
        if (parts.length == 2) {
            // schema.table 格式，分别校验
            validateSingleName(parts[0]);
            validateSingleName(parts[1]);
        } else if (parts.length == 1) {
            // 仅表名
            validateSingleName(parts[0]);
            // 白名单校验（如果配置了）
            if (allowedTables != null && !allowedTables.contains(tableName)) {
                throw new IllegalArgumentException("表名不在允许操作的白名单中: " + tableName);
            }
        } else {
            throw new IllegalArgumentException("非法表名格式: " + tableName + "，只允许 'table_name' 或 'schema.table_name' 格式");
        }
    }

    /**
     * 校验表在数据库中是否存在
     * 支持 schema.table 格式
     *
     * @param tableName 表名（支持 schema.table 格式）
     * @return 是否存在
     */
    public boolean tableExists(String tableName) {
        validateTableName(tableName);
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            
            String catalog = null;
            String actualTableName = tableName;
            
            // 解析 schema.table 格式
            String[] parts = tableName.split("\\.");
            if (parts.length == 2) {
                catalog = parts[0];
                actualTableName = parts[1];
            }
            
            try (ResultSet rs = metaData.getTables(catalog, null, actualTableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (Exception e) {
            log.error("检查表是否存在失败: {}", tableName, e);
            throw new RuntimeException("检查表是否存在失败: " + tableName, e);
        }
    }

    /**
     * 确保表存在，否则抛出异常
     *
     * @param tableName 表名
     * @throws IllegalStateException 表不存在
     */
    public void ensureTableExists(String tableName) {
        if (!tableExists(tableName)) {
            throw new IllegalStateException("表不存在: " + tableName);
        }
    }

    /**
     * 创建影子表（结构与原表相同）
     *
     * @param originalTableName 原表名
     * @param shadowTableName   影子表名
     * @return 是否创建成功
     */
    public boolean createShadowTable(String originalTableName, String shadowTableName) {
        validateTableName(originalTableName);
        validateTableName(shadowTableName);
        ensureTableExists(originalTableName);

        // 如果影子表已存在，先删除
        if (tableExists(shadowTableName)) {
            log.warn("影子表已存在，将被删除重建: {}", shadowTableName);
            dropTable(shadowTableName);
        }

        String sql = String.format("CREATE TABLE %s LIKE %s", formatTableName(shadowTableName), formatTableName(originalTableName));
        try {
            jdbcTemplate.update(sql, new HashMap<>());
            log.info("创建影子表成功: {} -> {}", originalTableName, shadowTableName);
            return true;
        } catch (Exception e) {
            log.error("创建影子表失败: {} -> {}", originalTableName, shadowTableName, e);
            throw new RuntimeException("创建影子表失败", e);
        }
    }

    /**
     * 清空表数据（TRUNCATE）
     * 注意：此操作不可回滚
     *
     * @param tableName 表名
     */
    public void truncateTable(String tableName) {
        validateTableName(tableName);
        ensureTableExists(tableName);

        String sql = String.format("TRUNCATE TABLE %s", formatTableName(tableName));
        try {
            jdbcTemplate.update(sql, new HashMap<>());
            log.info("清空表成功: {}", tableName);
        } catch (Exception e) {
            log.error("清空表失败: {}", tableName, e);
            throw new RuntimeException("清空表失败", e);
        }
    }

    /**
     * 删除表
     *
     * @param tableName 表名
     */
    public void dropTable(String tableName) {
        validateTableName(tableName);

        String sql = String.format("DROP TABLE IF EXISTS %s", formatTableName(tableName));
        try {
            jdbcTemplate.update(sql, new HashMap<>());
            log.info("删除表成功: {}", tableName);
        } catch (Exception e) {
            log.error("删除表失败: {}", tableName, e);
            throw new RuntimeException("删除表失败", e);
        }
    }

    /**
     * 原子两表切换
     *
     * <p>执行流程：
     * <pre>
     * 1. 如果备份表已存在，先删除（上次的旧数据）
     * 2. 原表(A) → 备份表(C)：旧数据存档
     * 3. 影子表(B) → 原表(A)：新数据上线
     * </pre>
     *
     * @param originalTableName 原表名（位置A）
     * @param shadowTableName   影子表名（位置B）
     * @param backupTableName   备份表名（位置C）
     */
    public void atomicSwitch(String originalTableName, String shadowTableName, String backupTableName) {
        validateTableName(originalTableName);
        validateTableName(shadowTableName);
        validateTableName(backupTableName);

        ensureTableExists(originalTableName);
        ensureTableExists(shadowTableName);

        boolean backupExists = tableExists(backupTableName);
        
        if (backupExists) {
            // 后续同步：先删除旧备份表，然后执行两表切换
            // 旧备份表里是上次的旧数据，可以安全删除
            log.info("后续同步，删除旧备份表: {}", backupTableName);
            dropTable(backupTableName);
        }
        
        // 两表切换（首次和后续都适用）
        // 原表(A) → 备份表(C)：旧数据存档
        // 影子表(B) → 原表(A)：新数据上线
        log.info("执行两表切换: {} → {}, {} → {}",
                originalTableName, backupTableName,
                shadowTableName, originalTableName);
        
        String sql = String.format(
                "RENAME TABLE %s TO %s, %s TO %s",
                formatTableName(originalTableName), formatTableName(backupTableName),
                formatTableName(shadowTableName), formatTableName(originalTableName)
        );
        
        try {
            jdbcTemplate.update(sql, new HashMap<>());
            log.info("两表切换成功: {} 已上线，旧数据存档到 {}", originalTableName, backupTableName);
        } catch (Exception e) {
            log.error("两表切换失败: original={}, shadow={}, backup={}",
                    originalTableName, shadowTableName, backupTableName, e);
            throw new RuntimeException("两表切换失败", e);
        }
    }

    /**
     * 原子两表切换（便捷封装）
     *
     * <p>自动生成影子表名和备份表名：
     * <ul>
     *     <li>影子表: {原表名}_shadow</li>
     *     <li>备份表: {原表名}_backup</li>
     * </ul>
     *
     * @param originalTableName 原表名
     */
    public void atomicSwitch(String originalTableName) {
        String shadowTableName = generateShadowTableName(originalTableName);
        String backupTableName = generateFixedBackupTableName(originalTableName);
        atomicSwitch(originalTableName, shadowTableName, backupTableName);
    }

    /**
     * 回滚切换
     * 
     * <p>用于切换后发现问题，需要回滚的场景
     *
     * <p>回滚流程（两表逆切换）：
     * <pre>
     * 当前状态：
     *   原表位置(A): 新数据（有问题）
     *   影子表位置(B): 不存在
     *   备份表位置(C): 旧数据（备份）
     *
     * 回滚后：
     *   原表位置(A): 旧数据（恢复正常）
     *   影子表位置(B): 新数据（有问题，待处理）
     *   备份表位置(C): 不存在
     * </pre>
     *
     * @param originalTableName 原表名
     */
    public void rollbackCycleSwitch(String originalTableName) {
        String shadowTableName = generateShadowTableName(originalTableName);
        String backupTableName = generateFixedBackupTableName(originalTableName);

        validateTableName(originalTableName);
        ensureTableExists(backupTableName);

        // 两表逆切换
        // A → B: 原表移动到影子表位置（新数据暂存）
        // C → A: 备份表移动到原表位置（旧数据恢复）
        String sql = String.format(
                "RENAME TABLE %s TO %s, %s TO %s",
                formatTableName(originalTableName), formatTableName(shadowTableName),
                formatTableName(backupTableName), formatTableName(originalTableName)
        );

        try {
            jdbcTemplate.update(sql, new HashMap<>());
            log.info("回滚切换成功: {} 恢复为旧数据", originalTableName);
        } catch (Exception e) {
            log.error("回滚切换失败: original={}", originalTableName, e);
            throw new RuntimeException("回滚切换失败", e);
        }
    }

    /**
     * 回滚切换（兼容旧方法，仅支持两表切换场景）
     * 用于切换后发现问题，需要回滚的场景
     *
     * @param originalTableName 原表名
     * @param backupTableName   备份表名
     * @deprecated 建议使用 rollbackCycleSwitch 进行三表循环架构回滚
     */
    @Deprecated
    public void rollbackSwitch(String originalTableName, String backupTableName) {
        validateTableName(originalTableName);
        validateTableName(backupTableName);

        ensureTableExists(backupTableName);

        // 当前原表改名为临时表
        String tempTableName = originalTableName + "_temp_" + System.currentTimeMillis();

        String sql = String.format(
                "RENAME TABLE %s TO %s, %s TO %s",
                formatTableName(originalTableName), formatTableName(tempTableName),
                formatTableName(backupTableName), formatTableName(originalTableName)
        );

        try {
            jdbcTemplate.update(sql, new HashMap<>());
            log.info("回滚切换成功: {} 恢复为 {}", backupTableName, originalTableName);

            // 删除临时表（原来的新数据）
            dropTable(tempTableName);
            log.info("清理临时表: {}", tempTableName);
        } catch (Exception e) {
            log.error("回滚切换失败: original={}, backup={}", originalTableName, backupTableName, e);
            throw new RuntimeException("回滚切换失败", e);
        }
    }

    /**
     * 获取表数据条数
     *
     * @param tableName 表名
     * @return 数据条数
     */
    public long getTableCount(String tableName) {
        validateTableName(tableName);
        ensureTableExists(tableName);

        String sql = String.format("SELECT COUNT(*) FROM %s", formatTableName(tableName));
        try {
            Long count = jdbcTemplate.queryForObject(sql, new HashMap<>(), Long.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取表数据条数失败: {}", tableName, e);
            throw new RuntimeException("获取表数据条数失败", e);
        }
    }

    /**
     * 获取备份表名（带日期后缀）
     *
     * @param originalTableName 原表名
     * @return 备份表名
     * @deprecated 三表循环架构使用固定表名，请使用 {@link #generateFixedBackupTableName(String)}
     */
    @Deprecated
    public String generateBackupTableName(String originalTableName) {
        validateTableName(originalTableName);
        return originalTableName + "_bak_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    /**
     * 获取影子表名
     *
     * @param originalTableName 原表名
     * @return 影子表名
     */
    public String generateShadowTableName(String originalTableName) {
        validateTableName(originalTableName);
        return originalTableName + "_shadow";
    }

    /**
     * 获取固定备份表名（用于三表循环架构）
     *
     * @param originalTableName 原表名
     * @return 备份表名
     */
    public String generateFixedBackupTableName(String originalTableName) {
        validateTableName(originalTableName);
        return originalTableName + "_backup";
    }

    /**
     * 批量删除备份表（清理历史备份）
     *
     * @param originalTableName 原表名
     * @param keepDays          保留天数
     * @deprecated 三表循环架构使用固定表名，不再产生历史备份表
     */
    @Deprecated
    public void cleanOldBackupTables(String originalTableName, int keepDays) {
        validateTableName(originalTableName);

        LocalDate threshold = LocalDate.now().minusDays(keepDays);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String pattern = originalTableName + "_bak_%";
            try (ResultSet rs = metaData.getTables(null, null, pattern, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    // 解析日期后缀
                    try {
                        String dateStr = tableName.substring(tableName.lastIndexOf("_bak_") + 5);
                        LocalDate backupDate = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
                        if (backupDate.isBefore(threshold)) {
                            dropTable(tableName);
                            log.info("清理过期备份表: {}", tableName);
                        }
                    } catch (Exception e) {
                        log.warn("无法解析备份表日期: {}", tableName);
                    }
                }
            }
        } catch (Exception e) {
            log.error("清理历史备份表失败: {}", originalTableName, e);
            throw new RuntimeException("清理历史备份表失败", e);
        }
    }

    /**
     * 全量同步完整流程
     * 
     * <p>流程：
     * <ol>
     *     <li>准备影子表（首次创建，后续清空）</li>
     *     <li>执行数据导入（回调）</li>
     *     <li>校验数据（回调）</li>
     *     <li>原子切换（删除旧备份，两表切换）</li>
     * </ol>
     *
     * @param originalTableName 原表名
     * @param dataImport        数据导入回调
     * @param validation        数据校验回调
     * @return 是否成功
     */
    public boolean fullSync(String originalTableName,
                            Runnable dataImport,
                            Runnable validation) {
        String shadowTableName = generateShadowTableName(originalTableName);
        String backupTableName = generateFixedBackupTableName(originalTableName);

        try {
            log.info("=== 开始全量同步: {} ===", originalTableName);

            // 1. 准备影子表
            prepare(originalTableName, shadowTableName);

            // 2. 执行数据导入
            log.info("开始导入数据到影子表: {}", shadowTableName);
            dataImport.run();

            // 3. 执行校验
            log.info("开始校验数据: {}", shadowTableName);
            validation.run();

            // 4. 原子切换
            atomicSwitch(originalTableName, shadowTableName, backupTableName);

            log.info("=== 全量同步完成: {} ===", originalTableName);
            return true;

        } catch (Exception e) {
            log.error("全量同步失败: {}", originalTableName, e);
            throw new RuntimeException("全量同步失败: " + originalTableName, e);
        }
    }

    public void prepare(String originalTableName, String shadowTableName) {
        if (tableExists(shadowTableName)) {
            // 影子表已存在（后续同步），清空数据
            log.info("影子表已存在，清空数据: {}", shadowTableName);
            truncateTable(shadowTableName);
        } else {
            // 影子表不存在（首次同步），创建影子表
            log.info("首次同步，创建影子表: {}", shadowTableName);
            createShadowTable(originalTableName, shadowTableName);
        }

    }

    public void prepare(String originalTableName) {
        prepare(originalTableName, generateShadowTableName(originalTableName));
    }
}
