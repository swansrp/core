package com.bidr.td.repository;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.vo.portal.AdvancedQueryReq;
import com.bidr.td.annotation.*;
import com.bidr.td.sync.TdAdvancedQueryParser;
import com.bidr.td.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public abstract class BaseTdRepo<T> {

    private static final Logger log = LoggerFactory.getLogger(BaseTdRepo.class);
    private static final TdAdvancedQueryParser PARSER = new TdAdvancedQueryParser();
    private static final Set<String> ALLOWED_AGGR_FUNCS = Set.of(
        "COUNT", "SUM", "AVG", "MAX", "MIN", "SPREAD", "FIRST", "LAST",
        "TOP", "BOTTOM", "PERCENTILE", "STDDEV", "LEASTSQUARES"
    );
    private static final Set<String> ALLOWED_FILL = Set.of("NONE", "PREV", "LINEAR", "NULL");
    protected final JdbcTemplate taosJdbcTemplate;
    protected final Class<T> entityClass;
    protected final String stableName;

    protected BaseTdRepo(JdbcTemplate taosJdbcTemplate, Class<T> entityClass) {
        this.taosJdbcTemplate = taosJdbcTemplate;
        this.entityClass = entityClass;
        TdStable anno = entityClass.getAnnotation(TdStable.class);
        this.stableName = (anno != null) ? anno.value() : entityClass.getSimpleName();
        validateIdentifier(this.stableName, "stableName");
    }

    // ============ INSERT ============

    public void insertOne(String subTableName, T entity) {
        validateIdentifier(subTableName, "subTableName");
        // 收集 columns 和 tags 的值
        List<Object> colValues = new ArrayList<>();
        List<Object> tagValues = new ArrayList<>();
        List<String> colNames = new ArrayList<>();
        List<String> tagNames = new ArrayList<>();

        reflectEntity(entity, colValues, tagValues, colNames, tagNames);

        if (tagNames.isEmpty()) {
            // 无 tag：直接插入
            String cols = String.join(", ", colNames);
            String placeholders = colNames.stream().map(c -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO " + subTableName + " (" + cols + ") VALUES (" + placeholders + ")";
            taosJdbcTemplate.update(sql, colValues.toArray());
        } else {
            // 有 tag：USING STABLE ... TAGS(...)
            String cols = String.join(", ", colNames);
            String placeholders = colNames.stream().map(c -> "?").collect(Collectors.joining(", "));
            String tagPlaceholders = tagNames.stream().map(t -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO " + subTableName + " USING " + stableName + " TAGS (" + tagPlaceholders +
                    ") (" + cols + ") VALUES (" + placeholders + ")";
            List<Object> allParams = new ArrayList<>();
            allParams.addAll(tagValues);
            allParams.addAll(colValues);
            taosJdbcTemplate.update(sql, allParams.toArray());
        }
    }

    public void insertBatch(String subTableName, List<T> entities) {
        validateIdentifier(subTableName, "subTableName");
        if (entities == null || entities.isEmpty()) return;

        // TDengine 批量插入语法：INSERT INTO t USING s TAGS(...) (cols) VALUES (v1) (v2) (v3)
        // 使用第一个 entity 提取列名和 tag 值，后续 entity 只追 VALUES
        List<Object> allParams = new ArrayList<>();

        // 从第一个 entity 提取结构
        T first = entities.get(0);
        List<Object> firstColValues = new ArrayList<>();
        List<Object> firstTagValues = new ArrayList<>();
        List<String> colNames = new ArrayList<>();
        List<String> tagNames = new ArrayList<>();
        reflectEntity(first, firstColValues, firstTagValues, colNames, tagNames);

        String cols = "(" + String.join(", ", colNames) + ")";
        String colPlaceholders = colNames.stream().map(c -> "?").collect(Collectors.joining(", "));
        String tagPlaceholders = tagNames.stream().map(t -> "?").collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(subTableName)
           .append(" USING ").append(stableName).append(" TAGS (").append(tagPlaceholders).append(") ")
           .append(cols).append(" VALUES (").append(colPlaceholders).append(") ");
        allParams.addAll(firstTagValues);
        allParams.addAll(firstColValues);

        // 后续 entity 只追 VALUES，不再重复 USING TAGS
        for (int i = 1; i < entities.size(); i++) {
            T entity = entities.get(i);
            List<Object> colValues = new ArrayList<>();
            List<Object> tagValues = new ArrayList<>();
            List<String> tmpColNames = new ArrayList<>();
            List<String> tmpTagNames = new ArrayList<>();
            reflectEntity(entity, colValues, tagValues, tmpColNames, tmpTagNames);

            sql.append("(").append(colPlaceholders).append(") ");
            allParams.addAll(colValues);
        }

        taosJdbcTemplate.update(sql.toString(), allParams.toArray());
    }

    public void insertMultiTableBatch(Map<String, List<T>> tableDataMap) {
        if (tableDataMap == null || tableDataMap.isEmpty()) return;
        for (Map.Entry<String, List<T>> entry : tableDataMap.entrySet()) {
            insertBatch(entry.getKey(), entry.getValue());
        }
    }

    // ============ QUERY ============

    public Page<Map<String, Object>> queryRange(TdRangeReq req) {
        AdvancedQueryReq advanced = req.getAdvanced();
        long page = (advanced != null && advanced.getCurrentPage() != null) ? advanced.getCurrentPage() : 1;
        long size = (advanced != null && advanced.getPageSize() != null) ? advanced.getPageSize() : 20;
        long offset = (page - 1) * size;

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(stableName).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (advanced != null && advanced.getCondition() != null) {
            TdAdvancedQueryParser.ParsedCondition pc = PARSER.parse(advanced.getCondition());
            if (!"1=1".equals(pc.getClause())) {
                sql.append(" AND ").append(pc.getClause());
                params.addAll(pc.getParams());
            }
        }
        if (req.getFrom() != null) {
            sql.append(" AND ts >= ?");
            params.add(new Timestamp(req.getFrom()));
        }
        if (req.getTo() != null) {
            sql.append(" AND ts < ?");
            params.add(new Timestamp(req.getTo()));
        }
        sql.append(" ORDER BY ts DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        List<Map<String, Object>> records = taosJdbcTemplate.queryForList(sql.toString(), params.toArray());

        Page<Map<String, Object>> result = new Page<>(page, size);
        result.setRecords(records);
        // NOTE: TDengine 不支持 COUNT(*) 大表精确计数，这里使用估算值
        // 实际 total 可能不精确，仅作分页 UI 参考
        result.setTotal(records.size() < size ? (page - 1) * size + records.size() : page * size + 1);
        return result;
    }

    public List<Map<String, Object>> queryLast(AdvancedQueryReq req, List<String> groupByTags) {
        if (groupByTags != null) {
            for (String tag : groupByTags) {
                validateIdentifier(tag, "groupByTag");
            }
        }
        StringBuilder sql = new StringBuilder("SELECT LAST_ROW(*) FROM ").append(stableName).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (req != null && req.getCondition() != null) {
            TdAdvancedQueryParser.ParsedCondition pc = PARSER.parse(req.getCondition());
            if (!"1=1".equals(pc.getClause())) {
                sql.append(" AND ").append(pc.getClause());
                params.addAll(pc.getParams());
            }
        }
        if (groupByTags != null && !groupByTags.isEmpty()) {
            sql.append(" GROUP BY ").append(String.join(", ", groupByTags));
        }
        return taosJdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> queryAdvanced(TdAdvancedReq req) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(stableName).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (req.getAdvanced() != null && req.getAdvanced().getCondition() != null) {
            TdAdvancedQueryParser.ParsedCondition pc = PARSER.parse(req.getAdvanced().getCondition());
            if (!"1=1".equals(pc.getClause())) {
                sql.append(" AND ").append(pc.getClause());
                params.addAll(pc.getParams());
            }
        }
        if (req.getFrom() != null) {
            sql.append(" AND ts >= ?");
            params.add(new Timestamp(req.getFrom()));
        }
        if (req.getTo() != null) {
            sql.append(" AND ts < ?");
            params.add(new Timestamp(req.getTo()));
        }
        sql.append(" ORDER BY ts DESC LIMIT ?");
        long limit = (req.getAdvanced() != null && req.getAdvanced().getPageSize() != null)
                ? req.getAdvanced().getPageSize() : 1000;
        params.add(limit);

        return taosJdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> queryInterval(TdIntervalReq req) {
        validateIntervalFormat(req.getWindow(), "window");
        if (req.getSliding() != null && !req.getSliding().isEmpty()) {
            validateIntervalFormat(req.getSliding(), "sliding");
        }
        // Validate aggregation functions to prevent SQL injection
        if (req.getFuncs() != null) {
            for (String func : req.getFuncs()) {
                String funcName = func.contains("(") ? func.substring(0, func.indexOf("(")).trim() : func.trim();
                if (!funcName.isEmpty() && !ALLOWED_AGGR_FUNCS.contains(funcName.toUpperCase())) {
                    throw new IllegalArgumentException("Invalid aggregation function: " + func);
                }
            }
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(req.getFuncs() != null ? String.join(", ", req.getFuncs()) : "COUNT(*)")
                .append(" FROM ").append(stableName).append(" WHERE 1=1");

        if (req.getAdvanced() != null && req.getAdvanced().getCondition() != null) {
            TdAdvancedQueryParser.ParsedCondition pc = PARSER.parse(req.getAdvanced().getCondition());
            if (!"1=1".equals(pc.getClause())) {
                sql.append(" AND ").append(pc.getClause());
                params.addAll(pc.getParams());
            }
        }
        if (req.getFrom() != null) {
            sql.append(" AND ts >= ?");
            params.add(new Timestamp(req.getFrom()));
        }
        if (req.getTo() != null) {
            sql.append(" AND ts < ?");
            params.add(new Timestamp(req.getTo()));
        }
        sql.append(" INTERVAL(").append(req.getWindow()).append(")");
        if (req.getSliding() != null && !req.getSliding().isEmpty()) {
            sql.append(" SLIDING(").append(req.getSliding()).append(")");
        }
        if (req.getFill() != null && !req.getFill().isEmpty()) {
            String fill = req.getFill().toUpperCase();
            if (!ALLOWED_FILL.contains(fill)) {
                throw new IllegalArgumentException("Invalid fill value: " + req.getFill() + ". Allowed: " + ALLOWED_FILL);
            }
            sql.append(" FILL(").append(fill).append(")");
        }
        return taosJdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> queryGroupByTag(TdGroupReq req) {
        if (req.getGroupByTags() != null) {
            for (String tag : req.getGroupByTags()) {
                validateIdentifier(tag, "groupByTag");
            }
        }
        // Validate funcs against SQL injection whitelist
        if (req.getFuncs() != null) {
            for (String func : req.getFuncs()) {
                String funcUpper = func.toUpperCase().trim();
                // Allow simple function names like "COUNT(*)" or "SUM(field)"
                String funcName = funcUpper.replaceAll("\\(.*\\)", "").trim();
                if (!ALLOWED_AGGR_FUNCS.contains(funcName)) {
                    throw new IllegalArgumentException("Invalid aggregation function: " + func + ". Allowed: " + ALLOWED_AGGR_FUNCS);
                }
            }
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(String.join(", ", req.getGroupByTags()))
                .append(", ")
                .append(req.getFuncs() != null ? String.join(", ", req.getFuncs()) : "COUNT(*)")
                .append(" FROM ").append(stableName)
                .append(" WHERE 1=1");

        if (req.getAdvanced() != null && req.getAdvanced().getCondition() != null) {
            TdAdvancedQueryParser.ParsedCondition pc = PARSER.parse(req.getAdvanced().getCondition());
            if (!"1=1".equals(pc.getClause())) {
                sql.append(" AND ").append(pc.getClause());
                params.addAll(pc.getParams());
            }
        }
        sql.append(" GROUP BY ").append(String.join(", ", req.getGroupByTags()));
        return taosJdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> queryTopN(TdTopNReq req) {
        List<Object> params = new ArrayList<>();
        String direction = "TOP".equalsIgnoreCase(req.getDirection()) ? "TOP" : "BOTTOM";
        validateIdentifier(req.getField(), "field");
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(direction).append("(").append(req.getField()).append(", ?)")
                .append(" FROM ").append(stableName)
                .append(" WHERE 1=1");
        params.add(req.getN());

        if (req.getAdvanced() != null && req.getAdvanced().getCondition() != null) {
            TdAdvancedQueryParser.ParsedCondition pc = PARSER.parse(req.getAdvanced().getCondition());
            if (!"1=1".equals(pc.getClause())) {
                sql.append(" AND ").append(pc.getClause());
                params.addAll(pc.getParams());
            }
        }
        return taosJdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    // ============ UTILITY ============

    public void createSubTable(String subTableName, Map<String, Object> tags) {
        validateIdentifier(subTableName, "subTableName");
        String tagCols = String.join(", ", tags.keySet());
        String tagPlaceholders = tags.keySet().stream().map(k -> "?").collect(Collectors.joining(", "));
        String sql = "CREATE TABLE IF NOT EXISTS " + subTableName +
                " USING " + stableName + " TAGS (" + tagPlaceholders + ")";
        taosJdbcTemplate.update(sql, tags.values().toArray());
    }

    public void alterTagValue(String subTableName, String tagName, Object tagValue) {
        validateIdentifier(subTableName, "subTableName");
        validateIdentifier(tagName, "tagName");
        // RESTful 驱动参数绑定不会自动加引号，需要手动处理字符串值
        String valueStr;
        if (tagValue instanceof Number) {
            valueStr = String.valueOf(tagValue);
        } else {
            valueStr = "'" + String.valueOf(tagValue).replace("'", "\\'") + "'";
        }
        String sql = "ALTER TABLE " + subTableName + " SET TAG " + tagName + " = " + valueStr;
        taosJdbcTemplate.execute(sql);
    }

    public void dropSubTable(String subTableName) {
        validateIdentifier(subTableName, "subTableName");
        taosJdbcTemplate.execute("DROP TABLE IF EXISTS " + subTableName);
    }

    public String getStableName() {
        return stableName;
    }

    // ============ VALIDATION HELPERS ============

    private void validateIdentifier(String name, String label) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
        if (!name.matches("^[a-zA-Z_][a-zA-Z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(label + " '" + name + "' is not a valid identifier");
        }
    }

    private void validateIntervalFormat(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be null or empty");
        }
        // m = minute, s = second, h = hour, d = day (TDengine interval specification)
        if (!value.matches("^\\d+[mshd]$")) {
            throw new IllegalArgumentException(label + " '" + value + "' must match pattern ^\\d+[mshd]$");
        }
    }

    // ============ REFLECTION HELPERS ============

    private void reflectEntity(T entity,
                                List<Object> colValues, List<Object> tagValues,
                                List<String> colNames, List<String> tagNames) {
        try {
            // 按 Timestamp → Column → Tag 顺序排序，每个组内保持声明顺序
            List<Field> tsFields = new ArrayList<>();
            List<Field> colFields = new ArrayList<>();
            List<Field> tagFields = new ArrayList<>();
            for (Field field : getAllFields(entityClass)) {
                if (field.isAnnotationPresent(TdTimestamp.class)) {
                    tsFields.add(field);
                } else if (field.isAnnotationPresent(TdColumn.class)) {
                    colFields.add(field);
                } else if (field.isAnnotationPresent(TdTag.class)) {
                    tagFields.add(field);
                }
            }
            List<Field> ordered = new ArrayList<>();
            ordered.addAll(tsFields);
            ordered.addAll(colFields);
            ordered.addAll(tagFields);

            for (Field field : ordered) {
                field.setAccessible(true);
                Object value = field.get(entity);

                if (field.isAnnotationPresent(TdTimestamp.class)) {
                    TdTimestamp t = field.getAnnotation(TdTimestamp.class);
                    colNames.add(t.name());
                    colValues.add(value instanceof Long ? new Timestamp((Long) value) : value);
                } else if (field.isAnnotationPresent(TdColumn.class)) {
                    TdColumn c = field.getAnnotation(TdColumn.class);
                    String name = c.name().isEmpty() ? field.getName() : c.name();
                    colNames.add(name);
                    colValues.add(value);
                } else if (field.isAnnotationPresent(TdTag.class)) {
                    TdTag t = field.getAnnotation(TdTag.class);
                    String name = t.name().isEmpty() ? field.getName() : t.name();
                    tagNames.add(name);
                    tagValues.add(value);
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Reflect entity error", e);
        }
    }

    /**
     * Get all fields including inherited fields from superclasses.
     */
    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }
}
