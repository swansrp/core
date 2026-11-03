package com.bidr.kernel.service.outbox;

import com.bidr.kernel.mybatis.dao.entity.SyncOutbox;
import com.bidr.kernel.mybatis.dao.repository.SyncOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Title: OutboxRecorder
 * Description: 变更事件记录器（T3.3a）——业务侧改完数据后调一句 {@code record(...)} 落一行事件。
 * <p>
 * 三条刻意的设计取舍：
 * <ol>
 *   <li><b>不挂 {@code @Transactional}</b>：单行 insert，跟随调用方事务才是正确语义——
 *       业务回滚了事件也跟着没，业务提交了事件才可见。自己开新事务会产生"业务失败但事件已发"的幽灵事件。</li>
 *   <li><b>不 try/catch</b>：写发件箱失败要不要连坐业务，是消费方的选择。核心吞掉异常＝把
 *       "事件丢了"变成静默事实；往上抛则让调用方自己决定（加重试、降级或直接失败）。</li>
 *   <li><b>不读 {@code TenantContext}</b>：租户因此由调用方当形参传进来。kernel 不允许依赖
 *       authorization-api（那会把底座钉死在上层），这也是 {@code tenantId} 是第一个形参的原因。</li>
 * </ol>
 * <p>
 * {@code occurredAt} 在这里取 {@code new Date()}：事件时刻不是落库时刻，补录/积压时两者会差很远，
 * 而保留期与对端的时间语义都按"发生"算。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
@RequiredArgsConstructor
public class OutboxRecorder {

    private final SyncOutboxService syncOutboxService;

    /**
     * 追加一条变更事件。
     *
     * @param tenantId  事件归属租户，null＝全局事件
     * @param aggType   聚合类型（消费方自定义字符串，如 USER/ORG/PROJECT）
     * @param aggId     聚合标识（被变更实体主键）
     * @param eventType 事件类型（如 CREATED/UPDATED/DELETED）
     * @param payload   事件载荷 JSON，核心不解析、原样转发
     */
    public void record(Long tenantId, String aggType, Long aggId, String eventType, String payload) {
        SyncOutbox event = new SyncOutbox();
        event.setTenantId(tenantId);
        event.setAggType(aggType);
        event.setAggId(aggId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setOccurredAt(new Date());
        syncOutboxService.save(event);
    }
}