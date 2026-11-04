package com.bidr.authorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Title: TenantProperties
 * Description: identity.tenant.{code,name,auto-create}——空库引导（T1.0 棒1）租户种子三态开关＋
 * FixedTenantResolver 的租户归属正源。前缀零客户名：值进各部署档 yml（dev 档客户名合法），
 * 代码与配置键不出现任何客户标识。
 *
 * <p>三态语义（TenantSeeder 消费）：</p>
 * <ul>
 *   <li>code 空白 ＝ 非租户形态：种子跳过、{@code FixedTenantResolver} 回 {@code Optional.empty()}；</li>
 *   <li>code 有值 + auto-create=true ＝ ac_tenant 无该 code 则自动建行；</li>
 *   <li>code 有值 + auto-create=false ＝ 必须已存在，否则启动失败（配置了租户但库里没有＝装配错误）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity.tenant")
public class TenantProperties {

    /** 租户业务编码（ac_tenant.code 口径）；空白＝非租户形态/未配置 */
    private String code = "";

    /** 租户名称（auto-create=true 自动建行时落 ac_tenant.name；空白时种子回退用 code 兜底） */
    private String name = "";

    /** 目标租户不存在时是否自动建行；false＝只校验必须已存在（缺行即启动失败） */
    private boolean autoCreate = false;
}
