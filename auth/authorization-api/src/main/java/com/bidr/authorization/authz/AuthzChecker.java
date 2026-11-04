package com.bidr.authorization.authz;

/**
 * Title: AuthzChecker
 * Description: 平台管理员（admin/平台档）判定 SPI（T2.3c 上提）——api 只定契约，
 * 判定实现按宿主形态装配：
 * <ul>
 *   <li>authorization server 侧＝{@code LocalAuthzChecker}（同模块，委托
 *       {@code PermitService.isAdmin()}，读 AccountContext/token ROLE_MAP ×
 *       sys_config ACCOUNT_ADMIN_ROLE_ID——全平台唯一 server 端实现）；</li>
 *   <li>远端形态（epc 等经 client 运行时）＝RemoteAuthzChecker（T2.3b/棒4 落地，
 *       走 /authz 端点），本批只留接口消费位。</li>
 * </ul>
 * 消费点：client 侧 {@code AuthAdmin}（@Auth(AuthAdmin) 端点的 admin 门槛）——
 * 经 {@code ObjectProvider<AuthzChecker>} 注入，<b>SPI 缺失时 fail-closed 拒绝</b>
 * （宁可全拒不可漏判，见 AuthAdmin javadoc）。
 *
 * <p>语义对齐 {@code PermitService.isAdmin()}：读<b>当前线程登录态</b>（请求线程＝
 * AccountContext；无上下文线程回落 token ROLE_MAP——server 实现的既有行为）。
 * 静态比对纯函数见 {@link PlatformRoles#containsAdminRole}。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
public interface AuthzChecker {

    /**
     * 当前登录态是否平台管理员。
     *
     * @return true＝平台档（AuthAdmin 端点放行并置 TenantContext 平台档）
     */
    boolean isAdmin();
}
