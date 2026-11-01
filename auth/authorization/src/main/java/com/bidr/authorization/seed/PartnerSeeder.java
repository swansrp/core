package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
import com.bidr.authorization.constants.AuthConstants;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import com.bidr.kernel.constant.CommonConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * Title: PartnerSeeder
 * Description: 三方应用＋SSO 回调白名单种子器（T1.0 空库引导·棒1）——
 * identity.clients 有值就把 ac_partner / ac_sso_redirect 保证到位（M-7：两张表＝client 注册面），
 * 种子链第三棒（TenantSeeder → AdminUserSeeder → 本类）。
 *
 * <p>幂等语义：逐 client 按 app_key 判重 upsert——缺则建（形态照现库行取证：
 * probe-seed-baseline.md，platform=''、status=1）；有则只同步 app_secret（配置是密钥正源），
 * <b>不动</b> platform/status/remark 等字段——现库行可能带人工语义（备注/平台归属），种子不得回写覆盖。
 * 回调白名单逐条按 (partner_id, redirect_uri, valid='1') 幂等补行，已登记即跳过。</p>
 *
 * <p>identity.clients 空列表 → 整体 skip 并 log.info（非对接形态语义）。</p>
 *
 * <p>⚠ 本类 DB 写路径不在离线单测覆盖（离线腿 mock 仓库层断言空/非空两态分支）。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)
@RequiredArgsConstructor
public class PartnerSeeder implements ApplicationRunner {

    /** 审计备注：方便日后按 remark 反查播种来源（同 MenuSeeder.REMARK 形态；列宽 50 内） */
    static final String REMARK = "partner-seed 播种";
    /** 白名单行备注（列宽 200 内） */
    static final String REDIRECT_REMARK = "identity.clients 播种";

    /**
     * platform 落库值——照现库行形态取证（probe-seed-baseline.md：现库基线行 platform=''）。
     * 非配置项：clients 条目只声明 app-key/app-secret/redirect-uris，platform 不由部署方赋值。
     */
    static final String PLATFORM_FROM_BASELINE = "";

    /** status 落库值——照现库行形态取证（现库基线行 status=1＝有效；DDL 列默认 0 与现库形态不符，显式赋值） */
    static final int STATUS_FROM_BASELINE = 1;

    private final ClientProperties clientProperties;
    private final AcPartnerService acPartnerService;
    private final AcSsoRedirectService acSsoRedirectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        List<ClientProperties.ClientItem> clients = clientProperties.getClients();
        if (clients == null || clients.isEmpty()) {
            log.info("[partner-seed] identity.clients 未配置——跳过三方应用种子（非对接形态）");
            return;
        }
        for (ClientProperties.ClientItem item : clients) {
            upsertPartner(item);
        }
    }

    /** 单 client：ac_partner 按 appKey 幂等 upsert ＋ ac_sso_redirect 逐条幂等补行。 */
    private void upsertPartner(ClientProperties.ClientItem item) {
        String appKey = item.getAppKey() == null ? "" : item.getAppKey().trim();
        if (appKey.isEmpty()) {
            log.warn("[partner-seed] identity.clients 存在空 app-key 条目——跳过该条");
            return;
        }
        AcPartner partner = acPartnerService.getByAppKey(appKey);
        if (partner == null) {
            partner = new AcPartner();
            partner.setAppKey(appKey);
            partner.setAppSecret(item.getAppSecret());
            partner.setPlatform(PLATFORM_FROM_BASELINE);
            partner.setStatus(STATUS_FROM_BASELINE);
            partner.setRemark(REMARK);
            partner.setCreateBy(AuthConstants.SYSTEM_USER);
            partner.setUpdateBy(AuthConstants.SYSTEM_USER);
            acPartnerService.insert(partner);
            log.info("[partner-seed] 新建三方应用 app_key={} id={}", appKey, partner.getId());
        } else {
            // 幂等 upsert：只同步密钥（配置正源）；platform/status/remark 保留现库人工值不回写
            if (partner.getAppSecret() == null || !partner.getAppSecret().equals(item.getAppSecret())) {
                partner.setAppSecret(item.getAppSecret());
                partner.setUpdateBy(AuthConstants.SYSTEM_USER);
                partner.setUpdateAt(new Date());
                acPartnerService.updateById(partner);
                log.info("[partner-seed] 三方应用已在位 app_key={} id={}——已同步 app_secret", appKey, partner.getId());
            } else {
                log.info("[partner-seed] 三方应用已在位 app_key={} id={}——app_secret 一致，幂等跳过",
                        appKey, partner.getId());
            }
        }
        seedRedirects(partner, item);
    }

    /** 回调白名单：(partner_id, redirect_uri, valid='1') 幂等补行，已登记即跳过。 */
    private void seedRedirects(AcPartner partner, ClientProperties.ClientItem item) {
        List<String> uris = item.getRedirectUris();
        if (uris == null || uris.isEmpty()) {
            return;
        }
        for (String uri : uris) {
            if (uri == null || uri.trim().isEmpty()) {
                continue;
            }
            String redirectUri = uri.trim();
            if (acSsoRedirectService.existsValid(partner.getId(), redirectUri)) {
                continue;
            }
            AcSsoRedirect row = new AcSsoRedirect();
            row.setPartnerId(partner.getId());
            row.setRedirectUri(redirectUri);
            row.setRemark(REDIRECT_REMARK);
            row.setValid(CommonConst.YES);
            row.setCreateBy(AuthConstants.SYSTEM_USER);
            row.setUpdateBy(AuthConstants.SYSTEM_USER);
            acSsoRedirectService.insert(row);
            log.info("[partner-seed] 补登记回调白名单 partner_id={} uri={}", partner.getId(), redirectUri);
        }
    }
}
