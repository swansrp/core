package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
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

/**
 * Title: AcSubsystemSeeder
 * Description: 子系统种子器（主系统开放口·方案B 棒2 起步，2026-11-11 用户拍板方案 A 扩为入口种子；
 * 2026-11-21 本地调试子系统 D7 拍板翻转为 create-if-missing）——
 * 遍历 identity.clients，对配置了 entry-url 的条目（app_code 幂等键）：
 * <ol>
 *   <li>缺行建行：ac_subsystem 无该行 → 插一条完整行（name/entry_type/entry_url/embed_chrome/
 *       partner_id/status/visible 全套声明初值），name 缺省用 app_code 兜底，
 *       create_by/update_by=subsystem-seed（来源审计）；</li>
 *   <li>已存在行零触碰：不回写不校对任何列——管理页/SQL 是已存在行的权威，重启不还原；
 *       重大变化＝删行＋改 yml＋重启（🔴 2026-11-11 版「入口 upsert 按配置回写顶替管理页修改」
 *       语义已于 2026-11-21 退役）。</li>
 * </ol>
 *
 * <p>前置：ac_partner 行由 PartnerSeeder（@Order LOWEST_PRECEDENCE - 1，先于本类）按 identity.clients
 * 保证到位；partner 缺席＋create-partner=true（缺省）＝该条目跳过并记日志，不阻断其他条目；
 * partner 缺席＋create-partner=false ⇒ 继续建行（partner_id 落 NULL＝未绑定，绑定关系由管理页/SQL 后补）。</p>
 *
 * <p>⚠ 本类 DB 写路径不在离线单测覆盖（离线腿 mock 仓库层断言分支，见 AcSubsystemSeederTest；
 * 建行真值交真机腿：起服务后核 ac_subsystem，重启第二次应零新增）。</p>
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AcSubsystemSeeder implements ApplicationRunner {

    /** 来源审计：种子建行的 create_by/update_by（方便日后反查播种来源；列宽 50 内） */
    static final String UPDATE_BY = "subsystem-seed";

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

    /** 单条目种子：create-if-missing——缺行建行（全套声明初值），已存在行零触碰（管理页/SQL 是权威，重启不还原）。 */
    private void seedOne(ClientProperties.ClientItem item) {
        String appCode = item.getAppKey();
        AcPartner partner = acPartnerService.getByAppKey(appCode);
        if (partner == null && Boolean.TRUE.equals(item.getCreatePartner())) {
            log.info("[subsystem-seed] ac_partner 无 app_key={} 行——跳过子系统种子（partner 由 PartnerSeeder 按配置保证）",
                    appCode);
            return;
        }

        AcSubsystem row = acSubsystemService.selectById(appCode);
        if (row != null) {
            // 已存在行零触碰：管理页/SQL 是已存在行的权威，重启不还原；重大变化＝删行+改yml+重启
            return;
        }

        AcSubsystem subsystem = new AcSubsystem();
        subsystem.setAppCode(appCode);
        // 显示名兜底：配置 name 优先，缺省用 app_code（现网 epc-ai 行由 yml 带 name 保持既有显示名）
        subsystem.setName(StringUtils.hasText(item.getName()) ? item.getName() : appCode);
        // 空串归一成 null：properties 默认值是 ""，不归一会让 DB 列落 '' 而非保持 NULL 语义
        subsystem.setEntryType(StringUtils.hasText(item.getEntryType()) ? item.getEntryType() : null);
        subsystem.setEntryUrl(item.getEntryUrl());
        // 嵌入形态初值（空串归一 null 同 entryType；只在建行时落一次，已存在行零触碰）
        subsystem.setEmbedChrome(StringUtils.hasText(item.getEmbedChrome()) ? item.getEmbedChrome() : null);
        // partner 缺席（create-partner=false）时落 NULL＝未绑定；绑定关系由管理页/SQL 后补
        subsystem.setPartnerId(partner == null ? null : partner.getId());
        subsystem.setStatus(CommonConst.YES);
        subsystem.setVisibleToAllTenant(CommonConst.YES);
        subsystem.setCreateBy(UPDATE_BY);
        subsystem.setUpdateBy(UPDATE_BY);
        acSubsystemService.insert(subsystem);
        log.info("[subsystem-seed] 首启建子系统 app_code={} partner_id={} entry_url={} embed_chrome={}",
                appCode, subsystem.getPartnerId(), item.getEntryUrl(), subsystem.getEmbedChrome());
    }
}
