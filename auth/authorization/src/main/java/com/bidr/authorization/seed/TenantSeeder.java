package com.bidr.authorization.seed;

import com.bidr.authorization.config.TenantProperties;
import com.bidr.authorization.constants.AuthConstants;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
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
 * Title: TenantSeeder
 * Description: 租户目录种子器（T1.0 空库引导·棒1）——「配置即种子」：identity.tenant.code
 * 有值就把该租户保证在 ac_tenant 里，与 MenuSeeder 并列的启动期 ApplicationRunner。
 *
 * <p>三态行为（幂等，重启零重复）：</p>
 * <ul>
 *   <li>code 有值 + auto-create=true：ac_tenant 无该 code（valid='1'）则建行（name 取配置，
 *       配置空白时回退用 code 兜底满足 NOT NULL）；有则校验存在即过；</li>
 *   <li>code 有值 + auto-create=false：必须已存在，否则抛异常启动失败——
 *       配置点名了租户而库里没有＝装配错误，半截启动比不启动更糟（同 MenuSeeder R-D D-4 硬拒口径）；</li>
 *   <li>code 未配置（空白）：skip 并 log.info（非租户形态语义，同 FixedTenantResolver 的 empty 口径）。</li>
 * </ul>
 *
 * <p>时序：排在 MenuSeeder 之后（显式 @Order，MenuSeeder 同批加了锚点值，见该类注释），
 * PartnerSeeder 更后——种子链 TenantSeeder → PartnerSeeder（AdminUserSeeder 已于 2026-11-02 退役：
 * 超管由 setInitData 建表即种，且不再有启动过程改写既有口令）。</p>
 *
 * <p>⚠ 本类 DB 写路径不在离线单测覆盖（离线腿 mock AcTenantService 断言三态分支；
 * 建行真值交真机腿：起服务后核 ac_tenant，重启第二次应零新增）。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 3)
@RequiredArgsConstructor
public class TenantSeeder implements ApplicationRunner {

    private final TenantProperties tenantProperties;
    private final AcTenantService acTenantService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) throws Exception {
        String code = tenantProperties.getCode();
        if (code == null || code.trim().isEmpty()) {
            log.info("[tenant-seed] identity.tenant.code 未配置——跳过租户种子（非租户形态）");
            return;
        }
        code = code.trim();
        AcTenant existing = acTenantService.getByCode(code);
        if (existing != null) {
            log.info("[tenant-seed] 租户已在位 code={} id={}——幂等跳过", code, existing.getId());
            return;
        }
        if (!tenantProperties.isAutoCreate()) {
            throw new IllegalStateException("[tenant-seed] identity.tenant.code=" + code
                    + " 在 ac_tenant 无有效行且 identity.tenant.auto-create=false——拒绝启动"
                    + "（配置指定了租户但库里没有：请先建租户，或显式开 auto-create）");
        }
        String name = tenantProperties.getName() == null ? "" : tenantProperties.getName().trim();
        if (name.isEmpty()) {
            name = code;
        }
        AcTenant tenant = new AcTenant();
        tenant.setCode(code);
        tenant.setName(name);
        tenant.setValid(CommonConst.YES);
        tenant.setCreateBy(AuthConstants.SYSTEM_USER);
        tenant.setUpdateBy(AuthConstants.SYSTEM_USER);
        acTenantService.insert(tenant);
        log.info("[tenant-seed] 自动建租户 code={} name={} id={}", code, name, tenant.getId());
    }
}
