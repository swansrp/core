package com.bidr.authorization.service.login;

/**
 * Title: SsoUserHandler
 * Description: SSO 身份源用户名解析 SPI（core 侧锚点，M15 棒 0 纯加法）。
 * <p>
 * core 只定义"从当前请求上下文解析出 SSO 已验签用户名"这一契约，自身不感知任何具体 IdP：
 * 各部署形态在自己一侧提供 bean——bidr 形态＝idm 验签后的线程态（见 bidr-framework 的
 * {@code IdmSsoUserHandler}，@ConditionalOnClass 保护）；子系统自接其它 IdP＝自写实现；
 * plsintec 等无 idm 的形态不提供 bean，SSO 登录口按"未接入SSO身份源"业务拒绝，不影响启动。
 * </p>
 * <p>
 * 🔴 全应用至多一个生效实现：注册多个实现时须其一标注 {@code @Primary} 或保证仅一个生效，
 * 否则 SSO 登录口按"SsoUserHandler 实现冲突"业务拒绝（M15 棒0 C9 修订口径＝
 * {@code ObjectProvider#getIfAvailable()}：null＝未接入、{@code NoUniqueBeanDefinitionException}＝冲突；
 * 实证 {@code orderedStream()} 不按 {@code @Primary} 去重故不采用）。
 * </p>
 * <p>
 * 🔴 实现自身装配故障（{@code BeanCreationException} 等）与 {@code resolveUserName()} 抛出的运行时异常
 * <b>不</b>转业务拒绝，按框架口径上浮为系统错误——伪装成"实现冲突"会掩盖真实根因（review#3 S-1 已钉负向腿）。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
public interface SsoUserHandler {

    /**
     * 解析当前 SSO 上下文中的用户名（对应 ac_user.user_name 口径）。
     *
     * @return 已验签的用户名；返回 null 或空白视为身份未解析（登录口将业务拒绝）
     */
    String resolveUserName();
}
