package com.bidr.authorization.service.sso;

import com.bidr.authorization.dao.entity.AcUser;

/**
 * M23 应用功能集填充 SPI（core 纯加法）。
 *
 * <p>{@code SsoIntrospectTicketService} 在返回身份/租户之后调用本 SPI，由宿主侧
 * 按 {@code app_code + tenant_code + user} 查出该用户的有效组/功能码填充
 * {@code app.groups} / {@code app.functions}。无实现＝返回空集（存量零位移）。</p>
 *
 * <p>🔴 core 不依赖 {@code inkhub_app_group} 等业务表；业务判定永远在宿主侧
 * （与 {@code SsoTicketAuthorizer} / {@code SsoUserHandler} 同款纪律）。</p>
 *
 * @author sharp
 * @since 2026-10-27
 */
public interface SsoAppGrantProvider {

    /**
     * 解析用户在某应用/租户下的有效组与功能码。
     *
     * @param appCode    应用编码（inkhub_subsystem.app_code）
     * @param tenantCode 租户 code（可空：空则宿主自行决定口径）
     * @param user       已验票的 InkHub 用户
     * @return 组码/功能码；无授权时返回空集对象，不返回 null（null 按空集处理）
     */
    SsoAppGrant resolve(String appCode, String tenantCode, AcUser user);
}