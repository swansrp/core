/**
 * Title: OpenIdRespPO.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.po.wechat
 * @author Sharp
 * @date 2019-01-30 21:29:09
 */
package com.bidr.wechat.po.miniprogram;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Sharp
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenIdRespPO extends WechatBaseRes {
    private String openid;
    @JsonProperty("session_key")
    private String sessionKey;
    @JsonProperty("unionid")
    private String unionId;

}
