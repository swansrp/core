package com.bidr.wechat.po.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: JsApiSignature
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/4/29 17:47
 */
@Data
public class JsApiSignature {
    @JsonProperty("jsapi_ticket")
    private String jsApiTicket;
    @JsonProperty("noncestr")
    private String nonceStr;
    private String timestamp;
    private String url;
    private String signatureStr;
}
