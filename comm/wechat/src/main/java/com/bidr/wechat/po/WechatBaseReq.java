package com.bidr.wechat.po;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: WechatBaseReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 16:06
 * @description Project Name: Seed
 * @Package: com.srct.service.wechat.po.platform
 */
@Data
public class WechatBaseReq {
    @JsonProperty("access_token")
    private String accessToken;
}
