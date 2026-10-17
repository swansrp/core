package com.bidr.authorization.mybatis.permission;

/**
 * Title: DataPermissionHolder
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/07 16:52
 */
public class DataPermissionHolder {

    private static final ThreadLocal<Class<? extends DataPermissionInf>[]> DATA_PERMISSION_HOLDER = new ThreadLocal<>();

    private DataPermissionHolder() {
    }

    public static void set(Class<? extends DataPermissionInf>[] s) {
        DATA_PERMISSION_HOLDER.set(s);
    }

    public static Class<? extends DataPermissionInf>[] get() {
        return DATA_PERMISSION_HOLDER.get();
    }

    public static void clear() {
        if (DATA_PERMISSION_HOLDER.get() != null) {
            DATA_PERMISSION_HOLDER.remove();
        }
    }
}
