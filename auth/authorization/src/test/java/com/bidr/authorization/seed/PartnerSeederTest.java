package com.bidr.authorization.seed;

import com.bidr.authorization.config.ClientProperties;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSsoRedirect;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSsoRedirectService;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;

/**
 * Title: PartnerSeederTest
 * Description: 三方应用种子离线腿（T1.0 棒1）——mock 仓库层，不连库；
 * 覆盖「identity.clients 空 → 整体 skip」与「非空 → partner 幂等 upsert＋白名单只补缺行」两态。
 * DB 真值腿交真机（重启第二次应零新增）。
 *
 * @author Sharp
 * @since 2026-11-01
 */
public class PartnerSeederTest {

    private ClientProperties.ClientItem item(String appKey, String secret, String... uris) {
        ClientProperties.ClientItem item = new ClientProperties.ClientItem();
        item.setAppKey(appKey);
        item.setAppSecret(secret);
        item.setRedirectUris(Arrays.asList(uris));
        return item;
    }

    /** 空列表 → 整体 skip，两张表零交互。 */
    @Test
    public void emptyClientsSkipsEntirely() throws Exception {
        ClientProperties properties = new ClientProperties();
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSsoRedirectService redirectService = Mockito.mock(AcSsoRedirectService.class);

        new PartnerSeeder(properties, partnerService, redirectService).run(null);

        Mockito.verifyNoInteractions(partnerService, redirectService);
    }

    /** 非空 → 缺 partner 建行（platform/status 照现库基线形态），白名单已登记行不重插、缺行补插。 */
    @Test
    public void clientsUpsertPartnerAndOnlyFillMissingRedirects() throws Exception {
        ClientProperties properties = new ClientProperties();
        properties.setClients(Collections.singletonList(item("k1", "s1", "u1", "u2")));
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSsoRedirectService redirectService = Mockito.mock(AcSsoRedirectService.class);
        Mockito.when(partnerService.getByAppKey("k1")).thenReturn(null);
        // insert 回填自增 id，供白名单挂 partner_id
        Mockito.when(partnerService.insert(Mockito.any(AcPartner.class))).thenAnswer(invocation -> {
            AcPartner partner = invocation.getArgument(0);
            partner.setId(77L);
            return true;
        });
        Mockito.when(redirectService.existsValid(77L, "u1")).thenReturn(true);   // 已登记
        Mockito.when(redirectService.existsValid(77L, "u2")).thenReturn(false);  // 缺行

        new PartnerSeeder(properties, partnerService, redirectService).run(null);

        ArgumentCaptor<AcPartner> partnerCaptor = ArgumentCaptor.forClass(AcPartner.class);
        Mockito.verify(partnerService, Mockito.times(1)).insert(partnerCaptor.capture());
        AcPartner inserted = partnerCaptor.getValue();
        Assert.assertEquals("k1", inserted.getAppKey());
        Assert.assertEquals("s1", inserted.getAppSecret());
        Assert.assertEquals("platform 照现库基线形态（probe 取证＝空串）", "", inserted.getPlatform());
        Assert.assertEquals("status 照现库基线形态（probe 取证＝1）", Integer.valueOf(1), inserted.getStatus());

        ArgumentCaptor<AcSsoRedirect> redirectCaptor = ArgumentCaptor.forClass(AcSsoRedirect.class);
        Mockito.verify(redirectService, Mockito.times(1)).insert(redirectCaptor.capture());
        Assert.assertEquals(Long.valueOf(77L), redirectCaptor.getValue().getPartnerId());
        Assert.assertEquals("u2", redirectCaptor.getValue().getRedirectUri());
        Assert.assertEquals("1", redirectCaptor.getValue().getValid());
    }
}
