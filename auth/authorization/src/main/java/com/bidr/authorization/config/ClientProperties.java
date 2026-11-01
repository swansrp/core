package com.bidr.authorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ClientProperties
 * Description: identity.clients[n].{app-key,app-secret,redirect-uris}——三方应用（ac_partner）
 * ＋SSO 回调白名单（ac_sso_redirect）的种子正源（PartnerSeeder 消费）。
 * <p>空列表 ＝ PartnerSeeder 整体跳过（非对接形态）。yml 绑定形态：</p>
 * <pre>
 * identity:
 *   clients:
 *     - app-key: xxx
 *       app-secret: yyy
 *       redirect-uris:
 *         - https://a.example.com/cb
 *         - https://b.example.com/cb
 * </pre>
 * <p>前缀零客户名：客户标识只出现在部署档 yml 的值里（dev 档合法），代码与键名不出现。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity.clients")
public class ClientProperties {

    /** 三方应用清单；空列表＝不种 ac_partner/ac_sso_redirect */
    private List<ClientItem> clients = new ArrayList<>();

    /**
     * 单个三方应用条目（app-key 幂等键）。
     * <p>🔴 appSecret 是对接密钥正源（ac_partner.app_secret），只在 yml 值面出现，不进代码。</p>
     */
    @Getter
    @Setter
    public static class ClientItem {

        /** 应用的唯一标识 key（ac_partner.app_key，幂等判重键） */
        private String appKey = "";

        /** 应用的密钥（ac_partner.app_secret） */
        private String appSecret = "";

        /** SSO 回调白名单（ac_sso_redirect.redirect_uri 全串等值登记，逐条幂等补行） */
        private List<String> redirectUris = new ArrayList<>();
    }
}
