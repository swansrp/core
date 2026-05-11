package com.bidr.td.repository;

import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.td.annotation.*;
import com.bidr.td.constant.TdDataType;
import com.bidr.td.inf.TdSchemaInf;
import com.bidr.td.sync.TdSchemaDiffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public abstract class BaseTdSchema<T> implements TdSchemaInf {

    private static final Logger log = LoggerFactory.getLogger(BaseTdSchema.class);
    // DDL SQL map: stores manual DDL statements keyed by schema class name
    protected static final Map<String, String> DDL_SQL_MAP = new ConcurrentHashMap<>();
    protected static final Map<String, LinkedHashMap<Integer, String>> UPGRADE_SCRIPTS = new ConcurrentHashMap<>();
    protected static final Map<String, List<String>> INIT_DATA_SCRIPTS = new ConcurrentHashMap<>();

    protected Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);

    protected static void setCreateDDL(String sql) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        DDL_SQL_MAP.put(stack[2].getClassName(), sql);
    }

    protected static void setUpgradeDDL(Integer version, String sql) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        UPGRADE_SCRIPTS.computeIfAbsent(stack[2].getClassName(), k -> new LinkedHashMap<>()).put(version, sql);
    }

    protected static void setInitData(String sql) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        INIT_DATA_SCRIPTS.computeIfAbsent(stack[2].getClassName(), k -> new ArrayList<>()).add(sql);
    }

    @Override
    public String getStableName() {
        TdStable annotation = entityClass.getAnnotation(TdStable.class);
        if (annotation == null) {
            throw new IllegalStateException("Entity " + entityClass.getName() + " must have @TdStable annotation");
        }
        return annotation.value();
    }

    @Override
    public String getCreateStableSql() {
        String ddl = DDL_SQL_MAP.get(getClass().getName());
        if (ddl != null) return ddl;
        return buildCreateStableSql();
    }

    @Override
    public LinkedHashMap<Integer, String> getUpgradeScripts() {
        return UPGRADE_SCRIPTS.getOrDefault(getClass().getName(), new LinkedHashMap<>());
    }

    @Override
    public List<String> getInitDataScripts() {
        return INIT_DATA_SCRIPTS.getOrDefault(getClass().getName(), new ArrayList<>());
    }

    @Override
    public void initStable(JdbcTemplate taosJdbcTemplate) {
        String stableName = getStableName();
        try {
            String createSql = getCreateStableSql();
            String manualDDL = DDL_SQL_MAP.get(getClass().getName());

            if (manualDDL != null) {
                // 有手动 SQL
                String checkSql = "SELECT 1 FROM information_schema.ins_stables WHERE stable_name = ?";
                List<Map<String, Object>> exists = taosJdbcTemplate.queryForList(checkSql, stableName);
                if (exists.isEmpty()) {
                    // 新建表 — CREATE STABLE 失败应阻止启动
                    try {
                        taosJdbcTemplate.execute(manualDDL);
                    } catch (Exception e) {
                        log.error("Failed to create STABLE [{}] via manual DDL", stableName, e);
                        throw new RuntimeException("Failed to create STABLE [" + stableName + "]", e);
                    }
                    log.info("TD STABLE [{}] created via manual DDL", stableName);
                    handleInitData(stableName, taosJdbcTemplate);
                } else {
                    // 已有表时才执行 UPGRADE 脚本（新表已是最新，无需重复执行）
                    handleUpgradeDDL(stableName, getUpgradeScripts(), taosJdbcTemplate);
                }
            } else {
                // 无手动 SQL：使用 Diff 引擎自动同步
                boolean stableExists = checkStableExists(stableName, taosJdbcTemplate);
                if (!stableExists) {
                    // STABLE 不存在，先创建
                    try {
                        taosJdbcTemplate.execute(createSql);
                        log.info("TD STABLE [{}] created via auto DDL", stableName);
                        handleInitData(stableName, taosJdbcTemplate);
                    } catch (Exception e) {
                        log.error("Failed to create STABLE [{}] via auto DDL", stableName, e);
                        throw new RuntimeException("Failed to create STABLE [" + stableName + "]", e);
                    }
                } else {
                    // STABLE 已存在，用 diff 增量同步
                    TdStable stableAnno = entityClass.getAnnotation(TdStable.class);
                    boolean autoDrop = stableAnno != null && stableAnno.autoDrop();
                    TdSchemaDiffer differ = new TdSchemaDiffer(taosJdbcTemplate, stableName, entityClass);
                    List<String> alterStatements = differ.diff(autoDrop);
                    if (!alterStatements.isEmpty()) {
                        for (String alterSql : alterStatements) {
                            try {
                                taosJdbcTemplate.execute(alterSql);
                                log.info("AUTO SCHEMA: {}", alterSql);
                            } catch (Exception e) {
                                throw new RuntimeException("TD STABLE [" + stableName + "] AUTO SCHEMA failed: " + alterSql, e);
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            // DDL 同步失败应阻止启动，直接抛出
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("TD STABLE [" + stableName + "] init failed", e);
        }
    }

    private String buildCreateStableSql() {
        TdStable stableAnno = entityClass.getAnnotation(TdStable.class);
        if (stableAnno == null) return null;

        String stableName = stableAnno.value();
        List<ColumnDef> columns = new ArrayList<>();
        List<ColumnDef> tags = new ArrayList<>();
        String tsField = "ts";
        TdDataType tsType = TdDataType.TIMESTAMP;

        for (Field field : getAllFields(entityClass)) {
            if (field.isAnnotationPresent(TdTimestamp.class)) {
                TdTimestamp t = field.getAnnotation(TdTimestamp.class);
                tsField = t.name();
                columns.add(new ColumnDef(tsField, TdDataType.TIMESTAMP, 0));
            } else if (field.isAnnotationPresent(TdColumn.class)) {
                TdColumn c = field.getAnnotation(TdColumn.class);
                String name = c.name().isEmpty() ? field.getName() : c.name();
                columns.add(new ColumnDef(name, c.type(), c.length()));
            } else if (field.isAnnotationPresent(TdTag.class)) {
                TdTag t = field.getAnnotation(TdTag.class);
                String name = t.name().isEmpty() ? field.getName() : t.name();
                tags.add(new ColumnDef(name, t.type(), t.length()));
            }
        }

        if (columns.isEmpty()) {
            throw new IllegalStateException("No columns defined for STABLE " + stableName
                    + ". Entity " + entityClass.getName() + " must have at least one @TdTimestamp or @TdColumn field");
        }

        if (!columns.get(0).type.equals(TdDataType.TIMESTAMP)) {
            columns.add(0, new ColumnDef(tsField, TdDataType.TIMESTAMP, 0));
        }

        String colDefs = columns.stream()
                .map(c -> c.name + " " + c.type.toSql() + formatLength(c.type, c.length))
                .collect(Collectors.joining(", "));

        String tagDefs = tags.stream()
                .map(t -> t.name + " " + t.type.toSql() + formatLength(t.type, t.length))
                .collect(Collectors.joining(", "));

        return "CREATE STABLE IF NOT EXISTS " + stableName + " (" + colDefs + ") TAGS (" + tagDefs + ")";
    }

    private boolean checkStableExists(String stableName, JdbcTemplate taosJdbcTemplate) {
        try {
            List<Map<String, Object>> result = taosJdbcTemplate.queryForList(
                    "SELECT 1 FROM information_schema.ins_stables WHERE stable_name = ?", stableName);
            return !result.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void handleInitData(String stableName, JdbcTemplate t) {
        List<String> scripts = getInitDataScripts();
        if (!scripts.isEmpty()) {
            for (String sql : scripts) {
                try {
                    t.execute(sql);
                } catch (Exception e) {
                    log.error("INIT DATA error for [{}]: {}", stableName, e.getMessage());
                }
            }
        }
    }

    private void handleUpgradeDDL(String stableName, LinkedHashMap<Integer, String> upgradeScripts, JdbcTemplate t) {
        if (upgradeScripts == null || upgradeScripts.isEmpty()) return;
        for (Map.Entry<Integer, String> entry : upgradeScripts.entrySet()) {
            try {
                t.execute(entry.getValue());
                log.info("UPGRADE [{}] v{}: {}", stableName, entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.warn("UPGRADE [{}] v{} failed (可能已执行): {}", stableName, entry.getKey(), e.getMessage());
            }
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

    private static boolean needsLength(TdDataType type) {
        return type == TdDataType.BINARY || type == TdDataType.NCHAR;
    }

    private static String formatLength(TdDataType type, int length) {
        return (needsLength(type) && length > 0) ? "(" + length + ")" : "";
    }

    static class ColumnDef {
        final String name;
        final TdDataType type;
        final int length;
        ColumnDef(String name, TdDataType type, int length) {
            this.name = name;
            this.type = type;
            this.length = length;
        }
    }
}
