package com.bidr.authorization.annotation.data.scope;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: GroupDataScopeHolder
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/14 15:26
 */
public class GroupDataScopeHolder {
    private static final ThreadLocal<Map<String, Map<String, List<String>>>> GROUP_DATA_SCOPE_HOLDER =
            new ThreadLocal<>();

    private GroupDataScopeHolder() {
    }

    public static void set(String groupName, String customerNumber, List<String> subordinate) {
        if (GROUP_DATA_SCOPE_HOLDER.get() == null) {
            Map<String, Map<String, List<String>>> group = new LinkedHashMap<>();
            GROUP_DATA_SCOPE_HOLDER.set(group);
        } else if (GROUP_DATA_SCOPE_HOLDER.get().get(groupName) == null) {
            Map<String, List<String>> customerMap = new LinkedHashMap<>();
            GROUP_DATA_SCOPE_HOLDER.get().put(groupName, customerMap);
        } else {
            GROUP_DATA_SCOPE_HOLDER.get().get(groupName).put(customerNumber, subordinate);
        }
    }

    public static List<String> get(String groupName, String customerNumber) {
        if (GROUP_DATA_SCOPE_HOLDER.get() == null) {
            return null;
        } else if (GROUP_DATA_SCOPE_HOLDER.get().get(groupName) == null) {
            return null;
        } else {
            return GROUP_DATA_SCOPE_HOLDER.get().get(groupName).get(customerNumber);
        }
    }

    public static void clear() {
        if (GROUP_DATA_SCOPE_HOLDER.get() != null) {
            GROUP_DATA_SCOPE_HOLDER.remove();
        }
    }
}
