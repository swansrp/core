package com.bidr.wechat.vo.login;

import lombok.Data;

/**
 * Title: WechatMiniLoginReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/7/10 21:20
 */
@Data
public class WechatMiniLoginReq {
    private String encryptedData;
    private String iv;
}
