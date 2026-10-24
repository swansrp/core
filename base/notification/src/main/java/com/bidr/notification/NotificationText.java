package com.bidr.notification;

import com.bidr.kernel.utils.FuncUtil;

/**
 * 消息文本清洗（站内信是补充通道：一切清洗失败只丢字段不丢消息）。
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
public final class NotificationText {

    private NotificationText() {
    }

    /** 人话原因：取 message、去堆栈行（以 "at " 开头），按 {@code max} 截断加省略号 */
    public static String humanReason(Throwable error, int max) {
        if (error == null) {
            return null;
        }
        return humanReason(error.getMessage() != null ? error.getMessage()
                : error.getClass().getSimpleName(), max);
    }

    public static String humanReason(String raw, int max) {
        if (FuncUtil.isEmpty(raw)) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("at ")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append(t);
            if (sb.length() >= max) {
                break;
            }
        }
        return truncate(sb.toString(), max);
    }

    /** 定长截断：超长加省略号（列宽内自洽） */
    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
