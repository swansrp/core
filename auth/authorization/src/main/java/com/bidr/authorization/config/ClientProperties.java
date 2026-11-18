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
 * 以及子系统入口种子（AcSubsystemSeeder 消费，2026-11-11 用户拍板方案 A：入口进配置）。
 * <p>空列表 ＝ PartnerSeeder 整体跳过（非对接形态）。yml 绑定形态：</p>
 * <pre>
 * identity:
 *   clients:
 *     - app-key: xxx
 *       app-secret: yyy
 *       name: 某子系统            # 可选：ac_subsystem.name 兜底（缺省用 app-code）
 *       entry-type: iframe        # 可选：ac_subsystem.entry_type（iframe/新窗口/同页）
 *       entry-url: https://host/xxx/   # 可选：ac_subsystem.entry_url；🔴 配了才种/回写入口
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
     * <p>🔴 entryUrl 是子系统入口正源（ac_subsystem.entry_url）——配了即每次启动 upsert 回写
     * （与 appSecret 同哲学：配置覆盖库值，管理页手改会在下次重启被配置顶回）；不配＝不碰入口字段。</p>
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

        /** 子系统入口形态（ac_subsystem.entry_type：iframe/新窗口/同页；空＝入口回写时不动该列） */
        private String entryType = "";

        /** 子系统入口地址（ac_subsystem.entry_url；🔴 种子触发键——非空才对该 app 做入口种/回写） */
        private String entryUrl = "";
    }
}
