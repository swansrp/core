package com.bidr.identity.mirror;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Title: IdentityMirrorProperties
 * Description: identity.mirror.sync.*——身份镜像快照同步配置面（T2.3d）。
 * <p>enabled 默认 false＝不装配调度（框架模块默认不装配纪律，同 OrgSyncProperties/
 * AuthzClientProperties 先例）：子系统（epc）引入本模块后默认不拉快照，
 * 显式开 {@code identity.mirror.sync.enabled=true} 才有调度与首跑。</p>
 * <p>base-url＝provider 侧 InkHub 墨枢基地址（snapshot 端点与 IdP 同域）；
 * app-key/app-secret＝provider 侧开放平台机器凭证（partner 注册面发放），请求头
 * {@code x-api-key: Base64(appKey:appSecret)}（与 {@code AuthPartner} 验签口径对称）；
 * cron 为 Spring 6 段式，缺省整点一次（快照全量拉平，频率需求低）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity.mirror.sync")
public class IdentityMirrorProperties {

    /** 快照同步开关；false＝IdentityMirrorSyncJob 不装配（不调度、不首跑） */
    private boolean enabled = false;

    /** provider 基地址（形如 http://inkhub:8080），快照路径固定拼接 /open-api/v1/identity/snapshot */
    private String baseUrl = "";

    /** 机器凭证 appKey（provider 侧开放平台 partner 注册面发放） */
    private String appKey = "";

    /** 机器凭证 appSecret */
    private String appSecret = "";

    /** 调度 cron（Spring 6 段式）；缺省 0 0 * * * ?＝每整点一次 */
    private String cron = "0 0 * * * ?";
}
