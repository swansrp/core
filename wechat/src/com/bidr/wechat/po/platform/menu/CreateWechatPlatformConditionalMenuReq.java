package com.bidr.wechat.po.platform.menu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: WechatPlatformConditionMenuRefreshReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/15 18:12
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateWechatPlatformConditionalMenuReq extends CreateWechatPlatformMenuReq {
    @JsonProperty("matchrule")
    private WechatPlatformMenuMatchRule matchRule;
}
