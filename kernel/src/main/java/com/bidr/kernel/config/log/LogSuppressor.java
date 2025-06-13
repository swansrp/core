package com.bidr.kernel.config.log;

/**
 * Title: LogSuppressor
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/13 11:52
 */

public class LogSuppressor {
    private static final ThreadLocal<Boolean> SUPPRESS_LOGS = ThreadLocal.withInitial(() -> false);

    public static void suppressLogs(boolean suppress) {
        SUPPRESS_LOGS.set(suppress);
    }

    public static boolean isLoggingSuppressed() {
        return SUPPRESS_LOGS.get();
    }
}
