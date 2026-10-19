package com.bidr.wechat.po.miniprogram;

import com.bidr.kernel.utils.JsonUtil;
import org.junit.Assert;
import org.junit.Test;

/**
 * getuserphonenumber 响应模型的 payload 级回归（review P0-1 钉子）：
 * 微信该接口<b>成功响应显式携带 errcode=0</b>（与 jscode2session 成功时不含 errcode 字段不同），
 * 守卫必须放行 errcode==0、只拒非 0 错误码——用官方文档成功样例反序列化后
 * 断言 errCode/phoneInfo/phoneNumber 三层都取得到。WechatMiniService 的
 * errcode 守卫语义以本样例为准（errCode==null || errCode==SUCCESS 才通过）。
 *
 * @author reviewer fix (0.15b 小程序壳与终端登录批)
 */
public class PhoneNumberRespPoParseTest {

    /** 微信官方文档 getuserphonenumber 成功响应样例（结构保真） */
    private static final String SUCCESS_PAYLOAD = "{\"errcode\":0,\"errmsg\":\"ok\","
            + "\"phone_info\":{\"phoneNumber\":\"13580000000\",\"purePhoneNumber\":\"13580000000\","
            + "\"countryCode\":\"86\",\"watermark\":{\"timestamp\":1630000000,\"appid\":\"wx0000000000000000\"}}}";

    @Test
    public void successPayloadCarriesZeroErrCodeAndPhoneInfo() {
        PhoneNumberRespPO po = JsonUtil.readJson(SUCCESS_PAYLOAD, PhoneNumberRespPO.class);
        Assert.assertNotNull(po);
        Assert.assertEquals(Integer.valueOf(0), po.getErrCode());
        // 守卫口径：errCode==null || errCode==WechatErrorCodeConst.SUCCESS 必须放行
        Assert.assertTrue(po.getErrCode() == null || po.getErrCode() == com.bidr.wechat.constant.WechatErrorCodeConst.SUCCESS);
        Assert.assertNotNull(po.getPhoneInfo());
        Assert.assertEquals("13580000000", po.getPhoneInfo().getPhoneNumber());
        Assert.assertEquals("13580000000", po.getPhoneInfo().getPurePhoneNumber());
        Assert.assertEquals("86", po.getPhoneInfo().getCountryCode());
    }

    @Test
    public void errorPayloadCarriesNonZeroErrCodeWithoutPhoneInfo() {
        PhoneNumberRespPO po = JsonUtil.readJson("{\"errcode\":40029,\"errmsg\":\"invalid code\"}",
                PhoneNumberRespPO.class);
        Assert.assertNotNull(po);
        Assert.assertEquals(Integer.valueOf(40029), po.getErrCode());
        Assert.assertFalse(po.getErrCode() == null || po.getErrCode() == com.bidr.wechat.constant.WechatErrorCodeConst.SUCCESS);
        Assert.assertNull(po.getPhoneInfo());
    }
}
