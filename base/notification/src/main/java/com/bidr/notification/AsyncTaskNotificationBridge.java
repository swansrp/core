package com.bidr.notification;

import com.bidr.task.AsyncTaskObserver;
import com.bidr.task.AsyncTaskStatus;
import com.bidr.task.dao.entity.SaAsyncTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 任务终态→站内信桥（M21 框架红利）：只听 {@code onTerminal} 的 <b>DEAD</b> 档——
 * 预算内 FAILED 正在退避重试（同一任务通知 N 次＝刷屏），SUCCESS 的产物在列表页可见，
 * 都不发。任何宿主接上异步任务底座、实现一个 {@link NotificationContentResolver}，
 * 即自动获得"死透提醒"。
 *
 * <p><b>纪律</b>：本桥自身吞异常（观察者异常不改任务终态——core 逐件 try-catch 之外的
 * 双保险）；开关 {@code notification.async-task.enabled}（sys_config，缺省开）经
 * {@link NotificationGate} 判定。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Slf4j
@Component
public class AsyncTaskNotificationBridge implements AsyncTaskObserver {

    private final ObjectProvider<NotificationContentResolver> resolvers;
    private final NotificationPublisher publisher;
    private final NotificationGate gate;

    public AsyncTaskNotificationBridge(ObjectProvider<NotificationContentResolver> resolvers,
                                       NotificationPublisher publisher, NotificationGate gate) {
        this.resolvers = resolvers;
        this.publisher = publisher;
        this.gate = gate;
    }

    @Override
    public void onTerminal(SaAsyncTask task, AsyncTaskStatus status) {
        if (status != AsyncTaskStatus.DEAD || task == null || !gate.asyncTaskEnabled()) {
            return;
        }
        for (NotificationContentResolver resolver : resolvers.orderedStream()
                .toArray(NotificationContentResolver[]::new)) {
            try {
                if (!resolver.supports(task.getTaskType())) {
                    continue;
                }
                resolver.resolve(task, status)
                        .map(draft -> withDefaultReceiver(draft, task))
                        .ifPresent(publisher::publish);
            } catch (Exception e) {
                log.error("任务失败提醒翻译/发送异常（不影响任务终态）: resolver={} type={} {}",
                        resolver.getClass().getName(), task.getTaskType(), e.getMessage(), e);
            }
        }
    }

    /** targetUser 缺省取提交人（resolver 未指定收件人时） */
    private NotificationDraft withDefaultReceiver(NotificationDraft draft, SaAsyncTask task) {
        if (draft.getTargetUser() != null) {
            return draft;
        }
        NotificationDraft.Builder b = NotificationDraft.builder()
                .msgType(draft.getMsgType())
                .level(draft.getLevel())
                .title(draft.getTitle())
                .summary(draft.getSummary())
                .content(draft.getContent())
                .actionType(draft.getActionType())
                .actionParams(draft.getActionParams() == null ? null : new java.util.LinkedHashMap<>(draft.getActionParams()))
                .bizType(draft.getBizType())
                .bizId(draft.getBizId())
                .dedupeKey(draft.getDedupeKey())
                .tenantId(draft.getTenantId())
                .targetUser(task.getCreateBy());
        if (draft.getExtra() != null) {
            for (java.util.Map.Entry<String, Object> e : draft.getExtra().entrySet()) {
                b.extra(e.getKey(), e.getValue());
            }
        }
        return b.build();
    }
}
