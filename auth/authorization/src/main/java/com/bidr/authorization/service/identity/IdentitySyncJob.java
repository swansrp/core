package com.bidr.authorization.service.identity;

import com.bidr.authorization.config.OrgSyncProperties;
import com.bidr.authorization.identity.IdentityBridge;
import com.bidr.authorization.identity.OrgSyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Title: IdentitySyncJob
 * Description: 组织定时同步调度（T1.1 棒4，原 bidr 侧 MdmJobService 两 cron 的退役接棒者）——
 * 调 pack-spi {@link IdentityBridge#syncOrg()} 拉数，交 {@link OrgSyncApplier} 落库
 * （"桥拉数、内核落库"的调度半边）。
 *
 * <p>🔴 装配纪律：{@code @ConditionalOnProperty(identity.org-sync.enabled=true)}——开关默认
 * false（{@link OrgSyncProperties} 桩定稿），不开启则本 bean 根本不注册、@Scheduled 不解析
 * （"不开启则不调度"的精确语义；bean 缺席时 cron 占位符不会被求值，enabled=false＋cron 空串
 * 不会炸启动）。开启但 cron 键缺失时占位符回落缺省 {@code 0 15 1 * * ?}（原 MdmJobService
 * 双 cron 0 10/0 15 的合并落点——syncOrg 单趟拉平部门＋账号，取原账号腿 01:15 时点）。</p>
 *
 * <p>启动初始化语义（原 DeptInitService.initDeptFormMdm 启动即拉一次组织）在"桥不写内核表"
 * （M-5）约束下分解为：ApplicationReadyEvent → {@code bridge.onStartup()}（桥侧启动钩子，
 * SPI 契约：内核装配后调用一次）→ {@code runOnce()}（syncOrg→apply，落库在内核）。
 * enabled=false 时整体不装配——关闭组织同步即不接触身份源。</p>
 *
 * <p>桥取用与 {@link com.bidr.authorization.service.login.impl.IdentityBridgeLoginService}
 * 同口径（ObjectProvider，pack-spi 接口＝守卫"pack-spi 有意放行"白名单既有形态）：
 * 无桥 bean 记 warn 跳过该趟（误开形态护栏，不炸调度线程），下趟重试。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "identity.org-sync", name = "enabled", havingValue = "true")
public class IdentitySyncJob {

    private final ObjectProvider<IdentityBridge> identityBridgeProvider;
    private final OrgSyncApplier orgSyncApplier;

    /** 定时同步（identity.org-sync.cron，缺省 0 15 1 * * ?＝原双 cron 合并落点） */
    @Scheduled(cron = "${identity.org-sync.cron:0 15 1 * * ?}")
    public void sync() {
        runOnce("scheduled");
    }

    /** 启动初始化：装配就绪后桥钩子一次＋首轮拉平落库一次（DeptInitService 启动语义承接） */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        IdentityBridge bridge = identityBridgeProvider.getIfAvailable();
        if (bridge != null) {
            bridge.onStartup();
        }
        runOnce("startup");
    }

    private void runOnce(String trigger) {
        IdentityBridge bridge = identityBridgeProvider.getIfAvailable();
        if (bridge == null) {
            log.warn("[组织同步]({})无 IdentityBridge 实现，本趟跳过（误开形态护栏："
                    + "identity.org-sync.enabled 开着但没有 pack 桥在 classpath）", trigger);
            return;
        }
        OrgSyncResult result = bridge.syncOrg();
        orgSyncApplier.apply(result);
    }
}
