package com.bidr.authorization.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: ClientProperties
 * Description: identity.clients[n].{app-key,app-secret,redirect-uris,name,entry-type,entry-url}——
 * 三方应用（ac_partner）＋SSO 回调白名单（ac_sso_redirect）的种子正源（PartnerSeeder 消费），
 * 以及子系统种子（AcSubsystemSeeder 消费，2026-11-11 用户拍板方案 A：入口进配置；
 * 2026-11-21 起为 create-if-missing——缺行建行、已存在行零触碰）。
 * <p>空列表 ＝ PartnerSeeder 整体跳过（非对接形态）。yml 绑定形态：</p>
 * <pre>
 * identity:
 *   clients:
 *     - app-key: xxx
 *       app-secret: yyy
 *       name: 某子系统            # 可选：ac_subsystem.name 兜底（缺省用 app-code）
 *       entry-type: iframe        # 可选：ac_subsystem.entry_type（iframe/新窗口/同页）
 *       entry-url: https://host/xxx/   # 可选：ac_subsystem.entry_url；🔴 配了才参与子系统种子（create-if-missing）
 *       create-partner: false          # 可选：缺省 true；false=只种 ac_subsystem 行，不建 ac_partner/ac_sso_redirect 凭证三件套
 *       embed-chrome: nav              # 可选：嵌入形态初值（缺省/none=embedded=1 壳全隐藏；nav=保留 topnav/左栏去身份条）；仅建行落值
 *       redirect-uris:
 *         - https://a.example.com/cb
 *         - https://b.example.com/cb
 * </pre>
 * <p>前缀零客户名：客户标识只出现在部署档 yml 的值里（dev 档合法），代码与键名不出现。
 * name/entry-type/entry-url 是环境相关值（各部署档域名不同），可选不填＝不参与子系统种子。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "identity")
public class ClientProperties {

    /** 三方应用清单；空列表＝不种 ac_partner/ac_sso_redirect */
    private List<ClientItem> clients = new ArrayList<>();

    /**
     * 单个三方应用条目（app-key 幂等键）。
     * <p>🔴 appSecret 是对接密钥正源（ac_partner.app_secret），只在 yml 值面出现，不进代码。</p>
     * <p>🔴 entryUrl 是子系统种子触发键（ac_subsystem.entry_url）——非空才建行（create-if-missing）；
     * 已存在行零触碰（管理页/SQL 是权威，重启不还原；与 appSecret「配置覆盖库值」哲学不同）；不配＝不参与子系统种子。</p>
     */
    @Getter
    @Setter
    public static class ClientItem {

        /** 应用的唯一标识 key（ac_partner.app_key / ac_subsystem.app_code，幂等判重键） */
        private String appKey = "";

        /** 应用的密钥（ac_partner.app_secret） */
        private String appSecret = "";

        /** SSO 回调白名单（ac_sso_redirect.redirect_uri 全串等值登记，逐条幂等补行） */
        private List<String> redirectUris = new ArrayList<>();

        /** 子系统显示名兜底（ac_subsystem.name；空＝建行时用 app-code 兜底，已存在行不回写） */
        private String name = "";

        /** 子系统入口形态（ac_subsystem.entry_type：iframe/新窗口/同页；空＝建行时该列留 NULL） */
        private String entryType = "";

        /** 子系统入口地址（ac_subsystem.entry_url；🔴 种子触发键——非空才对该 app 建行，已存在行零触碰） */
        private String entryUrl = "";

        /**
         * 声明式开关（缺省 true）：false＝只种 ac_subsystem 行，不建 ac_partner/ac_sso_redirect 凭证三件套
         * （本地调试子系统形态：门户挂本系统自己的调试入口，无三方对接凭证面；PartnerSeeder 按此跳过，
         * AcSubsystemSeeder 按此放行 partner 缺席建行）。
         */
        private Boolean createPartner = true;

        /**
         * 嵌入形态声明（缺省 ""）：''/none＝现行 embedded=1（壳全隐藏）；nav＝子页保留 topnav/左栏、去身份条。
         * seeder 仅建行落初值用，已存在行零触碰（管理页修改不被重启还原）。
         */
        private String embedChrome = "";
    }
}
