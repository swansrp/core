package com.bidr.forge.service.form;

import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.FormSchemaAttributeService;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FormData同步到动态Matrix表服务
 * 当FormData创建或更新时，如果关联的FormSchemaAttribute有matrixColumnId，
 * 则将数据同步到对应的动态Matrix表中
 *
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

        // 4. 构建参数并执行SQL
        String tableName = matrix.getTableName();
        String columnName = matrixColumn.getColumnName();
        String dataSource = matrix.getDataSource();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("value", formData.getValue());
        parameters.put("historyId", formData.getHistoryId());

        try {
            // 切换数据源
            if (FuncUtil.isNotEmpty(dataSource)) {
                jdbcConnectService.switchDataSource(dataSource);
            }

            // 检查记录是否存在（通过 history_id 定位）
            boolean recordExists = checkRecordExists(tableName, formData.getHistoryId());

            if (recordExists) {
                // 更新：通过 history_id 定位记录，更新指定字段
                String updateSql = buildUpdateSql(tableName, columnName);
                int affected = jdbcConnectService.update(updateSql, parameters);
                log.debug("同步FormData [{}] 到Matrix [{}] 更新成功，影响行数: {}", formData.getId(), tableName, affected);
            } else {
                // 插入：生成UUID主键，设置 history_id 和目标字段
                String uuid = UUID.randomUUID().toString().replace("-", "");
                parameters.put("id", uuid);

                String insertSql = buildInsertSql(tableName, columnName);
                int affected = jdbcConnectService.update(insertSql, parameters);
                log.debug("同步FormData [{}] 到Matrix [{}] 插入成功，影响行数: {}", formData.getId(), tableName, affected);
            }
        } finally {
            if (FuncUtil.isNotEmpty(dataSource)) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 检查动态表中是否存在对应记录
     * 通过 history_id 定位记录
     */
    private boolean checkRecordExists(String tableName, String historyId) {
        String sql = "SELECT COUNT(*) FROM `" + tableName + "` WHERE `history_id` = :historyId";

        Map<String, Object> params = new HashMap<>();
        params.put("historyId", historyId);

        Integer count = jdbcConnectService.queryForObject(sql, params, Integer.class);
        return count != null && count > 0;
    }

    /**
     * 构建更新SQL
     * 通过 history_id 定位记录，更新指定字段
     */
    private String buildUpdateSql(String tableName, String columnName) {
        return "UPDATE `" + tableName + "` SET `" + columnName + "` = :value WHERE `history_id` = :historyId";
    }

    /**
     * 构建插入SQL
     * 生成UUID主键，设置 history_id 和目标字段
     */
    private String buildInsertSql(String tableName, String columnName) {
        return "INSERT INTO `" + tableName + "` (`id`, `history_id`, `" + columnName + "`) VALUES (:id, :historyId, :value)";
    }
}
