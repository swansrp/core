package com.bidr.authorization.service.sso;

import lombok.Data;

import java.util.List;

/**
 * M23 introspect-ticket 响应 DTO（core 纯加法）。
 *
 * @author sharp
 * @since 2026-10-26
 */
@Data
public class SsoIntrospectTicketRes {

    /** 错误信息（null=成功） */
    private String error;

    private String customerNumber;

    private Long userId;

    private String name;

    private List<TenantItem> tenants;

    private AppInfo app;

    @Data
    public static class TenantItem {
        private Long id;
        private String code;
        private String name;
    }

    @Data
    public static class AppInfo {
        private String appCode;
        private String tenantCode;
        private List<String> groups;
        private List<String> functions;
    }
}
