package com.bidr.forge.service.dataset;

import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysDatasetTableService;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
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

        // 设置数据源
        if (FuncUtil.isNotEmpty(req.getDataSource()) && !tableList.isEmpty()) {
            tableList.get(0).setDataSource(req.getDataSource());
        }

        DatasetConfigRes res = new DatasetConfigRes();
        res.setDatasetId(req.getDatasetId());
        res.setTables(tableList);
        res.setColumns(columnList);
        return res;
    }

    /**
     * 解析SQL并保存配置（替换原有配置）
     * datasetId必须存在
     */
    @Transactional(rollbackFor = Exception.class)
    public DatasetConfigRes parseSqlAndSave(DatasetConfigReq req) throws JSQLParserException {
        // 验证datasetId必须存在
        Validator.assertNotNull(req.getDatasetId(), ErrCodeSys.SYS_ERR_MSG, "保存配置时datasetId不能为空");
        
        // 先解析
        DatasetConfigRes res = parseSql(req);

        // 删除旧配置
        sysDatasetTableService.deleteByDatasetId(req.getDatasetId());
        sysDatasetColumnService.deleteByDatasetId(req.getDatasetId());

        // 保存新配置
        if (FuncUtil.isNotEmpty(res.getTables())) {
            sysDatasetTableService.insert(res.getTables());
        }
        if (FuncUtil.isNotEmpty(res.getColumns())) {
            sysDatasetColumnService.insert(res.getColumns());
        }

        log.info("成功解析并保存Dataset配置，datasetId={}, tables={}, columns={}",
                req.getDatasetId(), res.getTables().size(), res.getColumns().size());

        return res;
    }

    /**
     * 新增保存配置（创建新的dataset并保存解析结果）
     * datasetId可以为空，将根据datasetName创建新的dataset
     */
    @Transactional(rollbackFor = Exception.class)
    public DatasetConfigRes parseSqlAndCreate(DatasetConfigReq req) throws JSQLParserException {
        Validator.assertTrue(FuncUtil.isNotEmpty(req.getDatasetId()) || FuncUtil.isNotEmpty(req.getDatasetName()),
                ErrCodeSys.SYS_ERR_MSG, "新增保存时datasetName不能为空或提供datasetId");

        Long datasetId = req.getDatasetId();
        if (FuncUtil.isEmpty(datasetId)) {
            SysDataset dataset = new SysDataset();
            dataset.setDatasetName(req.getDatasetName());
            dataset.setDataSource(req.getDataSource());
            sysDatasetService.insert(dataset);
            datasetId = dataset.getId();
            req.setDatasetId(datasetId);
        }

        DatasetConfigRes res = parseSql(req);

        // 保存配置（新增保存不需要删除旧配置）
        if (FuncUtil.isNotEmpty(res.getTables())) {
            sysDatasetTableService.insert(res.getTables());
        }
        if (FuncUtil.isNotEmpty(res.getColumns())) {
            sysDatasetColumnService.insert(res.getColumns());
        }

        log.info("成功新增并保存Dataset配置，datasetId={}, tables={}, columns={}",
                datasetId, res.getTables().size(), res.getColumns().size());

        return res;
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
