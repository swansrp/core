package com.bidr.wechat.po.platform.menu;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: CreateWechatPlatformConditionalMenuRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/17 16:51
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreateWechatPlatformConditionalMenuRes extends WechatBaseRes {
    @JsonProperty("menuid")
    private String menuId;
}
