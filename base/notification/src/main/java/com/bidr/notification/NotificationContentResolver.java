package com.bidr.notification;

import com.bidr.task.AsyncTaskStatus;
import com.bidr.task.dao.entity.SaAsyncTask;

import java.util.Optional;

/**
 * 任务→消息翻译 SPI（core 只长骨架不认业务："解析失败"怎么说、跳哪，由宿主实现翻人话）。
 *
 * <p>宿主实现为 {@code @Component} 即被 {@code AsyncTaskNotificationBridge} 收集
 * （{@code supports} 命中即问 {@link #resolve}）；targetUser 缺省取 {@code task.getCreateBy()}
 * （提交人）。</p>
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
public interface NotificationContentResolver {

    /** 是否消费该任务类型（taskType 宿主自定义串） */
    boolean supports(String taskType);

    /**
     * 翻译（返回 empty＝本任务不发消息）。
     *
     * @param task   任务行（含 error/payload/createBy；观察者链已在重建的账号上下文内）
     * @param status 终态档（SUCCESS／DEAD；预算内 FAILED 不进本口）
     */
    Optional<NotificationDraft> resolve(SaAsyncTask task, AsyncTaskStatus status);
}
