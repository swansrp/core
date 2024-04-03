package com.bidr.wechat.po.auth;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: JsApiTicketRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/4/29 17:40
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JsApiTicketRes extends WechatBaseRes {
    private String ticket;
    @JsonProperty("expires_in")
    private Integer expiresIn;
}
