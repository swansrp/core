package com.bidr.authorization.seed;

import com.bidr.authorization.constants.AuthConstants;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Title: AcSubsystemSeederTest
 * Description: 子系统首启兜底种子离线腿（方案B 棒2）——mock 仓库层，不连库；
 * 覆盖三态：①partner 缺席 → 整体 skip（不碰 ac_subsystem）；②子系统已在位 → 幂等跳过；
 * ③partner 在位＋子系统缺行 → 兜底插一行（partner_id 绑定、status/visible_to_all_tenant='1'）。
 * DB 真值腿交真机（重启第二次应零新增）。形态照 PartnerSeederTest。
 *
 * @author Sharp
 * @since 2026-11-06
 */
public class AcSubsystemSeederTest {

    private AcPartner partner(long id) {
        AcPartner partner = new AcPartner();
        partner.setId(id);
        partner.setAppKey(AcSubsystemSeeder.EPC_APP_CODE);
        return partner;
    }

    /** partner 缺席（identity.clients 未配 epc-ai）→ 零交互。 */
    @Test
    public void skipsEntirelyWhenPartnerMissing() throws Exception {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Mockito.when(partnerService.getByAppKey(AcSubsystemSeeder.EPC_APP_CODE)).thenReturn(null);

        new AcSubsystemSeeder(partnerService, subsystemService).run(null);

        Mockito.verifyNoInteractions(subsystemService);
    }

    /** 子系统已在位 → 只读判重、不重插。 */
    @Test
    public void skipsWhenSubsystemAlreadySeeded() throws Exception {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Mockito.when(partnerService.getByAppKey(AcSubsystemSeeder.EPC_APP_CODE)).thenReturn(partner(5L));
        Mockito.when(subsystemService.selectById(AcSubsystemSeeder.EPC_APP_CODE)).thenReturn(new AcSubsystem());

        new AcSubsystemSeeder(partnerService, subsystemService).run(null);

        Mockito.verify(subsystemService, Mockito.never()).insert(Mockito.any(AcSubsystem.class));
    }

    /** partner 在位＋子系统缺行 → 兜底插一行：app_code/name/partner_id/status/visible_to_all_tenant 钉死。 */
    @Test
    public void seedsFallbackRowWhenPartnerPresentAndRowMissing() throws Exception {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Mockito.when(partnerService.getByAppKey(AcSubsystemSeeder.EPC_APP_CODE)).thenReturn(partner(5L));
        Mockito.when(subsystemService.selectById(AcSubsystemSeeder.EPC_APP_CODE)).thenReturn(null);

        new AcSubsystemSeeder(partnerService, subsystemService).run(null);

        ArgumentCaptor<AcSubsystem> captor = ArgumentCaptor.forClass(AcSubsystem.class);
        Mockito.verify(subsystemService, Mockito.times(1)).insert(captor.capture());
        AcSubsystem seeded = captor.getValue();
        Assert.assertEquals(AcSubsystemSeeder.EPC_APP_CODE, seeded.getAppCode());
        Assert.assertEquals(AcSubsystemSeeder.EPC_NAME, seeded.getName());
        Assert.assertEquals(Long.valueOf(5L), seeded.getPartnerId());
        Assert.assertEquals("status=1（启用）", "1", seeded.getStatus());
        Assert.assertEquals("visible_to_all_tenant=1（公开目录）", "1", seeded.getVisibleToAllTenant());
        Assert.assertEquals(AuthConstants.SYSTEM_USER, seeded.getCreateBy());
        Assert.assertEquals(AuthConstants.SYSTEM_USER, seeded.getUpdateBy());
    }
}
