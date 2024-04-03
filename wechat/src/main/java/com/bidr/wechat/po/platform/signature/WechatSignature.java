package com.bidr.wechat.po.platform.signature;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: Signature
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/1/22 23:12
 * @description Project Name: Mall
 * @Package: com.srct.service.wechat.po.platform.signature
 */
@Data
public class WechatSignature {
    @ApiModelProperty("微信加密签名")
    private String signature;
    @ApiModelProperty("时间戳")
    private String timestamp;
    @ApiModelProperty("随机数")
    private String nonce;
    @ApiModelProperty("随机字符串")
    private String echostr;
}
