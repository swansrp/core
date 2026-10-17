package com.bidr.wechat.po.ocr;

import com.bidr.wechat.po.WechatBaseReq;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: IdCardOcrReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/15 15:01
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IdCardOcrReq extends WechatBaseReq {
    @JsonProperty("img_url")
    private String idCardImgUrl;
}
