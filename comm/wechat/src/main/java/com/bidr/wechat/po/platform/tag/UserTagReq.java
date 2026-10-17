package com.bidr.wechat.po.platform.tag;

import com.bidr.wechat.po.WechatBaseReq;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Title: UserTagReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/17 11:43
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserTagReq extends WechatBaseReq {
    @JsonProperty("openid_list")
    private List<String> openIdList;
    @JsonProperty("tagig")
    private Integer tagId;
}
