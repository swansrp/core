package com.bidr.wechat.po.platform.user;

import com.bidr.wechat.po.WechatBaseReq;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: PublicPlatformUserReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 16:09
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PublicPlatformUserReq extends WechatBaseReq {
    @JsonProperty("openid")
    private String openId;
    private String lang = "zh_CN";
}
