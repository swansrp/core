package com.bidr.authorization.client;

/**
 * Title: AuthzClientProperties
 * Description: client 装配属性（enabled 开关 + server 地址 + fail 策略）。
 * 对照 agent-runtime 先例：框架模块默认不装配，必须显式开启。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class AuthzClientProperties {

    /** 🔴 默认 false——引 jar 不改变行为；子系统 yml 显式 my.authz.client.enabled=true 才装配 */
    private boolean enabled = false;

    /** server 的 Check API 基址（如 http://hub:8080） */
    private String serverBaseUrl = "";

    /** fail 策略：shell 不可达时 closed=拒绝（默认，安全）／open-to-cache=回落缓存最近值 */
    private FailMode failMode = FailMode.CLOSED;

    public enum FailMode { CLOSED, OPEN_TO_CACHE }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getServerBaseUrl() { return serverBaseUrl; }

    public void setServerBaseUrl(String serverBaseUrl) { this.serverBaseUrl = serverBaseUrl; }

    public FailMode getFailMode() { return failMode; }

    public void setFailMode(FailMode failMode) { this.failMode = failMode; }
}
