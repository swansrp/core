package com.bidr.authorization.service.admin;

import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dao.repository.AcSubsystemService;
import com.bidr.authorization.vo.partner.RegisterSubsystemReq;
import com.bidr.authorization.vo.partner.RegisterSubsystemRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.exception.ServiceException;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Title: AdminPartnerServiceTest
 * Description: 渠道注册子系统事务腿（方案B 棒2）——mock 仓储离线验证
 * 「一个事务两行同插」的编排序：先建 partner（appKey/appSecret 服务端生成、status=启用），
 * 再插 ac_subsystem（partner_id=新 partner id），响应带密钥一次性展示＋appCode 回显。
 * <p>非法子系统参数（appCode/name 空）必须**先验**——在任何写库前抛出 ⇒ partner 也不落
 * （@Transactional 兜底插腿 DB 异常时的整体回滚，属容器行为不进离线腿，真机腿覆盖）。</p>
 *
 * @author Sharp
 * @since 2026-11-06
 */
public class AdminPartnerServiceTest {

    private RegisterSubsystemReq req(String appCode, String name) {
        RegisterSubsystemReq req = new RegisterSubsystemReq();
        req.setRemark("EPC 渠道");
        req.setAppCode(appCode);
        req.setName(name);
        req.setEntryType("iframe");
        req.setEntryUrl("https://epc.example.com");
        return req;
    }

    /** 顺风腿：两行同插、partner 先于 subsystem、密钥口径同 /add（RandomUtil 8/16）。 */
    @Test
    public void registerSubsystemCreatesPartnerThenSubsystem() {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        Mockito.when(partnerService.insert(Mockito.any(AcPartner.class))).thenAnswer(invocation -> {
            AcPartner partner = invocation.getArgument(0);
            partner.setId(77L); // 模拟自增 id 回填，供 subsystem 挂 partner_id
            return true;
        });
        AdminPartnerService underTest = new AdminPartnerService(partnerService, subsystemService);

        RegisterSubsystemRes res = underTest.registerSubsystem(req("epc-demo", "EPC 演示子系统"));

        Assert.assertEquals("appCode 回显", "epc-demo", res.getAppCode());
        Assert.assertEquals("appKey 口径同 /add（RandomUtil 8 位）", 8, res.getAppKey().length());
        Assert.assertEquals("appSecret 口径同 /add（RandomUtil 16 位）", 16, res.getAppSecret().length());

        ArgumentCaptor<AcPartner> partnerCaptor = ArgumentCaptor.forClass(AcPartner.class);
        Mockito.verify(partnerService, Mockito.times(1)).insert(partnerCaptor.capture());
        AcPartner partner = partnerCaptor.getValue();
        Assert.assertEquals(res.getAppKey(), partner.getAppKey());
        Assert.assertEquals(res.getAppSecret(), partner.getAppSecret());
        Assert.assertEquals("渠道备注平移", "EPC 渠道", partner.getRemark());
        Assert.assertEquals("status=1（ActiveStatusDict.ACTIVATE，同 /add）",
                Integer.valueOf(1), partner.getStatus());

        ArgumentCaptor<AcSubsystem> subsystemCaptor = ArgumentCaptor.forClass(AcSubsystem.class);
        Mockito.verify(subsystemService, Mockito.times(1)).insert(subsystemCaptor.capture());
        AcSubsystem subsystem = subsystemCaptor.getValue();
        Assert.assertEquals("epc-demo", subsystem.getAppCode());
        Assert.assertEquals("EPC 演示子系统", subsystem.getName());
        Assert.assertEquals("partner_id=新 partner id", Long.valueOf(77L), subsystem.getPartnerId());
        Assert.assertEquals("入口字段平移", "iframe", subsystem.getEntryType());
        Assert.assertEquals("status='1'（注册即门户可见，与 AcSubsystemSeeder 口径一致，I-2）",
                CommonConst.YES, subsystem.getStatus());
    }

    /** 事务腿（先验）：子系统参数非法 ⇒ 两仓储零写入（partner 不落）。 */
    @Test
    public void registerSubsystemRejectsInvalidParamsBeforeAnyWrite() {
        AcPartnerService partnerService = Mockito.mock(AcPartnerService.class);
        AcSubsystemService subsystemService = Mockito.mock(AcSubsystemService.class);
        AdminPartnerService underTest = new AdminPartnerService(partnerService, subsystemService);

        int rejected = 0;
        for (RegisterSubsystemReq bad : new RegisterSubsystemReq[]{req("", "有名"), req("app-1", "")}) {
            try {
                underTest.registerSubsystem(bad);
                Assert.fail("非法参数必须先验拒绝: appCode=" + bad.getAppCode() + " name=" + bad.getName());
            } catch (ServiceException e) {
                rejected++;
            }
        }
        Assert.assertEquals("两例非法输入都被拒", 2, rejected);
        Mockito.verifyNoInteractions(partnerService, subsystemService);
    }
}
