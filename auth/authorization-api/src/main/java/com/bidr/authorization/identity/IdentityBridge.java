package com.bidr.authorization.identity;

import com.bidr.authorization.identity.BridgeUserPrincipal;
import com.bidr.authorization.identity.IdentityLoginCmd;
import com.bidr.authorization.identity.OrgSyncResult;

/**
 * Title: IdentityBridge
 * Description: 身份桥 SPI——SSO 授权跳转、票据换身份、组织同步三件事的单点契约。
 * 语义来自 bidr 侧 InkHubLoginService.login()（id_token 票换身份）与
 * plsintec 侧 FeishuSsoService.ssoLogin(String code)（OAuth code 换身份），
 * 两座桥（pack-bidr / pack-plsintec）各出一份实现，内核只面向本接口装配。
 *
 * 🔗 边界裁定（M-1）：桥只验票、回 {@link BridgeUserPrincipal}，不签产品 JWT——
 * core 的 com.bidr.authorization.vo.login.LoginRes 不复用，产品登录态由内核凭 principal 自铸。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public interface IdentityBridge {

    /**
     * 产出 SSO 授权页跳转地址。
     * 语义来自 plsintec FeishuSsoService.getAuthorizeUrl(String state)（飞书 OAuth 授权页），
     * 入口形态对照 FeishuSsoController.authorize(state)。
     *
     * <p>bidr 形态：bidr 侧登录由框架 ambient SSO 过滤器接管（票据经 IdmHolder 注入），
     * 实现可返回公司 SSO 登录页跳转地址；ambient 全覆盖形态下内核也可不调用本方法。</p>
     *
     * <p>state 调和约定（与参考实现的回跳语义对齐——飞书侧 state 实为回跳路径，非纯防伪串）：</p>
     * <ul>
     *   <li>内核生成 state ＝ nonce + 回跳路径编码，建议 {@code nonce|path} 格式：
     *       nonce 段供回调防伪校验，path 段供前端恢复页面（如 {@code /pan?folderId=3}）</li>
     *   <li>桥必须把 state <b>原样</b>编入授权请求，并在回调原样带回，不做解释</li>
     *   <li>飞书 state 上限 128 字节，路径过长时由内核侧负责压缩/截断策略</li>
     * </ul>
     *
     * @param state 内核生成的状态串（nonce + 回跳路径编码，见上述调和约定），桥原样透传
     * @return 完整授权页 URL（302 目标）
     */
    String authorizeUrl(String state);

    /**
     * 票据换身份。三态票据（code / token / state）哪个非空用哪个，见 {@link IdentityLoginCmd}。
     * 语义来自 bidr InkHubLoginService.login()（id_token 票）＋ plsintec
     * FeishuSsoService.ssoLogin(String code)（code 票）。
     *
     * <p>参考实现勘误：InkHubLoginService 的 id_token 实际由框架 SSO 过滤器 ambient 解析进
     * IdmHolder（并非方法参数直传）；T0.3 迁移实现时需选边——桥内自解析票据，
     * 或内核解析后经 {@link IdentityLoginCmd} 传入，两桥实现须保持同一取边。</p>
     *
     * <p>验票失败抛桥侧运行时异常，由内核统一兜底；本契约不定义受检异常。</p>
     *
     * @param cmd 登录命令（含三态票据之一）
     * @return 桥侧身份主体（外部身份，未签发产品 JWT）
     */
    BridgeUserPrincipal login(IdentityLoginCmd cmd);

    /**
     * 全量组织同步：部门树＋账号一次拉平。
     * 语义来自 bidr MdmJobService.syncMdmDept() / syncMdmAccount()（MDM 定时同步）
     * 与 plsintec FeishuSyncService.syncAll()（飞书通讯录同步）。
     * 返回纯数据（{@link OrgSyncResult}），落库/建缓存由内核统一执行——桥不写内核表。
     *
     * @return 部门行＋用户行＋同步时间
     */
    OrgSyncResult syncOrg();

    /**
     * 启动期初始化（可选实现）。
     * 语义来自 bidr DeptInitService.initDeptFormMdm()（启动时组织初始化）。
     * 默认空体：桥无启动初始化需求时不必覆盖；内核在装配完成后调用一次。
     */
    default void onStartup() {
    }
}
