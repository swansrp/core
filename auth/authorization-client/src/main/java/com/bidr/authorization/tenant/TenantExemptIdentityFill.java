package com.bidr.authorization.tenant;

/**
 * 租户豁免入口身份接线 SPI（core 定义、业务装实现——{@code LoginFillTokenInf} 同族先例，
 * ADR 0001 裁定 5 落地形态）。
 *
 * <p><b>消费点</b>：{@code AuthInterceptor} 的 @CrossTenant 前置段——注解命中且已认证时，
 * core 先置平台档（读侧豁免，{@code TenantContext.markPlatform()}）并 WARN 留痕，
 * 再回调本接口。实现方负责把业务侧身份面同步升格为平台身份。</p>
 *
 * <p><b>为什么需要接线</b>：豁免注解的本义是"这个入口是跨租户读面"——行级隔离放行全量；
 * 若业务身份面不同步升格，业务列作用域（如 ERP 的 StaffScopeService 按员工身份收列）与
 * 行级全量视图会拧着。而身份升格所需的 extraData 键名是业务侧单一正源（框架枚举不含、
 * 也不许框架再造），core 写不了业务键——故拆成"core 管豁免置位、业务管身份面"两层，
 * 由本接口在请求线程上桥接。宿主无实现（非 ERP 项目）时静默跳过。</p>
 *
 * @author coder (棒1, 租户豁免治理 2026-10-24)
 */
public interface TenantExemptIdentityFill {

    /**
     * 豁免生效后回调（请求线程、鉴权已通过、平台档已置位）：
     * 把业务侧身份面升格为平台身份。实现方勿在此做权限判定——豁免独立于权限档
     * （ADR 0001 裁定 5），"谁能进"由端点自己的鉴权注解决定。
     */
    void fill();
}
