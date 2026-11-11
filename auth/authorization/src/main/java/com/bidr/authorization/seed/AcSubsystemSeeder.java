package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
import com.bidr.authorization.constants.AuthConstants;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.kernel.constant.CommonConst;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Title: AcSubsystemSeeder
 * Description: 子系统种子器（主系统开放口·方案B 棒2 起步，2026-11-11 用户拍板方案 A 扩为入口种子）——
 * 遍历 identity.clients，对配置了 entry-url 的条目做两件事（app_code 幂等键）：
 * <ol>
 *   <li>建行：ac_subsystem 无该行 → 插一条完整行（name/entry_type/entry_url/partner_id/status/visible），
 *       name 缺省用 app_code 兜底；</li>
 *   <li>入口 upsert：已有行且 entry_type/entry_url 与配置不同 → 只回写这两列（+update_by），
 *       相同即零写（幂等）。🔴 配置是入口正源：管理页手改的入口会在下次重启被配置顶回
 *       （与 PartnerSeeder 回写 app_secret 同哲学）；entry-url 不配＝完全不碰该 app 的入口字段。</li>
 * </ol>
 *
 * <p>前置：ac_partner 行由 PartnerSeeder（@Order LOWEST_PRECEDENCE - 1，先于本类）按 identity.clients
 * 保证到位；某条目 partner 缺席（配置与 PartnerSeeder 不同源时）＝该条目跳过并记日志，不阻断其他条目。</p>
 *
 * <p>⚠ 本类 DB 写路径不在离线单测覆盖（离线腿 mock 仓库层断言分支，见 AcSubsystemSeederTest；
 * 建行/回写真值交真机腿：起服务后核 ac_subsystem，重启第二次应零新增）。</p>
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AcSubsystemSeeder implements ApplicationRunner {

    private final AcPartnerService acPartnerService;
    private final AcSubsystemService acSubsystemService;
    private final ClientProperties clientProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        for (ClientProperties.ClientItem item : clientProperties.getClients()) {
            if (!StringUtils.hasText(item.getEntryUrl())) {
                continue;
            }
            seedOne(item);
        }
    }

    /** 单条目种子：partner 在位检查 → 缺行建行 / 有行入口 upsert（逐条隔离，失败不阻断其他条目由调用方事务兜底）。 */
    private void seedOne(ClientProperties.ClientItem item) {
        String appCode = item.getAppKey();
        AcPartner partner = acPartnerService.getByAppKey(appCode);
        if (partner == null) {
            log.info("[subsystem-seed] ac_partner 无 app_key={} 行——跳过子系统种子（partner 由 PartnerSeeder 按配置保证）",
                    appCode);
            return;
        }

        AcSubsystem row = acSubsystemService.selectById(appCode);
        if (row == null) {
            AcSubsystem subsystem = new AcSubsystem();
            subsystem.setAppCode(appCode);
            // 显示名兜底：配置 name 优先，缺省用 app_code（现网 epc-ai 行由 yml 带 name 保持既有显示名）
            subsystem.setName(StringUtils.hasText(item.getName()) ? item.getName() : appCode);
            // 空串归一成 null：properties 默认值是 ""，不归一会让 DB 列落 '' 而非保持 NULL 语义
            subsystem.setEntryType(StringUtils.hasText(item.getEntryType()) ? item.getEntryType() : null);
            subsystem.setEntryUrl(item.getEntryUrl());
            subsystem.setPartnerId(partner.getId());
            subsystem.setStatus(CommonConst.YES);
            subsystem.setVisibleToAllTenant(CommonConst.YES);
            subsystem.setCreateBy(AuthConstants.SYSTEM_USER);
            subsystem.setUpdateBy(AuthConstants.SYSTEM_USER);
            acSubsystemService.insert(subsystem);
            log.info("[subsystem-seed] 首启建子系统 app_code={} partner_id={} entry_url={}",
                    appCode, partner.getId(), item.getEntryUrl());
            return;
        }

        // 入口 upsert：只回写与配置不同的两列（null/同值不写，管理页其他列一律不碰）
        boolean changed = false;
        if (StringUtils.hasText(item.getEntryType()) && !Objects.equals(item.getEntryType(), row.getEntryType())) {
            row.setEntryType(item.getEntryType());
            changed = true;
        }
        if (!Objects.equals(item.getEntryUrl(), row.getEntryUrl())) {
            row.setEntryUrl(item.getEntryUrl());
            changed = true;
        }
        if (changed) {
            row.setUpdateBy(AuthConstants.SYSTEM_USER);
            acSubsystemService.updateById(row);
            log.info("[subsystem-seed] 入口按配置回写 app_code={} entry_type={} entry_url={}",
                    appCode, row.getEntryType(), row.getEntryUrl());
        }
    }
}
