package com.bidr.notification;

import com.bidr.notification.dao.repository.SaNotificationRepoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 站内信装配（M21）：默认 store 经 @Bean＋{@code @ConditionalOnMissingBean} 注册——宿主
 * 自己实现 {@link NotificationStore}（用自己的表）时自动让位，无顺序依赖。
 * {@code notification.store.enabled=false}＝不装默认 store 也不建 sa_notification 表
 * （覆盖型宿主用）。
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.store", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(NotificationStore.class)
    public NotificationStore saNotificationStore(SaNotificationRepoService repo) {
        return new SaNotificationStore(repo);
    }
}
