package com.bidr.td.sync;

import com.bidr.td.annotation.*;
import com.bidr.td.constant.TdDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class TdSchemaDiffer {

    private static final Logger log = LoggerFactory.getLogger(TdSchemaDiffer.class);
    private final JdbcTemplate taosJdbcTemplate;
    private final String stableName;
    private final Class<?> entityClass;

    public TdSchemaDiffer(JdbcTemplate taosJdbcTemplate, String stableName, Class<?> entityClass) {
        this.taosJdbcTemplate = taosJdbcTemplate;
        this.stableName = stableName;
        this.entityClass = entityClass;
    }

    public List<String> diff() {
        return diff(false);
    }

    public List<String> diff(boolean autoDrop) {
        boolean stableExists = checkStableExists();
        if (!stableExists) {
            return Collections.emptyList();
        }

        Map<String, TdColumnInfo> actualColumns = describe();
        ExpectedSchema expected = buildExpected();

        List<String> alterStatements = new ArrayList<>();

        // ADD COLUMN
        for (ColumnDef col : expected.columns) {
            if (!actualColumns.containsKey(col.name)) {
                alterStatements.add("ALTER STABLE " + stableName + " ADD COLUMN " +
                        col.name + " " + col.type.toSql() + (col.length > 0 ? "(" + col.length + ")" : ""));
            }
        }

        // ADD TAG
        for (ColumnDef tag : expected.tags) {
            if (!actualColumns.containsKey(tag.name)) {
                alterStatements.add("ALTER STABLE " + stableName + " ADD TAG " +
                        tag.name + " " + tag.type.toSql() + (tag.length > 0 ? "(" + tag.length + ")" : ""));
            }
        }

        // DROP extra columns/tags when autoDrop is enabled
        if (autoDrop) {
            for (Map.Entry<String, TdColumnInfo> entry : actualColumns.entrySet()) {
                String actualName = entry.getKey();
                TdColumnInfo info = entry.getValue();
                boolean foundInExpected = expected.columns.stream().anyMatch(c -> c.name.equals(actualName))
                        || expected.tags.stream().anyMatch(t -> t.name.equals(actualName));
                if (!foundInExpected) {
                    if (info.isTag) {
                        alterStatements.add("ALTER STABLE " + stableName + " DROP TAG " + actualName);
                    } else if (!"ts".equalsIgnoreCase(actualName)) {
                        // ts (timestamp) 列不可删除
                        alterStatements.add("ALTER STABLE " + stableName + " DROP COLUMN " + actualName);
                    }
                }
            }
        }

        return alterStatements;
    }

    // --- utility methods ---

    private boolean checkStableExists() {
        try {
            List<Map<String, Object>> result = taosJdbcTemplate.queryForList(
                    "SELECT 1 FROM information_schema.ins_stables WHERE stable_name = ?", stableName);
            return !result.isEmpty();
        } catch (Exception e) {
            throw new RuntimeException("Failed to check stable existence: " + stableName, e);
        }
    }

    private Map<String, TdColumnInfo> describe() {
        Map<String, TdColumnInfo> map = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = taosJdbcTemplate.queryForList("DESCRIBE " + stableName);
            for (Map<String, Object> row : rows) {
                TdColumnInfo info = new TdColumnInfo();
                // 对 Field 做 null 保护和类型 fallback
                Object fieldObj = row.get("Field");
                if (fieldObj == null) {
                    continue;
                }
                info.name = fieldObj instanceof String ? (String) fieldObj : String.valueOf(fieldObj);

                Object typeObj = row.get("Type");
                info.type = typeObj instanceof String ? (String) typeObj : (typeObj != null ? String.valueOf(typeObj) : "VARCHAR");

                Object noteObj = row.get("Note");
                info.isTag = "TAG".equals(noteObj instanceof String ? (String) noteObj : (noteObj != null ? String.valueOf(noteObj) : ""));
                map.put(info.name, info);
            }
        } catch (Exception e) {
            throw new RuntimeException("DESCRIBE " + stableName + " failed", e);
        }
        return map;
    }

    private ExpectedSchema buildExpected() {
        ExpectedSchema exp = new ExpectedSchema();
        for (Field field : getAllFields(entityClass)) {
            if (field.isAnnotationPresent(TdTimestamp.class)) {
                TdTimestamp t = field.getAnnotation(TdTimestamp.class);
                exp.columns.add(new ColumnDef(t.name(), TdDataType.TIMESTAMP, 0));
            } else if (field.isAnnotationPresent(TdColumn.class)) {
                TdColumn c = field.getAnnotation(TdColumn.class);
                String name = c.name().isEmpty() ? field.getName() : c.name();
                exp.columns.add(new ColumnDef(name, c.type(), c.length()));
            } else if (field.isAnnotationPresent(TdTag.class)) {
                TdTag t = field.getAnnotation(TdTag.class);
                String name = t.name().isEmpty() ? field.getName() : t.name();
                exp.tags.add(new ColumnDef(name, t.type(), t.length()));
            }
        }
        return exp;
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

    static class TdColumnInfo {
        String name;
        String type;
        boolean isTag;
    }

    static class ExpectedSchema {
        List<ColumnDef> columns = new ArrayList<>();
        List<ColumnDef> tags = new ArrayList<>();
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
