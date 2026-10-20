package com.bidr.authorization.tenant.job;

import com.bidr.authorization.holder.TenantContext;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Title: TenantJobExecutor
 * Description: "逐租户跑一段动作"的框架执行器（2026-10-20 下沉 core，取代 erp 项目侧
 * {@code TenantForeachRunner}）：把租户清单逐个套上 core {@code TenantContext}
 * （租户集＝单元素＋当前租户）后执行，全程<b>透明传播异常</b>——"单租户失败跳过继续"
 * 是消费方（{@link TenantJobScheduler}/{@code ErpTenantJobTemplate}）的语义，本类不做，
 * 两层职责不混（项目侧 0.5 棒既定的分层纪律照搬）。
 *
 * <p><b>上下文语义</b>：进入前快照 core 租户档（租户集＋当前租户），循环内逐租户覆写；
 * 结束（含异常路径）恢复进入前原值，进入前无值则彻底清除——绝不把本轮租户档留给
 * 池化线程的下一个任务。执行体只读得到 core 租户档（行级隔离读改写/写闸填充链据此
 * 生效）；ERP 身份面（identityType 等 extraData 键）不归本类管——那是业务侧
 * {@code ErpTenantJobTemplate} 的职责（core 不知道 extraData 键的存在）。</p>
 *
 * <p><b>静态形态</b>：本类无状态、无依赖注入面，静态工具即可（与 {@code TenantContext}
 * 同款"单一入口"纪律：业务要手动逐租户跑一段代码，只许经本类，不许自己摆 ThreadLocal）。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
public final class TenantJobExecutor {

    private TenantJobExecutor() {
    }

    /**
     * 对租户清单逐个执行动作（异常透明传播；执行完恢复进入前的 core 租户档）。
     *
     * @param tenantIds 租户 id 清单（调用方负责快照；null 视同空清单＝零执行）
     * @param action    每租户执行的动作，入参＝租户 id
     */
    public static void runForAllTenants(List<Long> tenantIds, Consumer<? super Long> action) {
        List<Long> prevTenantIds = TenantContext.getTenantIds();
        Long prevCurrent = TenantContext.currentTenantId();
        try {
            if (tenantIds == null) {
                return;
            }
            for (Long tenantId : tenantIds) {
                // core 行级隔离面：非空租户集＋当前租户 ⇒ 该租户的读改写、写闸填充全部就位
                TenantContext.set(Collections.singletonList(tenantId));
                TenantContext.setCurrentTenantId(tenantId);
                action.accept(tenantId);
            }
        } finally {
            if (prevTenantIds == null && prevCurrent == null) {
                TenantContext.remove();
            } else {
                TenantContext.set(prevTenantIds);
                TenantContext.setCurrentTenantId(prevCurrent);
            }
        }
    }

    /**
     * 向 {@link ActiveTenantSource} 要一份启用租户快照后逐个执行
     * （快照语义归 SPI：实现方返回的清单在本方法内一次取定，执行中途新建的租户不进本轮）。
     */
    public static void runForAllTenants(ActiveTenantSource source, Consumer<? super Long> action) {
        List<Long> tenantIds = source.listActiveTenantIds();
        runForAllTenants(tenantIds, action);
    }
}
