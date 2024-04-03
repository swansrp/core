package com.bidr.wechat.po.auth;

import com.bidr.wechat.po.WechatBaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: JsApiTicketReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/19 18:13
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class JsApiTicketReq extends WechatBaseReq {
    private String type = "jsapi";
}
