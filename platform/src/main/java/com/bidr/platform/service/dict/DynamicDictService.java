package com.bidr.platform.service.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.jdbc.JdbcConnectService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.entity.SysDynamicDictConfig;
import com.bidr.platform.dao.repository.SysBizDictService;
import com.bidr.platform.dao.repository.SysDynamicDictConfigService;
import com.bidr.platform.vo.dict.DynamicDictCondition;
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

    private final JdbcConnectService jdbcConnectService;
    private final SysDynamicDictConfigService configService;
    private final SysBizDictService sysBizDictService;

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

    // ==================== 动态查询 ====================

    /**
     * 根据请求生成动态字典选项
     *
     * @param req 动态字典请求
     * @return 字典选项列表（value-label 键值对）
     */
    public List<KeyValueResVO> generateDict(DynamicDictReq req) {
        // 参数校验
        Validator.assertNotBlank(req.getTableName(), ErrCodeSys.PA_PARAM_NULL, "表名");
        Validator.assertNotBlank(req.getValueColumn(), ErrCodeSys.PA_PARAM_NULL, "value字段名");
        Validator.assertNotBlank(req.getLabelColumn(), ErrCodeSys.PA_PARAM_NULL, "label字段名");

        // 安全校验
        validateTableName(req.getTableName());
        validateIdentifier(req.getValueColumn(), "value字段名");
        validateIdentifier(req.getLabelColumn(), "label字段名");

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
            // 构建 SQL
            String sql = buildQuerySQL(req);

            // 执行查询
            List<Map<String, Object>> rows = jdbcConnectService.query(sql, new HashMap<>());

            // 转换为 KeyValueResVO 列表
            return rows.stream()
                    .map(row -> {
                        KeyValueResVO vo = new KeyValueResVO();
                        vo.setValue(String.valueOf(row.get("value")));
                        Object labelVal = row.get("label");
                        vo.setLabel(labelVal != null ? String.valueOf(labelVal) : "");
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
    private String buildQuerySQL(DynamicDictReq req) {
        String valueColumn = req.getValueColumn();
        String labelColumn = req.getLabelColumn();

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
                && !orderByColumnOnly.equals(labelColumn);

        StringBuilder sql = new StringBuilder();

        // 子查询包装
        if (needSubquery) {
            sql.append("SELECT `value`, `label` FROM (");
        }

        sql.append("SELECT DISTINCT ");
        sql.append("`").append(valueColumn).append("` AS `value`, ");
        sql.append("`").append(labelColumn).append("` AS `label`");

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
                String clause = buildConditionClause(cond);
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
    private String buildConditionClause(DynamicDictCondition cond) {
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
            case "LIKE":
                Validator.assertNotBlank(cond.getValue(), ErrCodeSys.PA_PARAM_NULL, "LIKE条件值");
                return col + " LIKE '" + escapeSqlValue(cond.getValue()) + "'";
            case "=":
            case "!=":
                Validator.assertNotBlank(cond.getValue(), ErrCodeSys.PA_PARAM_NULL, operator + "条件值");
                return col + " " + operator + " " + formatValue(cond.getValue());
            default:
                return null;
        }
    }

    /**
     * 格式化值：数字不加引号，字符串加单引号
     */
    private String formatValue(String value) {
        if (isNumeric(value)) {
            return value;
        }
        return "'" + escapeSqlValue(value) + "'";
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

    /**
     * 转义SQL字符串中的单引号
     */
    private String escapeSqlValue(String value) {
        return value.replace("'", "''");
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

        // 查找是否已存在同 dictCode 的配置
        SysDynamicDictConfig existing = configService.getByDictCode(req.getDictCode());
        SysDynamicDictConfig config;
        if (existing != null) {
            config = existing;
        } else {
            config = new SysDynamicDictConfig();
            config.setValid(CommonConst.YES);
        }

        config.setDictCode(req.getDictCode());
        config.setDictName(req.getDictName());
        config.setDataSource(req.getDataSource());
        config.setDatabaseName(req.getDatabase());
        config.setTableName(req.getTableName());
        config.setValueColumn(req.getValueColumn());
        config.setLabelColumn(req.getLabelColumn());
        config.setOrderBy(req.getOrderBy());

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
     */
    public List<SysDynamicDictConfig> getConfigList() {
        return configService.getAllValidConfigs();
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
        List<SysDynamicDictConfig> configs = configService.getAllValidConfigs();
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
        List<KeyValueResVO> results = generateDict(req);

        // 删除旧的业务字典数据
        LambdaQueryWrapper<SysBizDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysBizDict::getDictCode, config.getDictCode());
        wrapper.isNull(SysBizDict::getBizId);
        sysBizDictService.remove(wrapper);

        // 插入新的业务字典数据
        if (FuncUtil.isNotEmpty(results)) {
            List<SysBizDict> bizDictList = new ArrayList<>();
            int sort = 0;
            for (KeyValueResVO item : results) {
                SysBizDict bizDict = new SysBizDict();
                bizDict.setDictCode(config.getDictCode());
                bizDict.setDictName(config.getDictName());
                bizDict.setLabel(item.getLabel());
                bizDict.setValue(item.getValue());
                bizDict.setSort(sort++);
                bizDict.setValid(CommonConst.YES);
                bizDictList.add(bizDict);
            }
            sysBizDictService.saveBatch(bizDictList);
        }

        log.debug("动态字典配置[{}]刷新完成，共{}条数据", config.getDictCode(),
                FuncUtil.isNotEmpty(results) ? results.size() : 0);
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
