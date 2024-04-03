package com.bidr.wechat.vo.auth;

import lombok.Data;

/**
 * Title: AuthRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/4/29 18:04

 */
@Data
public class AuthJdkApiRes {
    private String appId;
    private String nonceStr;
    private String timestamp;
    private String signature;
}
