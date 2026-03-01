package com.bidr.kernel.config.db;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sharp
 * @since 2026/2/28 16:22
 */

public class DynamicTableNameHolder {

    private static final ThreadLocal<Map<String, String>> DYNAMIC_TABLE_NAME_HOLDER = new ThreadLocal<>();

    private DynamicTableNameHolder() {

    }

    public static void set(String originalTableName, String targetTableName) {
        if (DYNAMIC_TABLE_NAME_HOLDER.get() == null) {
            DYNAMIC_TABLE_NAME_HOLDER.set(new HashMap<>(15));
        }
        DYNAMIC_TABLE_NAME_HOLDER.get().put(originalTableName, targetTableName);
    }


    public static Map<String, String> get() {
        return DYNAMIC_TABLE_NAME_HOLDER.get();
    }

    public static String get(String originalTableName) {
        if (DYNAMIC_TABLE_NAME_HOLDER.get() != null) {
            return DYNAMIC_TABLE_NAME_HOLDER.get().get(originalTableName);
        } else {
            return null;
        }

    }

    public static void clear(String originalTableName) {
        if (DYNAMIC_TABLE_NAME_HOLDER.get() != null) {
            DYNAMIC_TABLE_NAME_HOLDER.get().remove(originalTableName);
        }
    }

    public static void clear() {
        if (DYNAMIC_TABLE_NAME_HOLDER.get() != null) {
            DYNAMIC_TABLE_NAME_HOLDER.remove();
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    public static class DynamicTableName {
        private String originalTableName;
        private String targetTableName;
    }
}
