package com.bidr.authorization.seed;

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

/**
 * Title: AcSubsystemSeeder
 * Description: 子系统注册表首启兜底种子器（主系统开放口·方案B 棒2）——
 * 仅当 ac_partner 存在 app_key='epc-ai' 行（PartnerSeeder 按 identity.clients 保证到位）
 * 且 ac_subsystem 无 app_code='epc-ai' 行时插入一条绑定行，其余情况一律幂等跳过。
 *
 * <p>时序：@Order 取 LOWEST_PRECEDENCE＝排 PartnerSeeder（LOWEST_PRECEDENCE - 1）之后，
 * 保证「先有 partner 再挂子系统」的引导顺序。</p>
 *
 * <p>⚠ 门户清单（ac_menu 按 app_code 挂页）在 seed 前为空是已接受后果（M-14：menu-seed 只声明
 * title/path/component/icon，播种不涉及 app_code）——本行＝首启兜底，菜单归属后续由应用管理面维护。</p>
 *
 * <p>⚠ 本类 DB 写路径不在离线单测覆盖（离线腿 mock 仓库层断言三态分支，见 AcSubsystemSeederTest；
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

    /** 'epc-ai'＝identity.clients 既有 client 的 app_key 数据值（与 PartnerSeeder 播的 partner 对齐） */
    static final String EPC_APP_CODE = "epc-ai";
    /** 兜底显示名（现库无正源，首启展示兜底） */
    static final String EPC_NAME = "EPC 进度子系统";

    private final AcPartnerService acPartnerService;
    private final AcSubsystemService acSubsystemService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        AcPartner partner = acPartnerService.getByAppKey(EPC_APP_CODE);
        if (partner == null) {
            log.info("[subsystem-seed] ac_partner 无 app_key={} 行——跳过子系统种子（partner 由 PartnerSeeder 按配置保证）",
                    EPC_APP_CODE);
            return;
        }
        if (acSubsystemService.selectById(EPC_APP_CODE) != null) {
            log.info("[subsystem-seed] 子系统已在位 app_code={}——幂等跳过", EPC_APP_CODE);
            return;
        }
        AcSubsystem subsystem = new AcSubsystem();
        subsystem.setAppCode(EPC_APP_CODE);
        subsystem.setName(EPC_NAME);
        subsystem.setPartnerId(partner.getId());
        subsystem.setStatus(CommonConst.YES);
        subsystem.setVisibleToAllTenant(CommonConst.YES);
        subsystem.setCreateBy(AuthConstants.SYSTEM_USER);
        subsystem.setUpdateBy(AuthConstants.SYSTEM_USER);
        acSubsystemService.insert(subsystem);
        log.info("[subsystem-seed] 首启兜底建子系统 app_code={} partner_id={}", EPC_APP_CODE, partner.getId());
    }
}
