package com.bidr.feishu.service.token;

import lombok.Data;

/**
 * Title: FeishuTokenHolder
 * Description: 飞书 token 线程持有者（对标 WxTokenHolder）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Data
public class FeishuTokenHolder {
    private static final ThreadLocal<String> FEISHU_TOKEN_INFO_HOLDER = new ThreadLocal<>();

    private FeishuTokenHolder() {
    }

    public static void set(String token) {
        FEISHU_TOKEN_INFO_HOLDER.set(token);
    }

    public static void remove() {
        if (FEISHU_TOKEN_INFO_HOLDER.get() != null) {
            FEISHU_TOKEN_INFO_HOLDER.remove();
        }
    }

    public static String get() {
        return FEISHU_TOKEN_INFO_HOLDER.get();
    }
}
