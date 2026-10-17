package com.bidr.wechat.po.auth;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: AuthUserInfoRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/19 11:45
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AuthUserInfoRes extends WechatBaseRes {
    @JsonProperty("openid")
    private String openId;
    @JsonProperty("nickname")
    private String nickName;
    private String sex;
    private String province;
    private String city;
    private String country;
    @JsonProperty("headimgurl")
    private String headImgUrl;
    private List<String> privilege;
    @JsonProperty("unionid")
    private String unionId;
}
