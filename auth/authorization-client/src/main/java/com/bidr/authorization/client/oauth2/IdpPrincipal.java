package com.bidr.authorization.client.oauth2;

import lombok.Data;

/**
 * Title: IdpPrincipal
 * Description: IdP access_token 验签后解析出的身份（claims 面向宿主消费的最小集）。
 *
 * <p>迁移自 epc {@code InkHubJwtVerifier.IdpPrincipal}（字段与语义逐条保持），
 * 供宿主登录链做账号对齐与租户同步。</p>
 *
 * @author coder (棒0, T3.1 客户端件下沉 2026-11-03)
 * @since 2026-11-03
 */
@Data
public class IdpPrincipal {

    /** 登录名（ac_user.user_name）＝跨系统账号对齐唯一稳定键 */
    private String username;

    /** 工号（IdP claim sub＝customerNumber） */
    private String customerNumber;

    /** 主系统 ac_user.user_id（IdP claim uid；系统管理级判定=1 用） */
    private Long uid;

    /** 展示名（IdP claim name） */
    private String name;
}
