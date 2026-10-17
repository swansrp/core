package com.bidr.wechat.po.platform.user;

import com.bidr.wechat.po.WechatBaseReq;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryWechatPlatFormUserListReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 20:37
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryWechatPlatFormUserListReq extends WechatBaseReq {
    @JsonProperty("next_openid")
    private String nextOpenId;
}
