/**
 * Title: WechatPlatformAccessTokenRes
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 * @date 2019-5-20 22:33
 * @description Project Name: Tanya
 * Package: com.srct.service.wechat.po.platform
 */
package com.bidr.wechat.po.platform;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class WechatPlatformAccessTokenRes {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("expires_in")
    private Integer expiresIn;
}
