package com.bidr.feishu.dto.sso;

import lombok.Data;

/**
 * Title: FeishuSsoUser
 * Description: 飞书 SSO 授权后获取的用户信息
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Data
public class FeishuSsoUser {

    /**
     * 用户 user_access_token
     */
    private String accessToken;

    /**
     * 用户唯一标识
     */
    private String openId;

    /**
     * 用户跨应用唯一标识
     */
    private String unionId;

    /**
     * 姓名
     */
    private String name;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 头像
     */
    private String avatarUrl;
}
