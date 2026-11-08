package com.bidr.feishu.constant;

/**
 * Title: FeishuUrl
 * Description: 飞书开放平台 API 路径常量
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
public final class FeishuUrl {

    /**
     * 飞书开放平台默认域名
     */
    public static final String DEFAULT_HOST = "https://open.feishu.cn";

    /**
     * 获取自建应用 tenant_access_token
     */
    public static final String TENANT_ACCESS_TOKEN = "/open-apis/auth/v3/tenant_access_token/internal";

    /**
     * 获取用户 user_access_token（授权码模式）
     */
    public static final String USER_ACCESS_TOKEN = "/open-apis/authen/v1/oidc/access_token";

    /**
     * 网页授权登录页地址
     */
    public static final String AUTHORIZE_PAGE = "/open-apis/authen/v1/authorize";

    /**
     * 按部门获取用户列表
     */
    public static final String USERS_FIND_BY_DEPARTMENT = "/open-apis/contact/v3/users/find_by_department";

    /**
     * 获取部门列表
     */
    public static final String DEPARTMENTS = "/open-apis/contact/v3/departments";

    /**
     * 发送消息
     */
    public static final String SEND_MESSAGE = "/open-apis/im/v1/messages";

    private FeishuUrl() {
    }
}
