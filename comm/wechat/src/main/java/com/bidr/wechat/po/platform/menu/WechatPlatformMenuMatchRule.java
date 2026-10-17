package com.bidr.wechat.po.platform.menu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: WechatPlatformMenuMatchRule
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/15 18:14
 */
@Data
public class WechatPlatformMenuMatchRule {
    @JsonProperty("tag_id")
    private Integer tagId;
    private String sex;
    @JsonProperty("client_platform_type")
    private String clientPlatformType;
    private String country;
    private String province;
    private String city;
    private String language;
}
