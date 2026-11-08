package com.bidr.feishu.constant;

/**
 * Title: FeishuErrorCode
 * Description: 飞书开放平台通用错误码
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
public final class FeishuErrorCode {

    /**
     * 成功
     */
    public static final int SUCCESS = 0;

    /**
     * access token 无效
     */
    public static final int ACCESS_TOKEN_INVALID = 99991663;

    /**
     * access token 过期
     */
    public static final int ACCESS_TOKEN_EXPIRED = 99991661;

    private FeishuErrorCode() {
    }
}
