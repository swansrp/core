package com.bidr.authorization.service.sso;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcUser;

/**
 * M23 SSO 出票准入 SPI（core 纯加法，决策稿 §四.4）。
 *
 * <p>宿主侧提供 bean 实现本接口，{@code SsoTicketService.issue} 在 partner/白名单闸之后调用。
 * **无实现＝放行**（存量形态零位移——没有接入应用管理的部署不感知此 SPI）。</p>
 *
 * <p>🔴 core 不感知 `inkhub_subsystem`/`inkhub_app_group`；业务判定永远在宿主侧。
 * 实现方（如 InkHub）可在本方法里查 {@code visible_to_all_tenant} ∨ {@code inkhub_app_group_member}。</p>
 *
 * <p>🔴 全应用至多一个生效实现（同 {@code SsoUserHandler} 口径）；注册多个实现时须其一标 {@code @Primary}
 * 或保证仅一个生效，否则出票口按"准入 SPI 实现冲突"业务拒绝。</p>
 *
 * @author sharp
 * @since 2026-10-26
 */
public interface SsoTicketAuthorizer {

    /**
     * 判断当前用户是否可向指定 partner（子系统）签发 SSO 票。
     *
     * @param partner    出票目标（ac_partner 行，含 app_key/platform 等）
     * @param callerUser 发起出票的 InkHub 登录用户（ac_user 行）
     * @return true＝放行；false＝拒绝出票（业务拒绝文案由调用方统一）
     */
    boolean canIssue(AcPartner partner, AcUser callerUser);
}
