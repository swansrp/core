package com.bidr.authorization.identity;

import java.util.Map;

/**
 * Title: BridgeUserPrincipal
 * Description: 桥侧身份主体——桥验票通过后回给内核的外部身份。
 * 语义来自 bidr InkHubLoginService.login() 验票后可得的账号要素与 plsintec
 * FeishuSsoService.ssoLogin(String code) 换回的用户信息；外部 ID 以桥侧账号体系为准
 * （MDM account / 飞书 userId），产品登录态（JWT/会话）由内核凭本主体自铸（M-1 裁定：
 * 不复用 core 的 LoginRes，桥不签产品 JWT）。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class BridgeUserPrincipal {

    /** 桥侧账号体系唯一 ID（MDM 账号 ID / 飞书 userId 等），内核做账号映射的主键 */
    public String externalId;

    /** 登录名（与产品账号对齐的账号名） */
    public String username;

    /** 显示名（姓名 / 昵称） */
    public String displayName;

    /** 部门路径（如「公司/一级部门/二级部门」，展示用；结构化部门见 OrgSyncResult.UserRow） */
    public String deptPath;

    /** 头像地址（相对路径或完整 URL；允许 null——IdP 未返回或未设置时） */
    public String avatar;

    /**
     * 桥侧原始 claims（IdP 返回的原样键值，供内核扩展消费）。
     * 标准键名约定：{@code avatar} / {@code email} / {@code mobile} 三个键 T1.1 内核可依赖
     * （键存在与否仍由 IdP 决定）；其余键无结构承诺。
     */
    public Map<String, Object> rawClaims;
}
