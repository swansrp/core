package com.bidr.authorization.tenant.job;

import java.util.List;

/**
 * Title: ActiveTenantSource
 * Description: 启用租户枚举 SPI（2026-10-20 下沉 core）：租户级定时任务"遍历哪些租户"
 * 的唯一取数口。框架（{@link TenantJobScheduler}）在触发点向它要一份<b>快照清单</b>，
 * 逐个租户执行 {@link TenantScopedJob#execute(Long)}——框架不持有游标/连接跨租户，
 * 执行中途新建的租户留到下一触发点（定时轮转的天然口径）。
 *
 * <p><b>为什么是 SPI 而不是框架内置查询</b>：core 是全项目共享框架，不依赖任何业务表——
 * "哪些租户算启用"是业务口径（如 erp＝{@code biz_plat_tenant.status='active'}，业务租户
 * 唯一正源；框架 {@code ac_tenant} 只是身份镜像、可能与业务状态有落差），必须由业务侧
 * 以 bean 形式提供。容器内没有本接口实现且存在任务时＝装配事故，调度器启动 fail-fast
 * （宁可不启动，不收一个永远空转的任务）。</p>
 *
 * <p><b>实现纪律</b>：① 返回<b>快照</b>（新列表，不暴露内部可变结构）；② 本方法在框架
 * worker 线程调用、无请求上下文——查询语句自身不得依赖登录态/租户档（平台级表查询的
 * 合法场景）；③ 返回 null 视同空清单；④ 同一触发点内重复枚举不做缓存——由实现方决定
 * 取数成本与一致性口径。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
public interface ActiveTenantSource {

    /**
     * @return 本触发点要执行的启用租户 id 快照；null 视同空清单
     */
    List<Long> listActiveTenantIds();
}
