/**
 * Title: PhoneNumberRespPO.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @Project Name: SpringBootCommonLib
 * @Package: com.bidr.wechat.po.miniprogram
 * @author Sharp
 * @date 2026-10-19
 */
package com.bidr.wechat.po.miniprogram;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 微信小程序 getuserphonenumber 接口响应
 *
 * @author Sharp
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PhoneNumberRespPO extends WechatBaseRes {
    @JsonProperty("phone_info")
    private PhoneInfo phoneInfo;

    /**
     * 用户绑定的手机号信息
     */
    @Data
    public static class PhoneInfo {
        private String phoneNumber;
        private String purePhoneNumber;
        private String countryCode;
    }
}
