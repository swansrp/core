package com.bidr.wechat.po.auth;

import com.bidr.wechat.po.WechatBaseReq;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: AuthUserInfoReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/19 11:40
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthUserInfoReq extends WechatBaseReq {
    @JsonProperty("openid")
    private String openId;
    private String lang;
}
