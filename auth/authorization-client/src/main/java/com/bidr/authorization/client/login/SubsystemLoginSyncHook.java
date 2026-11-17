package com.bidr.authorization.client.login;

import com.bidr.authorization.client.oauth2.IdpPrincipal;

/**
 * Title: SubsystemLoginSyncHook
 * Description: 子系统登录后同步 SPI（宿主实现，best-effort）——IdP 票验签成功、本地会话签发之前的
 * 唯一业务回调点。
 *
 * <p>core 只负责「验票 → 发本地会话」通用链；宿主在拿到 {@link IdpPrincipal}（登录名/工号/uid/展示名）
 * 后要做的账号/租户/项目镜像同步属于业务面，由本 SPI 注入：</p>
 * <ul>
 *   <li>调用时机：{@code IdpTokenVerifier.verify} 返回非 null 之后、{@code SubsystemSessionService.login}
 *       之前（此时票面身份已可信，本地会话尚未签发）；</li>
 *   <li>调用语义：<b>best-effort</b>——实现抛任何运行时异常都只记 WARN，不阻断登录（存量账号不受影响，
 *       新账号由登录闸在会话签发前明确拒绝）；</li>
 *   <li>装配方式：宿主注册为 bean 即生效；未装配时登录链零影响（{@code ObjectProvider} 可选注入）。</li>
 * </ul>
 *
 * @author coder (棒2, 子系统登录上提 2026-11-17)
 * @since 2026-11-17
 */
public interface SubsystemLoginSyncHook {

    /**
     * IdP 票验签成功后的同步回调（best-effort：异常不得阻断登录）。
     *
     * @param principal      验签通过的 IdP 身份（username 必非空；customerNumber/uid/name 可能为 null）
     * @param idpAccessToken 原始 IdP 票串（宿主可同票走 Bearer 调用主系统开放口，如拉租户清单）
     */
    void afterIdpVerified(IdpPrincipal principal, String idpAccessToken);
}