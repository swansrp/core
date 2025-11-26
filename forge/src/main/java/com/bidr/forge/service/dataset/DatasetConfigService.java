package com.bidr.forge.service.dataset;

import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysDatasetTableService;
import com.bidr.forge.utils.PortalDatasetSqlUtil;
import com.bidr.forge.vo.dataset.DatasetColumnReq;
import com.bidr.forge.vo.dataset.DatasetConfigReq;
import com.bidr.forge.vo.dataset.DatasetConfigRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.IdOrderReqVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Dataset配置管理服务
 *
 * @author Sharp
 * @since 2025-11-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetConfigService {

    private final SysDatasetService sysDatasetService;
    private final SysDatasetTableService sysDatasetTableService;
    private final SysDatasetColumnService sysDatasetColumnService;

    /**
     * 解析SQL并生成配置（不保存）
     * datasetId可以为空，仅用于预览SQL解析结果
     */
    public DatasetConfigRes parseSql(DatasetConfigReq req) throws JSQLParserException {
        List<SysDatasetTable> tableList = new ArrayList<>();
        List<SysDatasetColumn> columnList = new ArrayList<>();

        // 使用工具类解析SQL（datasetId可为null，用于预览）
        PortalDatasetSqlUtil.parseSql(req.getSql(), req.getDatasetId(), tableList, columnList);

        DatasetConfigRes res = new DatasetConfigRes();
        res.setDatasetId(req.getDatasetId());
        res.setTables(tableList);
        res.setColumns(columnList);
        return res;
    }

    /**
     * 保存配置（智能新增/更新）
     * - 无datasetId：创建新的数据集
     * - 有datasetId：检测变更后智能更新
     */
    @Transactional(rollbackFor = Exception.class)
    public DatasetConfigRes save(DatasetConfigReq req) throws JSQLParserException {
        Validator.assertTrue(FuncUtil.isNotEmpty(req.getDatasetId()) || FuncUtil.isNotEmpty(req.getDatasetName()),
                ErrCodeSys.SYS_ERR_MSG, "保存时datasetId或datasetName至少需要提供一个");

        Long datasetId = req.getDatasetId();
        boolean isNewDataset = FuncUtil.isEmpty(datasetId);

        if (isNewDataset) {
            // ========== 新增模式 ==========
            SysDataset dataset = new SysDataset();
            dataset.setDatasetName(req.getDatasetName());
            dataset.setDataSource(req.getDataSource());
            dataset.setRemark(req.getRemark());
            sysDatasetService.insert(dataset);
            datasetId = dataset.getId();
            req.setDatasetId(datasetId);

            log.info("创建新Dataset，datasetId={}, datasetName={}", datasetId, req.getDatasetName());
        } else {
            // ========== 更新模式 ==========
            SysDataset existingDataset = sysDatasetService.selectById(datasetId);
            Validator.assertNotNull(existingDataset, ErrCodeSys.SYS_ERR_MSG, "数据集不存在，datasetId=" + datasetId);

            // 检测基本信息是否有变更
            boolean datasetChanged = hasDatasetChanged(existingDataset, req);
            if (datasetChanged) {
                updateDatasetIfChanged(existingDataset, req);
                sysDatasetService.updateById(existingDataset);
                log.info("更新Dataset基本信息，datasetId={}", datasetId);
            }
        }

        // 解析SQL生成新配置
        DatasetConfigRes newConfig = parseSql(req);

        // 对于更新模式，检测配置是否有变更
        if (!isNewDataset) {
            DatasetConfigRes existingConfig = getConfig(datasetId);
            boolean configChanged = hasConfigChanged(existingConfig, newConfig);

            if (!configChanged) {
                log.info("Dataset配置无变更，跳过更新，datasetId={}", datasetId);
                return existingConfig;
            }

            log.info("检测到Dataset配置变更，datasetId={}", datasetId);
            // 删除旧配置
            sysDatasetTableService.deleteByDatasetId(datasetId);
            sysDatasetColumnService.deleteByDatasetId(datasetId);
        }

        // 保存新配置
        if (FuncUtil.isNotEmpty(newConfig.getTables())) {
            sysDatasetTableService.insert(newConfig.getTables());
        }
        if (FuncUtil.isNotEmpty(newConfig.getColumns())) {
            sysDatasetColumnService.insert(newConfig.getColumns());
        }

        log.info("成功保存Dataset配置，datasetId={}, tables={}, columns={}, mode={}",
                datasetId, newConfig.getTables().size(), newConfig.getColumns().size(),
                isNewDataset ? "新增" : "更新");

        return newConfig;
    }

    /**
     * 检测Dataset基本信息是否有变更
     */
    private boolean hasDatasetChanged(SysDataset existing, DatasetConfigReq req) {
        boolean changed = false;

        if (FuncUtil.isNotEmpty(req.getDatasetName()) && !req.getDatasetName().equals(existing.getDatasetName())) {
            changed = true;
        }
        if (FuncUtil.isNotEmpty(req.getDataSource()) && !req.getDataSource().equals(existing.getDataSource())) {
            changed = true;
        }
        if (FuncUtil.isNotEmpty(req.getRemark()) && !req.getRemark().equals(existing.getRemark())) {
            changed = true;
        }

        return changed;
    }

    /**
     * 更新Dataset基本信息（仅更新非空字段）
     */
    private void updateDatasetIfChanged(SysDataset dataset, DatasetConfigReq req) {
        if (FuncUtil.isNotEmpty(req.getDatasetName())) {
            dataset.setDatasetName(req.getDatasetName());
        }
        if (FuncUtil.isNotEmpty(req.getDataSource())) {
            dataset.setDataSource(req.getDataSource());
        }
        if (FuncUtil.isNotEmpty(req.getRemark())) {
            dataset.setRemark(req.getRemark());
        }
    }

    /**
     * 检测表和列配置是否有变更
     */
    private boolean hasConfigChanged(DatasetConfigRes existing, DatasetConfigRes newConfig) {
        // 比较表配置
        if (!compareTableConfigs(existing.getTables(), newConfig.getTables())) {
            return true;
        }

        // 比较列配置
        if (!compareColumnConfigs(existing.getColumns(), newConfig.getColumns())) {
            return true;
        }

        return false;
    }

    /**
     * 比较表配置列表
     */
    private boolean compareTableConfigs(List<SysDatasetTable> existing, List<SysDatasetTable> newList) {
        if (FuncUtil.isEmpty(existing) && FuncUtil.isEmpty(newList)) {
            return true;
        }
        if (FuncUtil.isEmpty(existing) || FuncUtil.isEmpty(newList)) {
            return false;
        }
        if (existing.size() != newList.size()) {
            return false;
        }

        for (int i = 0; i < existing.size(); i++) {
            SysDatasetTable e = existing.get(i);
            SysDatasetTable n = newList.get(i);

            if (!compareString(e.getTableSql(), n.getTableSql()) ||
                    !compareString(e.getTableAlias(), n.getTableAlias()) ||
                    !compareString(e.getJoinType(), n.getJoinType()) ||
                    !compareString(e.getJoinCondition(), n.getJoinCondition()) ||
                    !compareInteger(e.getTableOrder(), n.getTableOrder())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 比较列配置列表
     */
    private boolean compareColumnConfigs(List<SysDatasetColumn> existing, List<SysDatasetColumn> newList) {
        if (FuncUtil.isEmpty(existing) && FuncUtil.isEmpty(newList)) {
            return true;
        }
        if (FuncUtil.isEmpty(existing) || FuncUtil.isEmpty(newList)) {
            return false;
        }
        if (existing.size() != newList.size()) {
            return false;
        }

        for (int i = 0; i < existing.size(); i++) {
            SysDatasetColumn e = existing.get(i);
            SysDatasetColumn n = newList.get(i);

            if (!compareString(e.getColumnSql(), n.getColumnSql()) ||
                    !compareString(e.getColumnAlias(), n.getColumnAlias()) ||
                    !compareString(e.getIsAggregate(), n.getIsAggregate()) ||
                    !compareInteger(e.getDisplayOrder(), n.getDisplayOrder())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 比较字符串（支持null值比较）
     */
    private boolean compareString(String s1, String s2) {
        if (s1 == null && s2 == null) {
            return true;
        }
        if (s1 == null || s2 == null) {
            return false;
        }
        return s1.equals(s2);
    }

    /**
     * 比较Integer（支持null值比较）
     */
    private boolean compareInteger(Integer i1, Integer i2) {
        if (i1 == null && i2 == null) {
            return true;
        }
        if (i1 == null || i2 == null) {
            return false;
        }
        return i1.equals(i2);
    }

    /**
     * 获取指定datasetId的所有配置
     */
    public DatasetConfigRes getConfig(Long datasetId) {
        List<SysDatasetTable> tables = sysDatasetTableService.getByDatasetId(datasetId);
        List<SysDatasetColumn> columns = sysDatasetColumnService.getByDatasetId(datasetId);

        DatasetConfigRes res = new DatasetConfigRes();
        res.setDatasetId(datasetId);
        res.setTables(tables);
        res.setColumns(columns);
        return res;
    }

    /**
     * 获取指定datasetId的所有列配置
     */
    public List<SysDatasetColumn> getColumns(Long datasetId) {
        return sysDatasetColumnService.getByDatasetId(datasetId);
    }

    /**
     * 根据ID获取单个列配置
     */
    public SysDatasetColumn getColumnById(Long id) {
        return sysDatasetColumnService.getById(id);
    }

    /**
     * 新增列配置
     */
    @Transactional(rollbackFor = Exception.class)
    public SysDatasetColumn addColumn(DatasetColumnReq req) {
        SysDatasetColumn column = new SysDatasetColumn();
        ReflectionUtil.copyProperties(req, column);
        sysDatasetColumnService.insert(column);
        log.info("新增Dataset列配置成功，id={}, datasetId={}, columnAlias={}",
                column.getId(), column.getDatasetId(), column.getColumnAlias());
        return column;
    }

    /**
     * 更新列配置
     */
    @Transactional(rollbackFor = Exception.class)
    public SysDatasetColumn updateColumn(DatasetColumnReq req) {
        Validator.assertNotNull(req.getId(), ErrCodeSys.SYS_ERR_MSG, "列配置ID不能为空");

        SysDatasetColumn column = sysDatasetColumnService.getById(req.getId());
        Validator.assertNotNull(column, ErrCodeSys.SYS_ERR_MSG, "列配置不存在");

        ReflectionUtil.copyProperties(req, column);
        sysDatasetColumnService.updateById(column);
        log.info("更新Dataset列配置成功，id={}, datasetId={}, columnAlias={}",
                column.getId(), column.getDatasetId(), column.getColumnAlias());
        return column;
    }

    /**
     * 删除列配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteColumn(Long id) {
        Validator.assertNotNull(id, ErrCodeSys.SYS_ERR_MSG, "列配置ID不能为空");
        sysDatasetColumnService.deleteById(id);
        log.info("删除Dataset列配置成功，id={}", id);
    }

    /**
     * 批量更新列配置的显示顺序
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateColumnsOrder(List<IdOrderReqVO> columns) {
        if (FuncUtil.isNotEmpty(columns)) {
            for (IdOrderReqVO column : columns) {
                if (column.getId() != null && column.getShowOrder() != null) {
                    SysDatasetColumn entity = new SysDatasetColumn();
                    entity.setId(Long.valueOf(column.getId().toString()));
                    entity.setDisplayOrder(column.getShowOrder());
                    sysDatasetColumnService.updateById(entity);
                }
            }
        }
    }
}
