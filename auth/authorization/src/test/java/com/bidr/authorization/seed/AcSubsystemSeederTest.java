package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
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
 * Description: 子系统种子离线腿（方案B 棒2 三态 → 2026-11-11 方案 A 七腿 → 2026-11-21 本地调试子系统
 * D7 拍板翻转为 create-if-missing 八腿）——mock 仓库层不连库：
 * ①clients 空 → 整体零交互；②未配 entry-url → 该条目跳过；
 * ③partner 缺席＋create-partner 缺省（true）→ 该条目不碰 ac_subsystem；
 * ④配置齐＋行缺 → 建完整行（name 兜底/入口/embed_chrome 归一/partner 绑定/status/visible/审计=subsystem-seed）；
 * ⑤行在＋入口同值 → 幂等零写；⑥行在＋入口不同 → 零触碰不回写（create-if-missing：管理页/SQL 是已存在行的权威）；
 * ⑦name 未配 → 建行用 app_code 兜底；⑧create-partner=false＋partner 缺席 → 建行 partner_id=NULL。
 * DB 真值腿交真机（重启第二次应零新增）。形态照 PartnerSeederTest。
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

    /** ③partner 缺席且 create-partner 缺省（true）→ 该条目不碰 ac_subsystem（维持跳过语义）。 */
    @Test
    public void skipsItemWhenPartnerMissing() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(null);
        runSeeder(item(APP, "某子系统", "iframe", "https://host/xxx/"));
        Mockito.verifyNoInteractions(subsystemService);
    }

    /** ④配置齐＋行缺 → 建完整行：name/entry_type/entry_url/partner_id/status/visible/审计字段钉死，
     *  审计来源＝subsystem-seed（2026-11-21 起不再用 SYSTEM_USER）；embed_chrome 未配 → 归一 NULL。 */
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
        Assert.assertEquals("embed_chrome 未配（空串）＝建行列留 NULL", null, seeded.getEmbedChrome());
        Assert.assertEquals(Long.valueOf(5L), seeded.getPartnerId());
        Assert.assertEquals("status=1（启用）", "1", seeded.getStatus());
        Assert.assertEquals("visible_to_all_tenant=1（公开目录）", "1", seeded.getVisibleToAllTenant());
        Assert.assertEquals("create_by=来源审计常量", AcSubsystemSeeder.UPDATE_BY, seeded.getCreateBy());
        Assert.assertEquals("update_by=来源审计常量", AcSubsystemSeeder.UPDATE_BY, seeded.getUpdateBy());
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

    /** ⑥行在＋入口不同（如管理页手改/共库遗留旧地址）→ 零触碰：不 insert 不 update
     *  （create-if-missing：已存在行的权威是管理页/SQL，重启不还原；原「回写入口两列」语义已退役）。 */
    @Test
    public void leavesRowUntouchedWhenEntryDifferent() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(partner(5L));
        AcSubsystem row = new AcSubsystem();
        row.setAppCode(APP);
        row.setName("库里已有的名字");
        row.setEntryType(null);
        row.setEntryUrl("http://10.3.6.60:18081/");
        Mockito.when(subsystemService.selectById(APP)).thenReturn(row);

        runSeeder(item(APP, "", "iframe", "http://pre-front.bidr.com.cn/epc-ai/"));

        Mockito.verify(subsystemService, Mockito.never()).insert(Mockito.any(AcSubsystem.class));
        Mockito.verify(subsystemService, Mockito.never()).updateById(Mockito.any(AcSubsystem.class));
        // 库行对象未被改写（entry_url 仍是库值）
        Assert.assertEquals("http://10.3.6.60:18081/", row.getEntryUrl());
        Assert.assertEquals("库里已有的名字", row.getName());
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

    /** ⑧create-partner=false＋partner 缺席 → 照常建行：partner_id=NULL（未绑定）、
     *  embed_chrome 按 yml 落初值（nav）、审计=subsystem-seed。 */
    @Test
    public void seedsRowWithNullPartnerWhenCreatePartnerFalse() {
        Mockito.when(partnerService.getByAppKey(APP)).thenReturn(null);
        Mockito.when(subsystemService.selectById(APP)).thenReturn(null);

        ClientProperties.ClientItem entry = item(APP, "本地调试", "iframe", "https://host/dbg/");
        entry.setCreatePartner(false);
        entry.setEmbedChrome("nav");
        runSeeder(entry);

        ArgumentCaptor<AcSubsystem> captor = ArgumentCaptor.forClass(AcSubsystem.class);
        Mockito.verify(subsystemService, Mockito.times(1)).insert(captor.capture());
        AcSubsystem seeded = captor.getValue();
        Assert.assertEquals("partner 缺席＋create-partner=false → partner_id=NULL（未绑定）", null, seeded.getPartnerId());
        Assert.assertEquals("nav", seeded.getEmbedChrome());
        Assert.assertEquals("https://host/dbg/", seeded.getEntryUrl());
        Assert.assertEquals(AcSubsystemSeeder.UPDATE_BY, seeded.getCreateBy());
        Assert.assertEquals(AcSubsystemSeeder.UPDATE_BY, seeded.getUpdateBy());
    }
}
