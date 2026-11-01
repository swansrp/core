package com.bidr.authorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Title: OrgSyncProperties
 * Description: identity.org-sync.{enabled,cron}——组织定时同步桩（T1.0 棒1 只建属性面，
 * 棒4 消费接线调度；本棒无任何读取方，先行把配置键位定桩，防棒4 再改键名破坏部署档 yml）。
 * <p>enabled 默认 false＝不装配（框架模块默认不装配纪律，同 AuthzClientProperties 先例）；
 * cron 为 Spring cron 表达式（棒4 消费时空白须回退自身缺省值）。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity.org-sync")
public class OrgSyncProperties {

    /** 组织定时同步开关；false＝不装配调度（棒4 消费） */
    private boolean enabled = false;

    /** 调度 cron（Spring 6 段式）；棒4 消费，空白时由消费方回退缺省 */
    private String cron = "";
}
