package com.bidr.wechat.vo.auth;

import lombok.Data;

/**
 * Title: AuthUrlReq
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/7 14:03
 */
@Data
public class AuthUrlReq {
    private String redirectUrl;
    private String state;
}
