package com.bidr.authorization.tenant;

import java.util.Optional;

/**
 * Title: TenantResolver
 * Description: 租户归属解析 SPI——身份创建点（注册/首登即建/JIT 建号等三链漏斗）在铸
 * {@code AcUser} 行时统一经它取得该身份应归属的租户业务编码（ac_tenant.code 口径）。
 *
 * <p>内核侧（core/authorization）提供默认实现 FixedTenantResolver：读
 * {@code identity.tenant.code} 配置；配置空白 ⇒ {@link Optional#empty()}。</p>
 *
 * <p>语义裁定：</p>
 * <ul>
 *   <li>返回 {@code Optional.empty()} ＝「非租户形态／未配置」——身份创建点据此不赋租户
 *       （不写 ac_user_tenant），账号落为全局账号；不是错误，也不得回退成猜测。</li>
 *   <li>返回有值 ＝ 创建点应把该 code 对应租户 id 绑进 ac_user_tenant；
 *       code 是否真实在库由内核种子链（TenantSeeder）保证，解析器不做库存在性校验。</li>
 *   <li>本接口只解析租户，不做鉴权、不做身份创建——桥实现（pack-bidr / pack-plsintec）
 *       可覆盖内核默认实现以接入客户侧租户映射（如按邮箱域/组织树推导）。</li>
 * </ul>
 *
 * <p>签名钉死（T1.0 棒1 产出、棒3 消费）：方法集不得增删改，参数与返回类型不得变——
 * 本接口是跨仓契约，改动即破坏两座桥的编译形态。</p>
 *
 * @author Sharp
 * @since 2026-11-01
 */
public interface TenantResolver {

    /**
     * 解析某身份应归属的租户业务编码。
     *
     * @param username 身份账号（登录名/外部唯一标识，永不为 null 的语义由调用方保证；
     *                 实现对 null 入参应按「未配置」处理返回 empty，不抛异常）
     * @return 租户业务编码（ac_tenant.code 口径）；非租户形态/未配置 ⇒ {@link Optional#empty()}
     */
    Optional<String> resolveTenantCode(String username);
}
