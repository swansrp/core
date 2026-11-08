package com.bidr.feishu.util;

/**
 * Title: FeishuMobileUtil
 * Description: 飞书手机号规范化：飞书返回国内手机号带 +86 国际区号前缀（如 +8613821823226），
 * 系统内登录/匹配使用 11 位裸号，入库前统一去除 +86 前缀
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/09/05 00:00
 */
public class FeishuMobileUtil {

    private static final String CN_PREFIX = "+86";

    private FeishuMobileUtil() {
    }

    /**
     * 去除飞书手机号的 +86 前缀，返回 11 位裸号；空值与其他国家格式原样返回
     *
     * @param mobile 飞书返回的手机号
     * @return 规范化后的手机号
     */
    public static String normalize(String mobile) {
        if (mobile != null && mobile.startsWith(CN_PREFIX)) {
            return mobile.substring(CN_PREFIX.length());
        }
        return mobile;
    }
}
