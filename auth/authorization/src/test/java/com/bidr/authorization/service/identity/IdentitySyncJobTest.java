package com.bidr.authorization.service.identity;

import com.bidr.authorization.identity.IdentityBridge;
import com.bidr.authorization.identity.OrgSyncResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Title: IdentitySyncJobTest
 * Description: 组织同步调度离线单测（T1.1 棒4）——桥取用护栏（无桥 bean 跳过该趟不炸）、
 * 启动钩子链（bridge.onStartup 一次＋首轮拉平落库一次）、定时趟编排（syncOrg→apply）。
 * 纯 Mockito；enabled=false 不装配的语义由 @ConditionalOnProperty 承担（容器形态，不在本测）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class IdentitySyncJobTest {

    private ObjectProvider<IdentityBridge> bridgeProvider;
    private IdentityBridge bridge;
    private OrgSyncApplier applier;
    private IdentitySyncJob job;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        bridgeProvider = Mockito.mock(ObjectProvider.class);
        bridge = Mockito.mock(IdentityBridge.class);
        applier = Mockito.mock(OrgSyncApplier.class);
        job = new IdentitySyncJob(bridgeProvider, applier);
    }

    /** 定时趟：桥在 → syncOrg 一次、结果原样交 Applier 落库 */
    @Test
    public void scheduledTickPullsAndApplies() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
        OrgSyncResult result = new OrgSyncResult();
        Mockito.when(bridge.syncOrg()).thenReturn(result);

        job.sync();

        Mockito.verify(bridge).syncOrg();
        ArgumentCaptor<OrgSyncResult> captor = ArgumentCaptor.forClass(OrgSyncResult.class);
        Mockito.verify(applier).apply(captor.capture());
        Assert.assertSame(result, captor.getValue());
    }

    /** 误开形态护栏：enabled 开着但 classpath 无桥 → 跳过该趟（不炸调度线程），Applier 不动 */
    @Test
    public void skippedSilentlyWhenNoBridgeBean() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(null);

        job.sync();

        Mockito.verify(applier, Mockito.never()).apply(Mockito.any());
        Mockito.verifyNoInteractions(bridge);
    }

    /** 启动初始化链：bridge.onStartup() 恰一次（SPI 契约：内核装配后调用一次）＋首轮拉平落库 */
    @Test
    public void startupCallsBridgeHookThenInitialSync() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(bridge);
        Mockito.when(bridge.syncOrg()).thenReturn(new OrgSyncResult());

        job.onStartup();

        Mockito.verify(bridge).onStartup();
        Mockito.verify(bridge).syncOrg();
        Mockito.verify(applier).apply(Mockito.any(OrgSyncResult.class));
    }

    /** 启动初始化无桥形态：钩子与首轮同步整体跳过（enabled=true 但无 pack 桥的误开形态） */
    @Test
    public void startupSkipsEverythingWhenNoBridgeBean() {
        Mockito.when(bridgeProvider.getIfAvailable()).thenReturn(null);

        job.onStartup();

        Mockito.verify(applier, Mockito.never()).apply(Mockito.any());
    }
}
