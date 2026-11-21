package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
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

import java.util.List;

/**
 * Title: PartnerSeeder
 * Description: 三方应用＋SSO 回调白名单种子器（T1.0 空库引导·棒1）——
 * identity.clients 有值就把 ac_partner / ac_sso_redirect 保证到位（M-7：两张表＝client 注册面），
 * 种子链：TenantSeeder → 本类（原第二棒 AdminUserSeeder 已于 2026-11-02 退役——超管账号与角色
 * 绑定由 AcUserSchema/AcUserRoleSchema 的 setInitData 在建表那一次种下，且不再有启动过程改口令）。
 *
 * <p>幂等语义（🔴 D7 seed 总原则，2026-11-21 用户拍板「部署直观新增；已有行以管理页/SQL 为准」）：
 * 逐 client 按 app_key 判重——<b>缺行建行</b>（形态照现库行取证：probe-seed-baseline.md，
 * platform=''、status=1，app_secret 取 yml 建行初值）；<b>已存在行零触碰</b>——app_secret 在内的
 * 全部字段一律不回写（运行时校验读库值：{@code AuthPartner} 拿 ac_partner.app_secret 比对
 * x-api-key，DB 本就是权威；yml 顶回曾会静默打断管理页轮换的密钥，2026-11-21 随 D7 盘点删除）。
 * 密钥轮换口径＝管理页/SQL 改库（update_by=工号可审计）；yml 值仅建行初值。
 * 回调白名单逐条按 (partner_id, redirect_uri, valid='1') 幂等补行，已登记即跳过（缺行补行，永不回删）。</p>
 *
 * <p>identity.clients 空列表 → 整体 skip 并 log.info（非对接形态语义）。</p>
 *
 * <p>create-partner=false 的条目跳过凭证面（不建 partner/白名单，2026-11-21 本地调试子系统棒1）——
 * 该形态条目只出 ac_subsystem 行，由 AcSubsystemSeeder 直种（partner_id=NULL）。</p>
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

    /** 审计标记（D7：seed 写入与管理页写入按 update_by 可辨） */
    static final String UPDATE_BY = "partner-seed";

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
        // create-partner=false：跳过凭证面（不建 ac_partner/ac_sso_redirect）；子系统行由 AcSubsystemSeeder 直接种
        if (!Boolean.TRUE.equals(item.getCreatePartner())) {
            log.debug("[partner-seed] create-partner=false 跳过凭证面 app_key={}", appKey);
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
            partner.setCreateBy(UPDATE_BY);
            partner.setUpdateBy(UPDATE_BY);
            acPartnerService.insert(partner);
            log.info("[partner-seed] 新建三方应用 app_key={} id={}", appKey, partner.getId());
        } else {
            // 🔴 D7 零触碰：已存在行一律不改（密钥轮换走管理页/SQL；yml 顶回已退役，见类注释）
            log.info("[partner-seed] 三方应用已在位 app_key={} id={}——零触碰幂等跳过",
                    appKey, partner.getId());
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
            row.setCreateBy(UPDATE_BY);
            row.setUpdateBy(UPDATE_BY);
            acSsoRedirectService.insert(row);
            log.info("[partner-seed] 补登记回调白名单 partner_id={} uri={}", partner.getId(), redirectUri);
        }
    }
}
