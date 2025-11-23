package com.bidr.forge.service.martix;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.forge.bo.MatrixColumns;
import com.bidr.forge.config.jdbc.JdbcConnectService;
import com.bidr.forge.constant.dict.MatrixChangeTypeDict;
import com.bidr.forge.constant.dict.MatrixStatusDict;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.dao.entity.SysMatrixChangeLog;
import com.bidr.forge.dao.entity.SysMatrixColumn;
import com.bidr.forge.dao.repository.SysMatrixChangeLogService;
import com.bidr.forge.dao.repository.SysMatrixColumnService;
import com.bidr.forge.dao.repository.SysMatrixService;
import com.bidr.forge.vo.matrix.MatrixChangeLogExportVO;
import com.bidr.forge.vo.matrix.SysMatrixChangeLogVO;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 矩阵表结构变更日志 Portal Service
 *
 * @author sharp
 * @since 2025-11-21
 */
@Service
@RequiredArgsConstructor
public class SysMatrixChangeLogPortalService extends BasePortalService<SysMatrixChangeLog, SysMatrixChangeLogVO> {

    private final SysMatrixChangeLogService sysMatrixChangeLogService;
    private final SysMatrixService sysMatrixService;
    private final SysMatrixColumnService sysMatrixColumnService;
    private final JdbcConnectService jdbcConnectService;
    private final ObjectMapper objectMapper;

    /**
     * 导出矩阵变更日志
     *
     * @param matrixId 矩阵ID
     * @return 变更日志JSON数据
     */
    public String exportChangeLog(Long matrixId) {
        // 查询矩阵信息
        SysMatrix matrix = sysMatrixService.getById(matrixId);
        Validator.assertNotNull(matrix, ErrCodeSys.SYS_ERR_MSG, "矩阵配置不存在");

        // 构建导出VO
        MatrixChangeLogExportVO exportVO = new MatrixChangeLogExportVO();
        exportVO.setTableName(matrix.getTableName());
        exportVO.setTableComment(matrix.getTableComment());
        exportVO.setDataSource(matrix.getDataSource());
        exportVO.setEngine(matrix.getEngine());
        exportVO.setCharset(matrix.getCharset());

        // 查询所有变更日志
        List<SysMatrixChangeLog> changeLogs = sysMatrixChangeLogService.lambdaQuery()
                .eq(SysMatrixChangeLog::getMatrixId, matrixId)
                .orderByAsc(SysMatrixChangeLog::getVersion)
                .orderByAsc(SysMatrixChangeLog::getId)
                .list();

        // 转换变更日志
        List<MatrixChangeLogExportVO.ChangeLogItemVO> changeLogItems = changeLogs.stream()
                .filter(log -> CommonConst.YES.equals(log.getExecuteStatus())) // 只导出成功的变更
                .map(log -> {
                    MatrixChangeLogExportVO.ChangeLogItemVO item = new MatrixChangeLogExportVO.ChangeLogItemVO();
                    item.setVersion(log.getVersion());
                    item.setChangeType(log.getChangeType());
                    item.setChangeDesc(log.getChangeDesc());
                    item.setDdlStatement(log.getDdlStatement());
                    item.setAffectedColumn(log.getAffectedColumn());
                    return item;
                })
                .collect(Collectors.toList());
        exportVO.setChangeLogs(changeLogItems);

        // 查询当前字段配置
        MatrixColumns matrixColumns = sysMatrixService.getMatrixColumns(matrixId);
        List<SysMatrixColumn> columns = matrixColumns.getColumns();
        List<MatrixChangeLogExportVO.MatrixColumnExportVO> columnVOs = columns.stream()
                .map(col -> {
                    MatrixChangeLogExportVO.MatrixColumnExportVO vo = new MatrixChangeLogExportVO.MatrixColumnExportVO();
                    BeanUtils.copyProperties(col, vo);
                    return vo;
                })
                .collect(Collectors.toList());
        exportVO.setColumns(columnVOs);

        // 转换为JSON
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportVO);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("导出变更日志失败: " + e.getMessage(), e);
        }
    }

    /**
     * 导入矩阵变更日志
     *
     * @param changeLogData 变更日志JSON数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void importChangeLog(String changeLogData) {
        // 解析JSON
        MatrixChangeLogExportVO importData;
        try {
            importData = objectMapper.readValue(changeLogData, MatrixChangeLogExportVO.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("解析变更日志数据失败: " + e.getMessage(), e);
        }

        // 根据表名查找目标环境的矩阵
        SysMatrix targetMatrix = sysMatrixService.lambdaQuery()
                .eq(SysMatrix::getTableName, importData.getTableName())
                .eq(SysMatrix::getValid, CommonConst.YES)
                .one();

        Validator.assertNotNull(targetMatrix, ErrCodeSys.SYS_ERR_MSG,
                "目标环境中不存在表名为 [" + importData.getTableName() + "] 的矩阵配置，请先创建矩阵");

        // 检查矩阵状态
        Validator.assertTrue(
                MatrixStatusDict.CREATED.getValue().equals(targetMatrix.getStatus()) ||
                        MatrixStatusDict.SYNCED.getValue().equals(targetMatrix.getStatus()) ||
                        MatrixStatusDict.PENDING_SYNC.getValue().equals(targetMatrix.getStatus()),
                ErrCodeSys.SYS_ERR_MSG,
                "目标矩阵状态为未创建，请先创建物理表"
        );

        // 获取目标环境当前的最大版本号
        Integer currentMaxVersion = getCurrentMaxVersion(targetMatrix.getId());

        // 获取目标环境已执行的变更日志
        List<SysMatrixChangeLog> existingLogs = sysMatrixChangeLogService.lambdaQuery()
                .eq(SysMatrixChangeLog::getMatrixId, targetMatrix.getId())
                .orderByAsc(SysMatrixChangeLog::getVersion)
                .list();

        // 找出需要执行的变更（版本号大于当前最大版本号的变更）
        List<MatrixChangeLogExportVO.ChangeLogItemVO> pendingChanges = importData.getChangeLogs().stream()
                .filter(log -> log.getVersion() > currentMaxVersion)
                .collect(Collectors.toList());

        if (pendingChanges.isEmpty()) {
            throw new RuntimeException("没有需要同步的变更记录");
        }

        // 切换到目标矩阵的数据源
        if (targetMatrix.getDataSource() != null && !targetMatrix.getDataSource().isEmpty()) {
            jdbcConnectService.switchDataSource(targetMatrix.getDataSource());
        }

        try {
            // 逐条执行变更
            for (MatrixChangeLogExportVO.ChangeLogItemVO change : pendingChanges) {
                executeChange(targetMatrix, change, importData.getColumns());
            }

            // 同步字段配置到目标环境
            syncColumns(targetMatrix.getId(), importData.getColumns());

            // 更新矩阵状态为已同步
            targetMatrix.setStatus(MatrixStatusDict.SYNCED.getValue());
            sysMatrixService.updateById(targetMatrix);

        } finally {
            // 重置数据源
            if (targetMatrix.getDataSource() != null && !targetMatrix.getDataSource().isEmpty()) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 获取当前最大版本号
     */
    private Integer getCurrentMaxVersion(Long matrixId) {
        SysMatrixChangeLog lastLog = sysMatrixChangeLogService.lambdaQuery()
                .eq(SysMatrixChangeLog::getMatrixId, matrixId)
                .orderByDesc(SysMatrixChangeLog::getVersion)
                .last("LIMIT 1")
                .one();
        return lastLog == null ? 0 : lastLog.getVersion();
    }

    /**
     * 执行单条变更
     */
    private void executeChange(SysMatrix targetMatrix, MatrixChangeLogExportVO.ChangeLogItemVO change,
                               List<MatrixChangeLogExportVO.MatrixColumnExportVO> allColumns) {
        String ddl = change.getDdlStatement();

        // 替换表名为目标环境的表名
        // CREATE TABLE 不需要执行，因为表已存在
        if (MatrixChangeTypeDict.CREATE_TABLE.getValue().equals(change.getChangeType())) {
            // 记录日志但不执行
            logChange(targetMatrix.getId(), change.getVersion(), change.getChangeType(),
                    change.getChangeDesc(), ddl, change.getAffectedColumn(), CommonConst.YES, "表已存在，跳过创建");
            return;
        }

        // 执行DDL语句
        try {
            jdbcConnectService.executeUpdate(ddl);
            // 记录成功日志
            logChange(targetMatrix.getId(), change.getVersion(), change.getChangeType(),
                    change.getChangeDesc(), ddl, change.getAffectedColumn(), CommonConst.YES, null);
        } catch (Exception e) {
            // 记录失败日志
            String errorMsg = e.getMessage();
            logChange(targetMatrix.getId(), change.getVersion(), change.getChangeType(),
                    change.getChangeDesc(), ddl, change.getAffectedColumn(), CommonConst.NO, errorMsg);

            // 某些失败可以忽略（如索引已存在、字段已存在等）
            if (isIgnorableError(errorMsg)) {
                // 继续执行
                return;
            }
            throw new RuntimeException("执行变更失败: " + errorMsg, e);
        }
    }

    /**
     * 判断错误是否可以忽略
     */
    private boolean isIgnorableError(String errorMsg) {
        if (errorMsg == null) {
            return false;
        }
        String lowerMsg = errorMsg.toLowerCase();
        return lowerMsg.contains("duplicate column") ||
                lowerMsg.contains("duplicate key") ||
                lowerMsg.contains("already exists") ||
                lowerMsg.contains("can't drop");
    }

    /**
     * 同步字段配置
     */
    private void syncColumns(Long targetMatrixId, List<MatrixChangeLogExportVO.MatrixColumnExportVO> importColumns) {
        // 查询目标环境当前字段配置
        List<SysMatrixColumn> existingColumns = sysMatrixColumnService.lambdaQuery()
                .eq(SysMatrixColumn::getMatrixId, targetMatrixId)
                .eq(SysMatrixColumn::getValid, CommonConst.YES)
                .list();

        // 构建字段名到字段对象的映射
        java.util.Map<String, SysMatrixColumn> existingColumnMap = existingColumns.stream()
                .collect(Collectors.toMap(SysMatrixColumn::getColumnName, col -> col));

        // 处理导入的字段配置
        for (MatrixChangeLogExportVO.MatrixColumnExportVO importCol : importColumns) {
            SysMatrixColumn existingCol = existingColumnMap.get(importCol.getColumnName());

            if (existingCol == null) {
                // 新字段：添加
                SysMatrixColumn newColumn = new SysMatrixColumn();
                BeanUtils.copyProperties(importCol, newColumn);
                newColumn.setId(null); // 清空ID，让数据库自动生成
                newColumn.setMatrixId(targetMatrixId);
                sysMatrixColumnService.save(newColumn);
            } else {
                // 已存在字段：更新配置
                BeanUtils.copyProperties(importCol, existingCol, "id", "matrixId", "createBy", "createAt");
                sysMatrixColumnService.updateById(existingCol);
            }
        }

        // 删除目标环境中多余的字段（不在导入配置中的字段）
        List<String> importColumnNames = importColumns.stream()
                .map(MatrixChangeLogExportVO.MatrixColumnExportVO::getColumnName)
                .collect(Collectors.toList());

        for (SysMatrixColumn existingCol : existingColumns) {
            if (!importColumnNames.contains(existingCol.getColumnName())) {
                // 软删除
                existingCol.setValid(CommonConst.NO);
                sysMatrixColumnService.updateById(existingCol);
            }
        }
    }

    /**
     * 记录变更日志
     */
    private void logChange(Long matrixId, Integer version, String changeType, String changeDesc,
                           String ddlStatement, String affectedColumn, String executeStatus, String errorMsg) {
        SysMatrixChangeLog log = new SysMatrixChangeLog();
        log.setMatrixId(matrixId);
        log.setVersion(version);
        log.setChangeType(changeType);
        log.setChangeDesc(changeDesc);
        log.setDdlStatement(ddlStatement);
        log.setAffectedColumn(affectedColumn);
        log.setExecuteStatus(executeStatus);
        log.setErrorMsg(errorMsg);
        log.setSort(version);
        sysMatrixChangeLogService.save(log);
    }
}
