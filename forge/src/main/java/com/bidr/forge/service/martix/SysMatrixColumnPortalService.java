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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 矩阵字段配置Portal Service
 *
 * @author sharp
 * @since 2025-11-20
 */
@Service
@RequiredArgsConstructor
public class SysMatrixColumnPortalService extends BasePortalService<SysMatrixColumn, SysMatrixColumnVO> {

    private final SysMatrixService sysMatrixService;
    private final JdbcConnectService jdbcConnectService;

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
     * 更新字段后，将矩阵状态设置为待同步
     */
    @Override
    public void afterUpdate(SysMatrixColumn sysMatrixColumn) {
        super.afterUpdate(sysMatrixColumn);
        markPendingSync(sysMatrixColumn.getMatrixId());
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
