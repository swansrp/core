package com.bidr.authorization.audit;

/**
 * 审计成功判定策略 SPI（core 定义、宿主装实现——默认无实现＝只正常返回算成功）。
 *
 * <p><b>动机</b>：bidr 框架族的 Portal 写尾段以 {@code Resp.notice(...)} 收尾，其实现是抛
 * {@code NoticeException}（配套 {@code @Transactional(noRollbackFor)}）＝业务已成功。
 * 默认口径"方法抛异常不产审计行"会把这类成功通道整面漏记。宿主注册本接口实现，把自家
 * 框架的"以异常作成功通道"收尾计为成功：切面照常组装落审计行，再把异常<b>原样 rethrow</b>
 * （MVC 异常处理器依赖它产出响应，吞掉即破坏前端契约）。</p>
 *
 * <p><b>判定纪律</b>：本策略只回答"这个异常是否代表业务成功"；真失败（校验拒绝/闸门拦截/
 * 系统异常）必须返回 false——失败操作没有"变更事实"，且异常 message 可能含用户输入
 * （泄密面），不落审计行。策略实现自身抛错按 false 处理（保守：宁可漏记成功行，不可误记失败行）。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
public interface AuditTrailSuccessPolicy {

    /**
     * proceed 抛出的异常是否计为"业务成功"（成功＝落审计行后原样 rethrow）。
     *
     * @param thrown 方法体抛出的异常（不为 null）
     * @return true＝成功通道；false＝真失败，不产审计行
     */
    boolean countsAsSuccess(Throwable thrown);
}
