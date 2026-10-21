package com.bidr.authorization.vo.login;

import lombok.Data;

/**
 * Title: TenantMyVO
 * Description: 「我的租户」清单条目（框架内置租户切换器数据面）。
 * <p>isAdmin 现查关系行（M-11 时效纪律：不消费登录烤进 token 的租户 claims 做展示判定，
 * 成员/管理员变更即时可见）；平台档/系统管理级的全量腿里，无成员关系的行 isAdmin=false；
 * current＝当前 token 的 TENANT_ID（前端高亮用）。</p>
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Data
public class TenantMyVO {

    /** 租户 id */
    private Long tenantId;

    /** 租户名称 */
    private String name;

    /** 租户码 */
    private String code;

    /** 是否本租户管理员（现查关系行 is_admin；无关系行恒 false） */
    private Boolean isAdmin;

    /** 是否当前租户（token TENANT_ID 命中） */
    private Boolean current;
}
