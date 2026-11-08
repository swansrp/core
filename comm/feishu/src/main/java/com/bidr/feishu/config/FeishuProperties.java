package com.bidr.feishu.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Title: FeishuProperties
 * Description: 飞书开放平台配置（自建应用凭证与 SSO 配置）
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Data
@Component
@ConfigurationProperties(prefix = "feishu")
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
public class FeishuProperties {

    /**
     * 飞书开放平台域名
     */
    private String host = "https://open.feishu.cn";

    /**
     * 自建应用 App ID
     */
    private String appId;

    /**
     * 自建应用 App Secret
     */
    private String appSecret;

    /**
     * 后端 SSO 回调接口地址（飞书授权后重定向到此）
     */
    private String redirectUri;

    /**
     * 系统登录成功后跳转的前端地址（携带 token 参数）
     */
    private String ssoFrontUrl;
}
