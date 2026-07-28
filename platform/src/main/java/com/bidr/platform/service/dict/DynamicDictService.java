package com.bidr.platform.service.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.entity.SysDynamicDictConfig;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.dao.repository.SysDynamicDictConfigService;
import com.bidr.platform.service.cache.dict.BizDictTreeCacheService;
import com.bidr.platform.vo.dict.DynamicDictCondition;
import com.bidr.platform.vo.dict.DynamicDictItemVO;
import com.bidr.platform.vo.dict.DynamicDictReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 动态字典生成Service
 * <p>
 * 通过动态拼接SQL，从指定表的指定列中GROUP BY出不同的key-value组合，
 * 自动生成字典选项。支持配置持久化和缓存刷新。
 *
 * <h3>SQL 结构示例：</h3>
 * <pre>
 * SELECT DISTINCT `col_value` AS `value`, `col_label` AS `label`
 * FROM `table_name`
 * WHERE `filter_col` = 'filter_value'
 * ORDER BY `col_value` ASC
 * </pre>
 *
 * @author Sharp
 * @since 2026-07-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDictService {

    /**
     * 合法标识符正则：只允许字母、数字、下划线
     */
    private static final Pattern VALID_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    /**
     * 合法全限定表名正则：允许 "标识符" 或 "标识符.标识符" 格式
     */
    private static final Pattern VALID_QUALIFIED_TABLE = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_]*$" +
                    "|^[a-zA-Z_][a-zA-Z0-9_]*\\.[a-zA-Z_][a-zA-Z0-9_]*$"
    );
    /**
     * 合法排序方向后缀
     */
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "^[a-zA-Z_][a-zA-Z0-9_]*(\\s+(ASC|DESC|asc|desc))?$"
    );
    /**
     * 支持的操作符
     */
    private static final Set<String> VALID_OPERATORS = new HashSet<>(Arrays.asList(
            "=", "!=", "IS NULL", "IS NOT NULL", "LIKE"
    ));
    private final JdbcConnectService jdbcConnectService;
    private final SysDynamicDictConfigService configService;
    private final SysBizDictService sysBizDictService;
    private final BizDictTreeCacheService bizDictTreeCacheService;

    // ==================== 动态查询 ====================

    /**
     * 根据请求生成动态字典选项
     *
     * @param req 动态字典请求
     * @return 字典选项列表（value-label 键值对）
     */
    public List<DynamicDictItemVO> generateDict(DynamicDictReq req) {
        // 参数校验
        Validator.assertNotBlank(req.getTableName(), ErrCodeSys.PA_PARAM_NULL, "表名");
        Validator.assertNotBlank(req.getValueColumn(), ErrCodeSys.PA_PARAM_NULL, "value字段名");
        Validator.assertNotBlank(req.getLabelColumn(), ErrCodeSys.PA_PARAM_NULL, "label字段名");

        // 安全校验
        validateTableName(req.getTableName());
        validateIdentifier(req.getValueColumn(), "value字段名");
        validateIdentifier(req.getLabelColumn(), "label字段名");
        if (FuncUtil.isNotEmpty(req.getPidColumn())) {
            validateIdentifier(req.getPidColumn(), "父ID字段名");
        }

        String database = req.getDatabase();
        if (FuncUtil.isNotEmpty(database)) {
            validateIdentifier(database, "数据库名");
        }

        String tableName = req.getTableName();
        String valueColumn = req.getValueColumn();
        String labelColumn = req.getLabelColumn();

        // 切换数据源
        boolean needSwitch = FuncUtil.isNotEmpty(req.getDataSource());
        if (needSwitch) {
            jdbcConnectService.switchDataSource(req.getDataSource());
        }

        try {
            // 构建 SQL（条件值使用命名参数占位，防止 SQL 注入）
            Map<String, Object> params = new HashMap<>();
            String sql = buildQuerySQL(req, params);

            // 执行查询
            List<Map<String, Object>> rows = jdbcConnectService.query(sql, params);

            // 转换为 DynamicDictItemVO 列表
            boolean hasPid = FuncUtil.isNotEmpty(req.getPidColumn());
            return rows.stream()
                    .map(row -> {
                        DynamicDictItemVO vo = new DynamicDictItemVO();
                        vo.setValue(String.valueOf(row.get("value")));
                        Object labelVal = row.get("label");
                        vo.setLabel(labelVal != null ? String.valueOf(labelVal) : "");
                        if (hasPid) {
                            Object pidVal = row.get("pid");
                            vo.setPid(pidVal != null ? String.valueOf(pidVal) : null);
                        }
                        return vo;
                    })
                    .collect(Collectors.toList());
        } finally {
            if (needSwitch) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }
    }

    /**
     * 构建 SQL 查询语句
     * <p>
     * Doris 对 SELECT DISTINCT 的 ORDER BY 有严格限制：ORDER BY 的列必须出现在 SELECT 列表中。
     * 如果 ORDER BY 列不在 valueColumn / labelColumn 中，则通过子查询包装，
     * 将排序列加入内层 DISTINCT 的 SELECT 列表，外层只投影 value 和 label。
     */
    private String buildQuerySQL(DynamicDictReq req, Map<String, Object> params) {
        String valueColumn = req.getValueColumn();
        String labelColumn = req.getLabelColumn();
        String pidColumn = req.getPidColumn();
        boolean hasPid = FuncUtil.isNotEmpty(pidColumn);

        // 解析 ORDER BY：提取列名和排序方向
        String orderByColumnOnly = null;
        String orderByFull = null;
        if (FuncUtil.isNotEmpty(req.getOrderBy())) {
            orderByFull = req.getOrderBy().trim();
            if (!ORDER_BY_PATTERN.matcher(orderByFull).matches()) {
                throw new RuntimeException("排序参数格式不合法: " + orderByFull);
            }
            String[] parts = orderByFull.split("\\s+");
            orderByColumnOnly = parts[0];
        }

        // 判断是否需要子查询：当 orderBy 列不在 SELECT DISTINCT 的列中时，需要子查询
        boolean needSubquery = orderByColumnOnly != null
                && !orderByColumnOnly.equals(valueColumn)
                && !orderByColumnOnly.equals(labelColumn)
                && !(hasPid && orderByColumnOnly.equals(pidColumn));

        StringBuilder sql = new StringBuilder();

        // 子查询包装
        if (needSubquery) {
            sql.append("SELECT `value`, `label`");
            if (hasPid) {
                sql.append(", `pid`");
            }
            sql.append(" FROM (");
        }

        sql.append("SELECT DISTINCT ");
        sql.append("`").append(valueColumn).append("` AS `value`, ");
        sql.append("`").append(labelColumn).append("` AS `label`");

        // 树形模式：加上 pid 列
        if (hasPid) {
            sql.append(", ").append("`").append(pidColumn).append("` AS `pid`");
        }

        // 当需要子查询时，将排序列也加入 DISTINCT 的 SELECT 列表
        if (needSubquery) {
            sql.append(", ").append("`").append(orderByColumnOnly).append("`");
        }

        // FROM
        String qualifiedTable = buildQualifiedTable(req.getDatabase(), req.getTableName());
        sql.append(" FROM ").append(qualifiedTable);

        // WHERE 条件
        List<String> whereClauses = new ArrayList<>();

        // 用户自定义条件
        if (FuncUtil.isNotEmpty(req.getConditions())) {
            for (DynamicDictCondition cond : req.getConditions()) {
                if (FuncUtil.isEmpty(cond.getColumn())) {
                    continue;
                }
                validateIdentifier(cond.getColumn(), "条件列名");
                String clause = buildConditionClause(cond, params);
                if (clause != null) {
                    whereClauses.add(clause);
                }
            }
        }

        // 过滤掉 value 和 label 为 NULL 或空字符串的记录
        whereClauses.add("`" + valueColumn + "` IS NOT NULL");
        whereClauses.add("`" + valueColumn + "` != ''");

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        // 关闭子查询
        if (needSubquery) {
            sql.append(") t");
        }

        // ORDER BY
        if (orderByFull != null) {
            sql.append(" ORDER BY ").append(orderByFull);
        } else {
            sql.append(" ORDER BY `").append(valueColumn).append("` ASC");
        }

        return sql.toString();
    }

    /**
     * 构建单个条件的 SQL 片段
     */
    private String buildConditionClause(DynamicDictCondition cond, Map<String, Object> params) {
        String operator = cond.getOperator();
        if (FuncUtil.isEmpty(operator)) {
            // 默认使用 = 操作符
            operator = "=";
        }
        operator = operator.toUpperCase().trim();

        if (!VALID_OPERATORS.contains(operator)) {
            throw new RuntimeException("不支持的操作符: " + operator + "，支持: " + VALID_OPERATORS);
        }

        String col = "`" + cond.getColumn() + "`";

        switch (operator) {
            case "IS NULL":
                return col + " IS NULL";
            case "IS NOT NULL":
                return col + " IS NOT NULL";
            case "LIKE": {
                Validator.assertNotBlank(cond.getValue(), ErrCodeSys.PA_PARAM_NULL, "LIKE条件值");
                String paramName = nextParamName(params);
                params.put(paramName, cond.getValue());
                return col + " LIKE :" + paramName;
            }
            case "=":
            case "!=": {
                Validator.assertNotBlank(cond.getValue(), ErrCodeSys.PA_PARAM_NULL, operator + "条件值");
                String paramName = nextParamName(params);
                params.put(paramName, convertValue(cond.getValue()));
                return col + " " + operator + " :" + paramName;
            }
            default:
                return null;
        }
    }

    /**
     * 生成唯一的命名参数占位名（基于当前参数数量递增）
     */
    private String nextParamName(Map<String, Object> params) {
        return "cond" + params.size();
    }

    /**
     * 转换条件值类型：数字型字符串转为数值，其余保持字符串，
     * 由 NamedParameterJdbcTemplate 负责安全绑定，避免 SQL 注入
     */
    private Object convertValue(String value) {
        if (isNumeric(value)) {
            try {
                if (value.contains(".")) {
                    return Double.parseDouble(value);
                }
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return value;
            }
        }
        return value;
    }

    /**
     * 判断字符串是否为数字
     */
    private boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ==================== 配置 CRUD ====================

    /**
     * 保存动态字典配置（新增或更新）
     * <p>
     * 注意：不加 @Transactional，因为 refreshSingleConfig 需要切换到 DORIS 数据源查询，
     * 如果外层有事务，Spring 会预先绑定 MySQL 连接到当前线程，
     * 导致 switchDataSource 无法生效（JdbcTemplate 拿到的是已绑定的 MySQL 连接）。
     * configService.saveOrUpdate 自带事务，无需外层包裹。
     */
    public void saveConfig(DynamicDictReq req) {
        Validator.assertNotBlank(req.getDictCode(), ErrCodeSys.PA_PARAM_NULL, "字典编码");
        Validator.assertNotBlank(req.getDictName(), ErrCodeSys.PA_PARAM_NULL, "字典名称");
        Validator.assertNotBlank(req.getTableName(), ErrCodeSys.PA_PARAM_NULL, "表名");
        Validator.assertNotBlank(req.getValueColumn(), ErrCodeSys.PA_PARAM_NULL, "value字段名");
        Validator.assertNotBlank(req.getLabelColumn(), ErrCodeSys.PA_PARAM_NULL, "label字段名");

        validateTableName(req.getTableName());
        validateIdentifier(req.getValueColumn(), "value字段名");
        validateIdentifier(req.getLabelColumn(), "label字段名");

        // 查找是否已存在同 dictCode 的配置（包含软删除记录，避免撞唯一键 uk_dict_code）
        SysDynamicDictConfig existing = configService.getByDictCodeIncludeInvalid(req.getDictCode());
        SysDynamicDictConfig config;
        if (existing != null) {
            // 已存在（含软删除）→ 复用该记录，下方统一置 valid=1 重新激活
            config = existing;
        } else {
            config = new SysDynamicDictConfig();
        }

        config.setValid(CommonConst.YES);
        config.setDictCode(req.getDictCode());
        config.setDictName(req.getDictName());
        config.setDataSource(req.getDataSource());
        config.setDatabaseName(req.getDatabase());
        config.setTableName(req.getTableName());
        config.setValueColumn(req.getValueColumn());
        config.setLabelColumn(req.getLabelColumn());
        config.setOrderBy(req.getOrderBy());
        config.setPidColumn(req.getPidColumn());

        // 序列化条件为 JSON
        if (FuncUtil.isNotEmpty(req.getConditions())) {
            config.setConditions(JsonUtil.toJson(req.getConditions()));
        } else {
            config.setConditions(null);
        }

        configService.saveOrUpdate(config);

        // 立即刷新该配置的数据
        refreshSingleConfig(config);
    }

    /**
     * 获取所有动态字典配置列表
     *
     * @param keyword 模糊搜索关键字（匹配dictName或dictCode），可为null
     */
    public List<SysDynamicDictConfig> getConfigList(String keyword) {
        return configService.getAllValidConfigs(keyword);
    }

    /**
     * 删除动态字典配置（软删除），同时清除对应的业务字典数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long id) {
        SysDynamicDictConfig config = configService.getById(id);
        if (config == null) {
            return;
        }

        // 软删除配置
        config.setValid(CommonConst.NO);
        configService.updateById(config);

        // 删除对应的业务字典数据
        LambdaQueryWrapper<SysBizDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysBizDict::getDictCode, config.getDictCode());
        wrapper.isNull(SysBizDict::getBizId);
        sysBizDictService.remove(wrapper);
    }

    // ==================== 缓存刷新 ====================

    /**
     * 刷新所有动态字典配置的数据到 sys_biz_dict 表
     * <p>
     * 在 DictService.refresh() 中，先调用此方法更新业务字典数据，
     * 再调用 dictCacheService.refresh() 刷新内存缓存。
     */
    public void refreshDynamicDictData() {
        List<SysDynamicDictConfig> configs = configService.getAllValidConfigs(null);
        if (FuncUtil.isEmpty(configs)) {
            return;
        }

        for (SysDynamicDictConfig config : configs) {
            try {
                refreshSingleConfig(config);
            } catch (Exception e) {
                log.error("刷新动态字典配置失败: dictCode={}", config.getDictCode(), e);
            }
        }
    }

    /**
     * 刷新单个配置：执行SQL → 写入 sys_biz_dict
     */
    private void refreshSingleConfig(SysDynamicDictConfig config) {
        boolean isTreeMode = FuncUtil.isNotEmpty(config.getPidColumn());

        if (isTreeMode) {
            refreshTreeConfig(config);
        } else {
            refreshFlatConfig(config);
        }
    }

    /**
     * 平铺模式刷新（原有逻辑）
     */
    private void refreshFlatConfig(SysDynamicDictConfig config) {
        // 构建 DynamicDictReq
        DynamicDictReq req = new DynamicDictReq();
        req.setDataSource(config.getDataSource());
        req.setDatabase(config.getDatabaseName());
        req.setTableName(config.getTableName());
        req.setValueColumn(config.getValueColumn());
        req.setLabelColumn(config.getLabelColumn());
        req.setOrderBy(config.getOrderBy());

        // 反序列化条件
        if (FuncUtil.isNotEmpty(config.getConditions())) {
            List<DynamicDictCondition> conditions = JsonUtil.readJson(
                    config.getConditions(), List.class, DynamicDictCondition.class);
            req.setConditions(conditions);
        }

        // 执行查询
        List<DynamicDictItemVO> results = generateDict(req);

        // 组装最新字典数据
        List<SysBizDict> bizDictList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(results)) {
            int sort = 0;
            for (DynamicDictItemVO item : results) {
                SysBizDict bizDict = new SysBizDict();
                bizDict.setDictCode(config.getDictCode());
                bizDict.setDictName(config.getDictName());
                bizDict.setLabel(item.getLabel());
                bizDict.setValue(item.getValue());
                bizDict.setSort(sort++);
                bizDict.setValid(CommonConst.YES);
                bizDictList.add(bizDict);
            }
        }

        // 与现有数据增量比对同步
        syncBizDictData(config, bizDictList);

        log.debug("动态字典配置[{}]刷新完成，共{}条数据", config.getDictCode(), bizDictList.size());
    }

    /**
     * 树形模式刷新：从源表读取 id/pid/label，写入 sys_biz_dict 并设置 parent_dict_code 自引用
     */
    private void refreshTreeConfig(SysDynamicDictConfig config) {
        String idColumn = config.getValueColumn();
        String pidColumn = config.getPidColumn();
        String labelColumn = config.getLabelColumn();

        // 安全校验
        validateIdentifier(idColumn, "ID列名");
        validateIdentifier(pidColumn, "父级ID列名");
        validateIdentifier(labelColumn, "label列名");

        // 构建树形查询SQL（条件值使用命名参数占位，防止 SQL 注入）
        Map<String, Object> params = new HashMap<>();
        String sql = buildTreeQuerySQL(config, idColumn, pidColumn, labelColumn, params);

        // 切换数据源
        boolean needSwitch = FuncUtil.isNotEmpty(config.getDataSource());
        if (needSwitch) {
            jdbcConnectService.switchDataSource(config.getDataSource());
        }

        List<Map<String, Object>> rows;
        try {
            rows = jdbcConnectService.query(sql, params);
        } finally {
            if (needSwitch) {
                jdbcConnectService.resetToDefaultDataSource();
            }
        }

        // 组装最新树形字典数据
        List<SysBizDict> bizDictList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(rows)) {
            int sort = 0;
            for (Map<String, Object> row : rows) {
                SysBizDict bizDict = new SysBizDict();
                bizDict.setDictCode(config.getDictCode());
                bizDict.setDictName(config.getDictName());
                bizDict.setValue(String.valueOf(row.get("value")));
                Object labelVal = row.get("label");
                bizDict.setLabel(labelVal != null ? String.valueOf(labelVal) : "");
                // 树形自引用：parent_dict_code = dict_code
                bizDict.setParentDictCode(config.getDictCode());
                // parent_value = 源表的pid值（根节点pid为null）
                Object pidVal = row.get("parent_value");
                bizDict.setParentValue(pidVal != null ? String.valueOf(pidVal) : null);
                bizDict.setSort(sort++);
                bizDict.setValid(CommonConst.YES);
                bizDictList.add(bizDict);
            }
        }

        // 与现有数据增量比对同步
        syncBizDictData(config, bizDictList);

        log.debug("树形动态字典配置[{}]刷新完成，共{}条数据", config.getDictCode(), bizDictList.size());

        // 刷新树形字典内存缓存
        bizDictTreeCacheService.refreshSingle(config.getDictCode());
    }

    /**
     * 将最新字典数据与 sys_biz_dict 中现有数据增量比对同步（以 value 为业务键）：
     * 新增的插入、有变化的更新、已消失的删除，避免全删全插导致 id 变化和查询空窗
     */
    private void syncBizDictData(SysDynamicDictConfig config, List<SysBizDict> latestList) {
        // 查询现有数据
        LambdaQueryWrapper<SysBizDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysBizDict::getDictCode, config.getDictCode());
        wrapper.isNull(SysBizDict::getBizId);
        List<SysBizDict> existingList = sysBizDictService.list(wrapper);

        // 现有数据按 value 索引（同 value 的重复记录保留第一条，其余待删除）
        Map<String, SysBizDict> existingMap = new HashMap<>();
        List<Long> deleteIds = new ArrayList<>();
        for (SysBizDict existing : existingList) {
            if (existingMap.putIfAbsent(existing.getValue(), existing) != null) {
                deleteIds.add(existing.getId());
            }
        }

        // 比对：新增的收集待插入，已存在且有差异的按 id 更新
        List<SysBizDict> insertList = new ArrayList<>();
        for (SysBizDict latest : latestList) {
            SysBizDict existing = existingMap.remove(latest.getValue());
            if (existing == null) {
                insertList.add(latest);
            } else if (isDictItemChanged(existing, latest)) {
                // 显式 set 各字段，保证 null 值（如树形根节点 parentValue）也能覆盖
                LambdaUpdateWrapper<SysBizDict> updateWrapper = new LambdaUpdateWrapper<>();
                updateWrapper.eq(SysBizDict::getId, existing.getId());
                updateWrapper.set(SysBizDict::getDictName, latest.getDictName());
                updateWrapper.set(SysBizDict::getLabel, latest.getLabel());
                updateWrapper.set(SysBizDict::getSort, latest.getSort());
                updateWrapper.set(SysBizDict::getParentDictCode, latest.getParentDictCode());
                updateWrapper.set(SysBizDict::getParentValue, latest.getParentValue());
                updateWrapper.set(SysBizDict::getValid, latest.getValid());
                sysBizDictService.update(updateWrapper);
            }
        }

        // 源数据中已不存在的记录删除
        for (SysBizDict existing : existingMap.values()) {
            deleteIds.add(existing.getId());
        }
        if (FuncUtil.isNotEmpty(deleteIds)) {
            sysBizDictService.removeBatchByIds(deleteIds);
        }
        if (FuncUtil.isNotEmpty(insertList)) {
            sysBizDictService.saveBatch(insertList);
        }
    }

    /**
     * 判断字典项是否有变化（value 为业务键不参与比对）
     */
    private boolean isDictItemChanged(SysBizDict existing, SysBizDict latest) {
        return !Objects.equals(existing.getDictName(), latest.getDictName())
                || !Objects.equals(existing.getLabel(), latest.getLabel())
                || !Objects.equals(existing.getSort(), latest.getSort())
                || !Objects.equals(existing.getParentDictCode(), latest.getParentDictCode())
                || !Objects.equals(existing.getParentValue(), latest.getParentValue())
                || !Objects.equals(existing.getValid(), latest.getValid());
    }

    /**
     * 构建树形查询SQL
     */
    private String buildTreeQuerySQL(SysDynamicDictConfig config, String idColumn, String pidColumn, String labelColumn, Map<String, Object> params) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("`").append(idColumn).append("` AS `value`, ");
        sql.append("`").append(labelColumn).append("` AS `label`, ");
        sql.append("`").append(pidColumn).append("` AS `parent_value`");
        sql.append(" FROM ").append(buildQualifiedTable(config.getDatabaseName(), config.getTableName()));

        // WHERE 条件
        List<String> whereClauses = new ArrayList<>();
        if (FuncUtil.isNotEmpty(config.getConditions())) {
            List<DynamicDictCondition> conditions = JsonUtil.readJson(
                    config.getConditions(), List.class, DynamicDictCondition.class);
            if (FuncUtil.isNotEmpty(conditions)) {
                for (DynamicDictCondition cond : conditions) {
                    if (FuncUtil.isEmpty(cond.getColumn())) continue;
                    validateIdentifier(cond.getColumn(), "条件列名");
                    String clause = buildConditionClause(cond, params);
                    if (clause != null) whereClauses.add(clause);
                }
            }
        }
        // 过滤 value 为空的记录
        whereClauses.add("`" + idColumn + "` IS NOT NULL");
        whereClauses.add("`" + idColumn + "` != ''");

        if (!whereClauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", whereClauses));
        }

        // ORDER BY
        if (FuncUtil.isNotEmpty(config.getOrderBy())) {
            sql.append(" ORDER BY ").append(config.getOrderBy());
        } else {
            sql.append(" ORDER BY `").append(idColumn).append("` ASC");
        }

        return sql.toString();
    }

    // ==================== 工具方法 ====================

    private String buildQualifiedTable(String database, String tableName) {
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(".");
                sb.append("`").append(parts[i]).append("`");
            }
            return sb.toString();
        }
        if (FuncUtil.isNotEmpty(database)) {
            return "`" + database + "`.`" + tableName + "`";
        }
        return "`" + tableName + "`";
    }

    private void validateIdentifier(String identifier, String fieldDesc) {
        if (!VALID_IDENTIFIER.matcher(identifier).matches()) {
            throw new RuntimeException(fieldDesc + "包含非法字符: " + identifier);
        }
    }

    private void validateTableName(String tableName) {
        if (!VALID_QUALIFIED_TABLE.matcher(tableName).matches()) {
            throw new RuntimeException("表名包含非法字符: " + tableName);
        }
    }
}
