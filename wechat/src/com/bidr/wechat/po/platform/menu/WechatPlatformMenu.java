package com.bidr.wechat.po.platform.menu;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: WechatPlatformMenu
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/5 15:57
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WechatPlatformMenu {
    private String name;
    private String type;
    private String key;
    private String url;
    @JsonProperty("media_id")
    private String mediaId;
    @JsonProperty("appid")
    private String appId;
    @JsonProperty("pagepath")
    private String pagePath;
    @JsonProperty("sub_button")
    private List<WechatPlatformMenu> subButton;
}
