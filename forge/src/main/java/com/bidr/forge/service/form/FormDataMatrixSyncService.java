package com.bidr.forge.service.form;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.FormSchemaAttributeService;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FormData同步到动态Matrix表服务
 * 当FormData创建或更新时，如果关联的FormSchemaAttribute有matrixColumnId，
 * 则将数据同步到对应的动态Matrix表中
 * <p>
 * 动态表结构说明：
 * - 主键：id (UUID类型)
 * - 关联字段：history_id (用于定位记录)
 *
 * @author sharp
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormDataMatrixSyncService {

    private final FormSchemaAttributeService formSchemaAttributeService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final SysMatrixService sysMatrixService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 死锁重试最大次数
     */
    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 死锁重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL_MS = 50;

    /**
     * 同步FormData到动态Matrix表
     * 在FormData创建后调用，向动态表插入或更新字段值
     *
     * @param formData 表单数据
     */
    public void syncToMatrix(FormData formData) {
        if (formData == null || formData.getAttributeId() == null) {
            return;
        }

        // 1. 查找FormSchemaAttribute
        FormSchemaAttribute attribute = formSchemaAttributeService.getById(formData.getAttributeId());
        if (attribute == null || attribute.getMatrixColumnId() == null) {
            log.debug("FormData [{}] 的属性未配置 matrixColumnId，跳过同步", formData.getId());
            return;
        }

        // 2. 查找SysMatrixColumn
        SysMatrixColumn matrixColumn = sysMatrixColumnService.getById(attribute.getMatrixColumnId());
        if (matrixColumn == null) {
            log.warn("FormData [{}] 的 matrixColumnId [{}] 不存在", formData.getId(), attribute.getMatrixColumnId());
            return;
        }

        // 3. 查找SysMatrix获取表名和数据源
        SysMatrix matrix = sysMatrixService.getById(matrixColumn.getMatrixId());
        if (matrix == null) {
            log.warn("FormData [{}] 的 matrixId [{}] 不存在", formData.getId(), matrixColumn.getMatrixId());
            return;
        }

        // 4. 构建参数并执行SQL（带死锁重试）
        String tableName = matrix.getTableName();
        String columnName = matrixColumn.getColumnName();
        String dataSource = matrix.getDataSource();

        executeWithRetry(tableName, columnName, dataSource, formData);
    }

    /**
     * 带死锁重试的执行方法
     *
     * @param tableName         表名
     * @param columnName        列名
     * @param dataSource        数据源
     * @param formData          表单数据
     */
    private void executeWithRetry(String tableName, String columnName, String dataSource, FormData formData) {
        int retryCount = 0;
        while (true) {
            try {
                doSync(tableName, columnName, dataSource, formData);
                return;
            } catch (DeadlockLoserDataAccessException e) {
                retryCount++;
                if (retryCount >= MAX_RETRY_COUNT) {
                    log.error("同步FormData [{}] 到Matrix [{}] 死锁重试次数超限，放弃重试", formData.getId(), tableName, e);
                    throw e;
                }
                log.warn("同步FormData [{}] 到Matrix [{}] 发生死锁，第 {} 次重试", formData.getId(), tableName, retryCount);
                try {
                    Thread.sleep(RETRY_INTERVAL_MS * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("线程被中断", ie);
                }
            }
        }
    }

    /**
     * 执行同步操作
     *
     * @param tableName         表名
     * @param columnName        列名
     * @param dataSource        数据源
     * @param formData          表单数据
     */
    private void doSync(String tableName, String columnName, String dataSource, FormData formData) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("value", formData.getValue());
        parameters.put("historyId", formData.getHistoryId());
        parameters.put("sectionInstanceId", formData.getSectionInstanceId());

        try {
            // 切换数据源
            if (FuncUtil.isNotEmpty(dataSource)) {
                jdbcConnectService.switchDataSource(dataSource);
            }

            // 获取当前操作人
            String operator = AccountContext.getOperator();
            parameters.put("updateBy", operator);
            parameters.put("createBy", operator);

            // 使用 INSERT ... ON DUPLICATE KEY UPDATE 避免竞态条件
            String uuid = UUID.randomUUID().toString().replace("-", "");
            parameters.put("id", uuid);

            String upsertSql = buildUpsertSql(tableName, columnName);
            int affected = jdbcConnectService.update(upsertSql, parameters);
            log.debug("同步FormData [{}] 到Matrix [{}] 成功，影响行数: {}", formData.getId(), tableName, affected);
        } finally {
            if (FuncUtil.isNotEmpty(dataSource)) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 构建 INSERT ... ON DUPLICATE KEY UPDATE SQL
     * 使用 upsert 语法避免先查询后更新的竞态条件，减少死锁概率
     *
     * @param tableName  表名
     * @param columnName  列名
     * @return SQL语句
     */
    private String buildUpsertSql(String tableName, String columnName) {
        return "INSERT INTO `" + tableName + "` (`id`, `history_id`, `section_instance_id`, `" + columnName + "`, `create_by`, `update_by`) " +
                "VALUES (:id, :historyId, :sectionInstanceId, :value, :createBy, :updateBy) " +
                "ON DUPLICATE KEY UPDATE `" + columnName + "` = :value, `update_by` = :updateBy";
    }

    /**
     * 构建更新SQL（保留用于特殊场景）
     * 通过 history_id 和 section_instance_id 联合定位记录，更新指定字段
     */
    private String buildUpdateSql(String tableName, String columnName) {
        return "UPDATE `" + tableName + "` SET `" + columnName + "` = :value, `update_by` = :updateBy WHERE `history_id` = :historyId AND `section_instance_id` = :sectionInstanceId";
    }
}
