package com.bidr.forge.service.martix;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.constant.dict.MatrixStatusDict;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.vo.matrix.SysMatrixColumnVO;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdReqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 矩阵字段配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMatrixColumnPortalService extends BasePortalService<SysMatrixColumn, SysMatrixColumnVO> {

    private final SysMatrixService sysMatrixService;
    private final JdbcConnectService jdbcConnectService;

    /**
     * 用于存储更新前的字段配置
     */
    private ThreadLocal<SysMatrixColumn> beforeUpdateColumn = new ThreadLocal<>();

    /**
     * 新增前处理：处理AUTO_INCREMENT配置
     */
    @Override
    public void beforeAdd(SysMatrixColumn sysMatrixColumn) {
        super.beforeAdd(sysMatrixColumn);
        handleAutoIncrement(sysMatrixColumn);
    }

    /**
     * 更新前保存原始数据并处理AUTO_INCREMENT配置
     */
    @Override
    public void beforeUpdate(SysMatrixColumn sysMatrixColumn) {
        super.beforeUpdate(sysMatrixColumn);
        // 查询并保存更新前的数据
        SysMatrixColumn oldColumn = getRepo().selectById(sysMatrixColumn.getId());
        beforeUpdateColumn.set(oldColumn);
        
        // 处理AUTO_INCREMENT配置
        handleAutoIncrement(sysMatrixColumn);
    }

    /**
     * 处理AUTO_INCREMENT配置
     * 当sequence或defaultValue为AUTO_INCREMENT时，自动配置为自增型主键
     *
     * @param sysMatrixColumn 字段配置
     */
    private void handleAutoIncrement(SysMatrixColumn sysMatrixColumn) {
        boolean isAutoIncrement = false;
        String source = null;
        
        // 优先检查sequence字段
        if ("AUTO_INCREMENT".equalsIgnoreCase(sysMatrixColumn.getSequence())) {
            isAutoIncrement = true;
            source = "sequence";
        }
        // 如果sequence为空，检查defaultValue字段（兼容旧版本）
        else if ("AUTO_INCREMENT".equalsIgnoreCase(sysMatrixColumn.getDefaultValue())) {
            isAutoIncrement = true;
            source = "defaultValue";
            // 将AUTO_INCREMENT移动到sequence字段
            sysMatrixColumn.setSequence("AUTO_INCREMENT");
            sysMatrixColumn.setDefaultValue(null);
        }
        
        if (isAutoIncrement) {
            // 设置为主键
            sysMatrixColumn.setIsPrimaryKey("1");
            // 不可为空
            sysMatrixColumn.setIsNullable("0");
            
            log.info("字段 [{}] 从 {} 检测到 AUTO_INCREMENT 配置，自动设置为自增型主键", 
                    sysMatrixColumn.getColumnName(), source);
        }
    }

    /**
     * 删除字段前检查数据
     */
    @Override
    public void beforeDelete(IdReqVO vo) {
        // 查询要删除的字段
        SysMatrixColumn column = getRepo().selectById(vo.getId());


        // 查询对应的矩阵配置
        SysMatrix matrix = sysMatrixService.getById(column.getMatrixId());

        // 只有表已创建的情况才检查数据
        if ("1".equals(matrix.getStatus()) || "2".equals(matrix.getStatus())) {
            // 切换数据源（如果配置了）
            if (matrix.getDataSource() != null && !matrix.getDataSource().isEmpty()) {
                jdbcConnectService.switchDataSource(matrix.getDataSource());
            }

            try {
                // 检查字段是否有非空数据
                String checkSql = "SELECT COUNT(*) as count FROM `" + matrix.getTableName() +
                        "` WHERE `" + column.getColumnName() + "` IS NOT NULL";
                List<Map<String, Object>> result = jdbcConnectService.executeQuery(checkSql);

                if (!result.isEmpty()) {
                    long count = ((Number) result.get(0).get("count")).longValue();
                    Validator.assertTrue(count == 0, ErrCodeSys.SYS_ERR_MSG, "字段 [" + column.getColumnName() + "] 中存在数据（" + count + " 条），无法删除");
                }
            } catch (Exception e) {
                // 如果查询失败（比如字段不存在），允许删除
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
     * 新增字段后，将矩阵状态设置为待同步
     */
    @Override
    public void afterAdd(SysMatrixColumn sysMatrixColumn) {
        super.afterAdd(sysMatrixColumn);
        markPendingSync(sysMatrixColumn.getMatrixId());
    }

    /**
     * 删除字段后，将矩阵状态设置为待同步
     */
    @Override
    public void afterDelete(IdReqVO vo) {
        super.afterDelete(vo);
        // 从 vo 中获取已删除的字段信息，标记为待同步
        SysMatrixColumn column = getRepo().selectById(vo.getId());
        if (column != null) {
            markPendingSync(column.getMatrixId());
        }
    }

    /**
     * 更新字段后，判断字段变更是否需要标记为待同步
     */
    @Override
    public void afterUpdate(SysMatrixColumn sysMatrixColumn) {
        super.afterUpdate(sysMatrixColumn);
        
        try {
            SysMatrixColumn oldColumn = beforeUpdateColumn.get();
            if (oldColumn != null) {
                // 判断是否需要标记为待同步（只有影响DDL的字段修改才需要）
                if (isNeedMarkPending(oldColumn, sysMatrixColumn)) {
                    markPendingSync(sysMatrixColumn.getMatrixId());
                }
            }
        } finally {
            // 清理ThreadLocal
            beforeUpdateColumn.remove();
        }
    }

    /**
     * 判断字段变更是否需要标记为待同步
     * 只有影响DDL的字段修改才需要标记为待同步
     *
     * @param oldColumn 更新前的字段
     * @param newColumn 更新后的字段
     * @return true-需要标记为待同步
     */
    private boolean isNeedMarkPending(SysMatrixColumn oldColumn, SysMatrixColumn newColumn) {
        String columnName = newColumn.getColumnName();
        
        // 字段名变更
        if (!Objects.equals(oldColumn.getColumnName(), newColumn.getColumnName())) {
            log.info("字段 [{}] 的 columnName 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getColumnName(), newColumn.getColumnName());
            return true;
        }
        // 字段注释变更
        if (!Objects.equals(oldColumn.getColumnComment(), newColumn.getColumnComment())) {
            log.info("字段 [{}] 的 columnComment 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getColumnComment(), newColumn.getColumnComment());
            return true;
        }
        // 字段类型变更
        if (!Objects.equals(oldColumn.getColumnType(), newColumn.getColumnType())) {
            log.info("字段 [{}] 的 columnType 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getColumnType(), newColumn.getColumnType());
            return true;
        }
        // 字段长度变更
        if (!Objects.equals(oldColumn.getColumnLength(), newColumn.getColumnLength())) {
            log.info("字段 [{}] 的 columnLength 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getColumnLength(), newColumn.getColumnLength());
            return true;
        }
        // 小数位数变更
        if (!Objects.equals(oldColumn.getDecimalPlaces(), newColumn.getDecimalPlaces())) {
            log.info("字段 [{}] 的 decimalPlaces 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getDecimalPlaces(), newColumn.getDecimalPlaces());
            return true;
        }
        // 是否可空变更
        if (!Objects.equals(oldColumn.getIsNullable(), newColumn.getIsNullable())) {
            log.info("字段 [{}] 的 isNullable 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getIsNullable(), newColumn.getIsNullable());
            return true;
        }
        // 默认值变更
        if (!Objects.equals(oldColumn.getDefaultValue(), newColumn.getDefaultValue())) {
            log.info("字段 [{}] 的 defaultValue 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getDefaultValue(), newColumn.getDefaultValue());
            return true;
        }
        // 序列变更
        if (!Objects.equals(oldColumn.getSequence(), newColumn.getSequence())) {
            log.info("字段 [{}] 的 sequence 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getSequence(), newColumn.getSequence());
            return true;
        }
        // 是否主键变更
        if (!Objects.equals(oldColumn.getIsPrimaryKey(), newColumn.getIsPrimaryKey())) {
            log.info("字段 [{}] 的 isPrimaryKey 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getIsPrimaryKey(), newColumn.getIsPrimaryKey());
            return true;
        }
        // 是否索引变更
        if (!Objects.equals(oldColumn.getIsIndex(), newColumn.getIsIndex())) {
            log.info("字段 [{}] 的 isIndex 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getIsIndex(), newColumn.getIsIndex());
            return true;
        }
        // 是否唯一变更
        if (!Objects.equals(oldColumn.getIsUnique(), newColumn.getIsUnique())) {
            log.info("字段 [{}] 的 isUnique 发生变更: {} -> {}, 触发 markPending", 
                    columnName, oldColumn.getIsUnique(), newColumn.getIsUnique());
            return true;
        }
        
        // 以下字段变更不需要标记为待同步（不影响DDL）：
        // - fieldType (表单字段类型)
        // - sort (排序)
        // - isDisplayNameField (名称字段)
        // - isOrderField (顺序字段)
        // - isPidField (父节点字段)
        // - referenceMatrixId (关联矩阵)
        
        log.debug("字段 [{}] 的变更不影响DDL，无需触发 markPending", columnName);
        return false;
    }

    /**
     * 将矩阵状态标记为待同步
     *
     * @param matrixId 矩阵ID
     */
    private void markPendingSync(Long matrixId) {
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        if (matrix != null) {
            // 只有已创建或已同步状态的表才需要标记为待同步
            if (MatrixStatusDict.CREATED.getValue().equals(matrix.getStatus()) ||
                    MatrixStatusDict.SYNCED.getValue().equals(matrix.getStatus())) {
                matrix.setStatus(MatrixStatusDict.PENDING_SYNC.getValue());
                sysMatrixService.updateById(matrix);
            }
        }
    }
}
