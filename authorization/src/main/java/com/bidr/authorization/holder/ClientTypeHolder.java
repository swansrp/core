package com.bidr.authorization.holder;

/**
 * Title: ClientTypeHolder
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/27 13:59
 */
public class ClientTypeHolder {

    private static final ThreadLocal<String> CLIENT_TYPE_HOLDER = new ThreadLocal<>();

    private ClientTypeHolder() {
    }

    public static void set(String s) {
        CLIENT_TYPE_HOLDER.set(s);
    }

    public static String get() {
        return CLIENT_TYPE_HOLDER.get();
    }

    public static void remove() {
        if (CLIENT_TYPE_HOLDER.get() != null) {
            CLIENT_TYPE_HOLDER.remove();
        }
    }
}
