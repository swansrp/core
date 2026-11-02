package com.bidr.authorization.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: AuthzClientProperties
 * Description: client 装配属性（enabled 开关 + server 地址 + fail 策略）。
 * 对照 agent-runtime 先例：框架模块默认不装配，必须显式开启。
 *
 * @author Sharp
 * @since 2026-10-31
 */
@ConfigurationProperties(prefix = "my.authz.client")
public class AuthzClientProperties {

    /** 🔴 默认 false——引 jar 不改变行为；子系统 yml 显式 my.authz.client.enabled=true 才装配 */
    private boolean enabled = false;

    /** server 的 Check API 基址（如 http://hub:8080） */
    private String serverBaseUrl = "";

    /** fail 策略：shell 不可达时 closed=拒绝（默认，安全）／open-to-cache=回落缓存最近值 */
    private FailMode failMode = FailMode.CLOSED;

    /**
     * T2.3b 扩展：机器凭证 appKey（server 侧 ac_partner 注册面发放）——
     * 请求头 {@code x-api-key: Base64(appKey:appSecret)}（与 AuthPartner 验签口径对称）。
     */
    private String appKey = "";

    /** T2.3b 扩展：机器凭证 appSecret */
    private String appSecret = "";

    /**
     * T2.3b 扩展：授权版本拉取节流周期（毫秒）——RemoteAuthzChecker 在本周期内不重复拉
     * /authz/version；周期到点拉一次，版本变化即整缓存失效（bump 后收回上限＝本值）。
     */
    private long versionRefreshMs = 5000L;

    public enum FailMode { CLOSED, OPEN_TO_CACHE }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServerBaseUrl() { return serverBaseUrl; }

    public void setServerBaseUrl(String serverBaseUrl) { this.serverBaseUrl = serverBaseUrl; }

    public FailMode getFailMode() { return failMode; }

    public void setFailMode(FailMode failMode) { this.failMode = failMode; }

    public String getAppKey() { return appKey; }

    public void setAppKey(String appKey) { this.appKey = appKey; }

    public String getAppSecret() { return appSecret; }

    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

    public long getVersionRefreshMs() { return versionRefreshMs; }

    public void setVersionRefreshMs(long versionRefreshMs) { this.versionRefreshMs = versionRefreshMs; }
}
