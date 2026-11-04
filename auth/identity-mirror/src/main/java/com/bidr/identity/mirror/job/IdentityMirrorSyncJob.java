package com.bidr.identity.mirror.job;

import com.bidr.identity.mirror.service.IdentityMirrorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Title: IdentityMirrorSyncJob
 * Description: 身份镜像定时同步调度（T2.3d）——形态照 authorization 侧 IdentitySyncJob 惯例：
 * {@code @ConditionalOnProperty(identity.mirror.sync.enabled=true)}（默认 false，不开启则 bean
 * 不注册、cron 占位符不求值）＋{@code @Scheduled(cron=${identity.mirror.sync.cron:0 0 * * * ?})}
 * ＋ApplicationReadyEvent 首跑（子系统起来先拉平一次，再等整点周期）。
 * <p>异常兜底：拉取失败（配置缺失/HTTP 失败/库异常）整趟吞掉记 log.error，不炸启动、
 * 不炸调度线程——下个周期重试；失败趟不写水位（水位只标记成功落库的快照）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "identity.mirror.sync", name = "enabled", havingValue = "true")
public class IdentityMirrorSyncJob {

    private final IdentityMirrorSyncService identityMirrorSyncService;

    /** 定时同步（identity.mirror.sync.cron，缺省 0 0 * * * ?＝每整点一次） */
    @Scheduled(cron = "${identity.mirror.sync.cron:0 0 * * * ?}")
    public void sync() {
        runOnce("scheduled");
    }

    /** 启动首跑：装配就绪后先拉平一次镜像 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runOnce("startup");
    }

    private void runOnce(String trigger) {
        try {
            identityMirrorSyncService.synchronize();
        } catch (Exception e) {
            log.error("[身份镜像]({})同步失败，本趟跳过待下周期重试（失败趟不写水位）", trigger, e);
        }
    }
}
