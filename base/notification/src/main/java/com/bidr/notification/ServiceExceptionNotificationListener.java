package com.bidr.notification;

import com.bidr.authorization.service.menu.MenuSeeder;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 框架腿：严重系统异常 → 内置管理员站内信告警（M21 B 腿；宿主零接线即生效）。
 *
 * <p><b>框架腿定位</b>：kernel {@link ServiceExceptionEvent}（发布点＝{@code ResponseExceptionHandler}
 * ＋{@code AuditTrailWriter}，每次 {@code ServiceException} 都发，载荷含错误码/级别/堆栈/请求信息）
 * 的框架侧消费者，与 comm/email 的 {@code ServiceExceptionEmailListener}（邮件告警腿）对称——
 * 同一事件、站内信/邮件两渠道互不依赖。本腿逻辑自 inkhub 业务样例
 * {@code ErrorEventNotificationSample}（2026-10-25 落码）同日迁 core 框架化：样例口径
 * 升级为"默认开＋可覆盖可关闭"，样例类已删除，inkhub 侧零改动享受本腿。</p>
 *
 * <p><b>过滤口径</b>：{@code !isSevere()}（WARN 及以下是日志不是告警）或
 * {@code isBizException()}（业务异常是给用户的话术，不是给管理员的告警）任一命中即跳过。</p>
 *
 * <p><b>收件人配置三态</b>（sys_config {@value #CONFIG_TARGET_USER}，缓存读改配置即时生效）：</p>
 * <table border="1">
 *   <caption>notification.error-alert.target-user 三态</caption>
 *   <tr><th>配置值</th><th>行为</th></tr>
 *   <tr><td>未配置 / 读异常</td><td>缺省发内置管理员 000001（{@link MenuSeeder#SYSTEM_USER} 单一正源）</td></tr>
 *   <tr><td>非空且 ≠ off</td><td>该值为收件人（ac_user.customer_number）</td></tr>
 *   <tr><td>off（忽略大小写）/ 空串</td><td>整腿关闭，本条不发</td></tr>
 * </table>
 *
 * <p><b>🔴 租户兜底契约（业务/框架双侧必读）</b>：draft 不设 tenantId 时，publisher 会用
 * <b>当前线程的租户上下文</b>兜底（{@code currentTenantOrNull}）；本监听器跑在 {@code @Async}
 * 线程，上下文经 {@code ContextCopyingTaskDecorator} 从出错请求线程复制而来——所以告警消息的
 * 归属＝<b>请求者租户</b>，无上下文时才 null＝全局。也就是说"不设 tenantId"≠"全局可见"。
 * 要"强制全局"（平台级告警发多租户管理员）需 core 提供显式全局哨兵/重载后另立批次——
 * 在 core 有哨兵之前，业务侧无法表达强制 null。</p>
 *
 * <p><b>堆栈坑提示</b>：{@code ServiceException} 带 lombok {@code @Data}，{@code printStackTrace}
 * 首行走覆写的 {@code toString()}（{@code ServiceException(errCode=…)} 字段形态）而非全限定类名——
 * 排查日志/断言堆栈时钉堆栈帧行（{@code \tat …}），勿钉异常全限定名。</p>
 *
 * <p>publish 内部绝不抛（publisher 铁律：提示失败不许变成业务二次失败），本监听器不再兜
 * try-catch 假装成功。</p>
 *
 * @author sharp (M21：报错告警腿迁 core 2026-10-25)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceExceptionNotificationListener {

    /** sys_config 键：告警收件人（三态语义见类 javadoc 表；缺省＝内置管理员） */
    public static final String CONFIG_TARGET_USER = "notification.error-alert.target-user";

    private final NotificationPublisher notificationPublisher;
    private final SysConfigCacheService sysConfigCacheService;

    /**
     * 严重系统异常 → 管理员告警站内信。
     *
     * <p>过滤口径：低级别（WARN 及以下）是日志不是告警；业务异常是给用户的话术，
     * 不是给管理员的告警——两条任一命中即跳过；收件人解析为 null（显式 off/空串）同样跳过。</p>
     */
    @Async
    @EventListener
    public void onServiceException(ServiceExceptionEvent event) {
        if (event == null || !event.isSevere() || event.isBizException()) {
            return;
        }
        String targetUser = resolveTargetUser();
        if (targetUser == null) {
            return;
        }
        notificationPublisher.publish(NotificationDraft.builder()
                .msgType("SYSTEM_ERROR_ALERT")
                .level(NotificationLevel.ERROR)
                .targetUser(targetUser)
                .title("[" + event.getErrCode().getErrCode() + "] " + event.getErrorMessage())
                .summary(StringUtils.hasText(event.getRequestUrl()) ? event.getRequestUrl() : event.getErrType())
                .content(event.getStackTrace())                       // publisher 侧截 2000
                .bizType("ServiceException")
                .bizId(String.valueOf(event.getErrCode().getErrCode()))
                .dedupeKey("ERR:" + event.getErrCode().getErrCode() + ":"
                        + event.getException().getClass().getName())  // 同错误未读合并防刷屏
                .build());
    }

    /**
     * 收件人解析（三态；照 {@link NotificationGate}"缺项读取会抛、须吞掉"的现行口径全程 quiet）：
     * 读异常/缺项 → 缺省 {@link MenuSeeder#SYSTEM_USER}；非空且 ≠ off → 该值；
     * off（忽略大小写）或空串 → {@code null}＝不发。
     */
    private String resolveTargetUser() {
        String configured = readConfigQuietly();
        if (configured == null) {
            return MenuSeeder.SYSTEM_USER;
        }
        String trimmed = configured.trim();
        if (trimmed.isEmpty() || "off".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /** 读配置（缺项/缓存不可用＝返回 null，绝不抛——PA_DATA_NOT_EXIST 教训，同 NotificationGate） */
    private String readConfigQuietly() {
        try {
            return sysConfigCacheService.getSysConfigValue(CONFIG_TARGET_USER);
        } catch (Exception e) {
            return null;
        }
    }
}
