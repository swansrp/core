package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
import com.bidr.authorization.constants.AuthConstants;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

/**
 * Title: AcSubsystemSeederTest
 * Description: 子系统种子离线腿（方案B 棒2 三态 → 2026-11-11 方案 A 扩为七腿）——mock 仓库层不连库：
 * ①clients 空 → 整体零交互；②未配 entry-url → 该条目跳过；③partner 缺席 → 该条目不碰 ac_subsystem；
 * ④配置齐＋行缺 → 建完整行（name 兜底/入口/partner 绑定/status/visible 钉死）；
 * ⑤配置齐＋行在＋入口同值 → 幂等零写；⑥行在＋入口不同 → 只回写入口两列（updateById）；
 * ⑦name 未配 → 建行用 app_code 兜底。DB 真值腿交真机（重启第二次应零新增）。形态照 PartnerSeederTest。
 *
 * @author Sharp
 * @since 2026-11-06
 */
public class AcSubsystemSeederTest {

    private static final String APP = "epc-ai";

    private AcPartnerService partnerService;
    private AcSubsystemService subsystemService;

    @Before
    public void setUp() {
        partnerService = Mockito.mock(AcPartnerService.class);
        subsystemService = Mockito.mock(AcSubsystemService.class);
    }

    private ClientProperties props(ClientProperties.ClientItem item) {
        ClientProperties props = new ClientProperties();
        props.setClients(item == null ? Collections.emptyList() : List.of(item));
        return props;
    }

    private ClientProperties.ClientItem item(String appKey, String name, String entryType, String entryUrl) {
        ClientProperties.ClientItem item = new ClientProperties.ClientItem();
        item.setAppKey(appKey);
        item.setName(name);
        item.setEntryType(entryType);
        item.setEntryUrl(entryUrl);
        return item;
    }

    private AcPartner partner(long id) {
        AcPartner partner = new AcPartner();
        partner.setId(id);
        partner.setAppKey(APP);
        return partner;
    }

    private void runSeeder(ClientProperties.ClientItem item) {
        new AcSubsystemSeeder(partnerService, subsystemService, props(item)).run(null);
    }

    /** ①clients 空 → 整体零交互。 */
    @Test
    public void skipsEntirelyWhenClientsEmpty() {
        Mockito.when(partnerService.getByAppKey(Mockito.anyString())).thenReturn(null);
        new AcSubsystemSeeder(partnerService, subsystemService, props(null)).run(null);
        Mockito.verifyNoInteractions(subsystemService);
        Mockito.verifyNoInteractions(partnerService);
    }

    /** ②条目未配 entry-url → 该条目整条跳过（不查 partner、不碰 subsystem）。 */
    @Test
    public void skipsItemWithoutEntryUrl() {
        new AcSubsystemSeeder(partnerService, subsystemService, props(item(APP, "", "", ""))).run(null);
        Mockito.verifyNoInteractions(subsystemService);
        Mockito.verifyNoInteractions(partnerService);
    }

    /** ③partner 缺席（PartnerSeeder 未种同 key）→ 该条目不碰 ac_subsystem。 */
    @Test
    public void skipsItemWhenPartnerMissing() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(null);
        runSeeder(item(APP, "某子系统", "iframe", "https://host/xxx/"));
        Mockito.verifyNoInteractions(subsystemService);
    }

    /** ④配置齐＋行缺 → 建完整行：name/entry_type/entry_url/partner_id/status/visible/审计字段钉死。 */
    @Test
    public void seedsFullRowWhenConfiguredAndMissing() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(partner(5L));
        Mockito.when(subsystemService.selectById(APP)).thenReturn(null);

        runSeeder(item(APP, "某子系统", "iframe", "https://host/xxx/"));

        ArgumentCaptor<AcSubsystem> captor = ArgumentCaptor.forClass(AcSubsystem.class);
        Mockito.verify(subsystemService, Mockito.times(1)).insert(captor.capture());
        Mockito.verify(subsystemService, Mockito.never()).updateById(Mockito.any(AcSubsystem.class));
        AcSubsystem seeded = captor.getValue();
        Assert.assertEquals(APP, seeded.getAppCode());
        Assert.assertEquals("某子系统", seeded.getName());
        Assert.assertEquals("iframe", seeded.getEntryType());
        Assert.assertEquals("https://host/xxx/", seeded.getEntryUrl());
        Assert.assertEquals(Long.valueOf(5L), seeded.getPartnerId());
        Assert.assertEquals("status=1（启用）", "1", seeded.getStatus());
        Assert.assertEquals("visible_to_all_tenant=1（公开目录）", "1", seeded.getVisibleToAllTenant());
        Assert.assertEquals(AuthConstants.SYSTEM_USER, seeded.getCreateBy());
        Assert.assertEquals(AuthConstants.SYSTEM_USER, seeded.getUpdateBy());
    }

    /** ⑤行在＋入口与配置同值 → 幂等零写（不 insert 不 update）。 */
    @Test
    public void noWriteWhenEntryMatches() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(partner(5L));
        AcSubsystem row = new AcSubsystem();
        row.setAppCode(APP);
        row.setEntryType("iframe");
        row.setEntryUrl("https://host/xxx/");
        Mockito.when(subsystemService.selectById(APP)).thenReturn(row);

        runSeeder(item(APP, "", "iframe", "https://host/xxx/"));

        Mockito.verify(subsystemService, Mockito.never()).insert(Mockito.any(AcSubsystem.class));
        Mockito.verify(subsystemService, Mockito.never()).updateById(Mockito.any(AcSubsystem.class));
    }

    /** ⑥行在＋入口不同（如共库遗留旧地址）→ updateById 只回写入口（实体其余列保持库值）。 */
    @Test
    public void upsertsEntryWhenDifferent() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(partner(5L));
        AcSubsystem row = new AcSubsystem();
        row.setAppCode(APP);
        row.setName("库里已有的名字");
        row.setEntryType(null);
        row.setEntryUrl("http://10.3.6.60:18081/");
        Mockito.when(subsystemService.selectById(APP)).thenReturn(row);

        runSeeder(item(APP, "", "iframe", "http://pre-front.bidr.com.cn/epc-ai/"));

        ArgumentCaptor<AcSubsystem> captor = ArgumentCaptor.forClass(AcSubsystem.class);
        Mockito.verify(subsystemService, Mockito.times(1)).updateById(captor.capture());
        Mockito.verify(subsystemService, Mockito.never()).insert(Mockito.any(AcSubsystem.class));
        AcSubsystem updated = captor.getValue();
        Assert.assertEquals("iframe", updated.getEntryType());
        Assert.assertEquals("http://pre-front.bidr.com.cn/epc-ai/", updated.getEntryUrl());
        // 其余列不碰：name 保持库值（配置 name 空＝不回写显示名）
        Assert.assertEquals("库里已有的名字", updated.getName());
        Assert.assertEquals(AuthConstants.SYSTEM_USER, updated.getUpdateBy());
    }

    /** ⑦name 未配 → 建行显示名用 app_code 兜底。 */
    @Test
    public void fallsBackNameToAppCodeWhenUnset() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(partner(5L));
        Mockito.when(subsystemService.selectById(APP)).thenReturn(null);

        runSeeder(item(APP, "", "", "https://host/xxx/"));

        ArgumentCaptor<AcSubsystem> captor = ArgumentCaptor.forClass(AcSubsystem.class);
        Mockito.verify(subsystemService, Mockito.times(1)).insert(captor.capture());
        Assert.assertEquals(APP, captor.getValue().getName());
        Assert.assertEquals("entry_type 空＝建行列留空", null, captor.getValue().getEntryType());
        Assert.assertEquals("https://host/xxx/", captor.getValue().getEntryUrl());
    }
}
