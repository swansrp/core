package com.bidr.forge.service.dataset;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bidr.forge.dao.entity.SysDataset;
import com.bidr.forge.dao.entity.SysDatasetColumn;
import com.bidr.forge.dao.entity.SysDatasetTable;
import com.bidr.forge.dao.repository.SysDatasetColumnService;
import com.bidr.forge.dao.repository.SysDatasetService;
import com.bidr.forge.dao.repository.SysDatasetTableService;
import com.bidr.forge.utils.DatasetBaseFilterUtil;
import com.bidr.forge.utils.DatasetColumnRemarkUtil;
import com.bidr.forge.utils.PortalDatasetSqlUtil;
import com.bidr.forge.utils.SqlIdentifierUtil;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

        // 常驻条件来源：SQL 带外层 WHERE 时以 SQL 为准（提取后走同一套六重校验转存），
        // 无 WHERE 时以页面「常驻条件」输入框为准（恒提交，空串=清除）。非法内容直接抛出阻止入库。
        String sqlWhere = PortalDatasetSqlUtil.extractWhereSql(req.getSql());
        boolean baseFilterFromSql = FuncUtil.isNotEmpty(sqlWhere);
        String baseFilter = baseFilterFromSql
                ? DatasetBaseFilterUtil.validate(sqlWhere)
                : DatasetBaseFilterUtil.validate(req.getBaseFilter());

        Long datasetId = req.getDatasetId();
        boolean isNewDataset = FuncUtil.isEmpty(datasetId);

        if (isNewDataset) {
            // ========== 新增模式 ==========
            SysDataset dataset = new SysDataset();
            dataset.setDatasetName(req.getDatasetName());
            dataset.setDataSource(req.getDataSource());
            dataset.setRemark(req.getRemark());
            dataset.setBaseFilter(baseFilter);
            sysDatasetService.insert(dataset);
            datasetId = dataset.getId();
            req.setDatasetId(datasetId);

            log.info("创建新Dataset，datasetId={}, datasetName={}", datasetId, req.getDatasetName());
        } else {
            // ========== 更新模式 ==========
            SysDataset existingDataset = sysDatasetService.selectById(datasetId);
            Validator.assertNotNull(existingDataset, ErrCodeSys.SYS_ERR_MSG, "数据集不存在，datasetId=" + datasetId);

            // 检测基本信息是否有变更
            boolean datasetChanged = hasDatasetChanged(existingDataset, req, baseFilter, baseFilterFromSql);
            if (datasetChanged) {
                updateDatasetIfChanged(existingDataset, req, baseFilter, baseFilterFromSql);
                sysDatasetService.updateById(existingDataset);
                log.info("更新Dataset基本信息，datasetId={}", datasetId);
            }
        }

        // 解析SQL生成新配置
        DatasetConfigRes newConfig = parseSql(req);

        // 对于更新模式，检测配置是否有变更
        if (!isNewDataset) {
            DatasetConfigRes existingConfig = getConfig(datasetId);

            // 关键：合并备注（不重置旧备注；SQL 注释可覆盖；新列生成默认备注）
            mergeColumnRemarks(existingConfig, newConfig);

            boolean configChanged = hasConfigChanged(existingConfig, newConfig);

            if (!configChanged) {
                log.info("Dataset配置无变更，跳过更新，datasetId={}", datasetId);
                // 但如果用户只是改了 SQL 注释（备注），也需要把备注更新到列配置表
                persistColumnRemarksIfOnlyRemarksChanged(existingConfig, newConfig);
                DatasetConfigRes unchanged = getConfig(datasetId);
                unchanged.setBaseFilter(baseFilter);
                return unchanged;
            }

            log.info("检测到Dataset配置变更，datasetId={}", datasetId);
            // 删除旧配置
            sysDatasetTableService.deleteByDatasetId(datasetId);
            sysDatasetColumnService.deleteByDatasetId(datasetId);
        } else {
            // 新增模式：给没有注释的列生成默认备注
            ensureDefaultRemarksForNewColumns(newConfig);
        }

        // 保存新配置
        if (FuncUtil.isNotEmpty(newConfig.getTables())) {
            sysDatasetTableService.insert(newConfig.getTables());
        }
        if (FuncUtil.isNotEmpty(newConfig.getColumns())) {
            sysDatasetColumnService.insert(newConfig.getColumns());
        }
        newConfig.setBaseFilter(baseFilter);

        log.info("成功保存Dataset配置，datasetId={}, tables={}, columns={}, mode={}, baseFilter={}, baseFilterFromSql={}",
                datasetId,
                FuncUtil.isEmpty(newConfig.getTables()) ? 0 : newConfig.getTables().size(),
                FuncUtil.isEmpty(newConfig.getColumns()) ? 0 : newConfig.getColumns().size(),
                isNewDataset ? "新增" : "更新",
                baseFilter, baseFilterFromSql);

        return newConfig;
    }

    /**
     * 检测Dataset基本信息是否有变更
     */
    private boolean hasDatasetChanged(SysDataset existing, DatasetConfigReq req, String baseFilter,
                                      boolean baseFilterFromSql) {
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
        // SQL 携带外层 WHERE 时以 SQL 为准（不看 req.baseFilter 是否提交）；
        // 否则 req.baseFilter 为 null 表示本次未提交该字段，空串归一为 null 即清除。
        // 这里必须用 null 归一比较，不能走 isNotEmpty（否则空串永远触发不了清除）
        if (baseFilterFromSql) {
            if (!compareString(baseFilter, existing.getBaseFilter())) {
                changed = true;
            }
        } else if (req.getBaseFilter() != null && !compareString(baseFilter, existing.getBaseFilter())) {
            changed = true;
        }

        return changed;
    }

    /**
     * 更新Dataset基本信息（仅更新非空字段）
     */
    private void updateDatasetIfChanged(SysDataset dataset, DatasetConfigReq req, String baseFilter,
                                        boolean baseFilterFromSql) {
        if (FuncUtil.isNotEmpty(req.getDatasetName())) {
            dataset.setDatasetName(req.getDatasetName());
        }
        if (FuncUtil.isNotEmpty(req.getDataSource())) {
            dataset.setDataSource(req.getDataSource());
        }
        if (FuncUtil.isNotEmpty(req.getRemark())) {
            dataset.setRemark(req.getRemark());
        }
        if (baseFilterFromSql) {
            // baseFilterFromSql ⇒ baseFilter 经 validate 归一化必非 null，直接覆盖
            dataset.setBaseFilter(baseFilter);
        } else if (req.getBaseFilter() != null) {
            if (baseFilter != null) {
                dataset.setBaseFilter(baseFilter);
            } else if (FuncUtil.isNotEmpty(dataset.getBaseFilter())) {
                // updateById 默认跳过 null 字段，清除必须显式置 NULL
                sysDatasetService.update(new LambdaUpdateWrapper<SysDataset>()
                        .set(SysDataset::getBaseFilter, null)
                        .eq(SysDataset::getId, dataset.getId()));
                dataset.setBaseFilter(null);
            }
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
        // 该字段此前只由保存链路赋值、读取侧恒 null（声明了却没通道），读取时一并带出
        SysDataset dataset = sysDatasetService.selectById(datasetId);
        res.setBaseFilter(dataset == null ? null : dataset.getBaseFilter());
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
        column.setColumnAlias(SqlIdentifierUtil.sanitizeQuotedIdentifier(column.getColumnAlias()));
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
        column.setColumnAlias(SqlIdentifierUtil.sanitizeQuotedIdentifier(column.getColumnAlias()));
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

    /**
     * 合并列备注规则：
     * 1) 如果新解析出来的备注是中文（通常来自 SQL 注释），则使用新备注（覆盖旧值）
     * 2) 否则如果旧备注存在，则保留旧备注（避免字段增删/重解析导致重置）
     * 3) 否则：无注释统一英文兜底
     */
    private void mergeColumnRemarks(DatasetConfigRes existingConfig, DatasetConfigRes newConfig) {
        if (newConfig == null || FuncUtil.isEmpty(newConfig.getColumns())) {
            return;
        }

        Map<String, SysDatasetColumn> oldByAlias = existingConfig == null || FuncUtil.isEmpty(existingConfig.getColumns())
                ? new java.util.HashMap<>()
                : existingConfig.getColumns().stream()
                .filter(c -> FuncUtil.isNotEmpty(c.getColumnAlias()))
                .collect(Collectors.toMap(SysDatasetColumn::getColumnAlias, Function.identity(), (a, b) -> a));

        for (SysDatasetColumn n : newConfig.getColumns()) {
            if (FuncUtil.isEmpty(n.getColumnAlias())) {
                continue;
            }

            String newRemark = n.getRemark();
            SysDatasetColumn old = oldByAlias.get(n.getColumnAlias());

            // SQL 注释（通常为中文）视为显式输入，允许覆盖
            if (FuncUtil.isNotEmpty(newRemark) && containsChinese(newRemark)) {
                continue;
            }

            // 否则优先保留旧备注（避免重置）
            if (old != null && FuncUtil.isNotEmpty(old.getRemark())) {
                n.setRemark(old.getRemark());
                continue;
            }

            // 最后兜底：无注释统一英文
            if (FuncUtil.isEmpty(newRemark) || isEnglishLike(newRemark)) {
                n.setRemark(com.bidr.forge.utils.DatasetColumnRemarkUtil.generateEnglishRemark(n.getColumnAlias()));
            }
        }
    }

    private void ensureDefaultRemarksForNewColumns(DatasetConfigRes newConfig) {
        if (newConfig == null || FuncUtil.isEmpty(newConfig.getColumns())) {
            return;
        }
        for (SysDatasetColumn c : newConfig.getColumns()) {
            // 有中文备注（来自 SQL 注释）则保留
            if (FuncUtil.isNotEmpty(c.getRemark()) && containsChinese(c.getRemark())) {
                continue;
            }
            // 无注释统一英文
            c.setRemark(DatasetColumnRemarkUtil.generateEnglishRemark(c.getColumnAlias()));
        }
    }

    /**
     * 当 SQL 解析出来的表/字段结构没变，但备注变了（仅改注释）时：
     * compareColumnConfigs 不比较 remark，所以会被判定为无变更并直接 return。
     * 这里单独把 remark 按 alias 回写到数据库。
     */
    private void persistColumnRemarksIfOnlyRemarksChanged(DatasetConfigRes existingConfig, DatasetConfigRes newConfig) {
        if (existingConfig == null || newConfig == null
                || FuncUtil.isEmpty(existingConfig.getColumns())
                || FuncUtil.isEmpty(newConfig.getColumns())) {
            return;
        }

        Map<String, SysDatasetColumn> oldByAlias = existingConfig.getColumns().stream()
                .filter(c -> FuncUtil.isNotEmpty(c.getColumnAlias()))
                .collect(Collectors.toMap(SysDatasetColumn::getColumnAlias, Function.identity(), (a, b) -> a));

        for (SysDatasetColumn n : newConfig.getColumns()) {
            if (FuncUtil.isEmpty(n.getColumnAlias()) || FuncUtil.isEmpty(n.getRemark())) {
                continue;
            }
            SysDatasetColumn old = oldByAlias.get(n.getColumnAlias());
            if (old == null) {
                continue;
            }
            // 仅当新备注是中文/显式备注，并且与旧值不同才更新
            if (containsChinese(n.getRemark()) && !n.getRemark().equals(old.getRemark())) {
                SysDatasetColumn upd = new SysDatasetColumn();
                upd.setId(old.getId());
                upd.setRemark(n.getRemark());
                sysDatasetColumnService.updateById(upd);
            }
        }
    }

    public String buildDatasetSql(Long datasetId, boolean includeRemarks) {
        Validator.assertNotNull(datasetId, ErrCodeSys.SYS_ERR_MSG, "datasetId不能为空");
        DatasetConfigRes cfg = getConfig(datasetId);
        return PortalDatasetSqlUtil.buildQuerySql(cfg.getTables(), cfg.getColumns(), includeRemarks);
    }

    /**
     * 取常驻过滤谓词（外层 WHERE 片段）
     * SQL 回显必须与它一起下发：编辑器里的外层 WHERE 是唯一书写入口，回显不拼回则下次保存
     * 以"SQL 无 WHERE ＋ 恒提交空串"命中清除分支，把已转存的常驻条件静默清掉
     */
    public String getBaseFilter(Long datasetId) {
        Validator.assertNotNull(datasetId, ErrCodeSys.SYS_ERR_MSG, "datasetId不能为空");
        SysDataset dataset = sysDatasetService.selectById(datasetId);
        Validator.assertNotNull(dataset, ErrCodeSys.SYS_ERR_MSG, "数据集不存在，datasetId=" + datasetId);
        return dataset.getBaseFilter();
    }

    private boolean containsChinese(String s) {
        if (s == null) return false;
        return s.matches(".*[\\u4e00-\\u9fa5].*");
    }

    private boolean isEnglishLike(String s) {
        if (s == null) return false;
        return s.matches("[A-Za-z0-9 _\\-]+") && s.matches(".*[A-Za-z].*");
    }
}
