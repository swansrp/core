package com.bidr.wechat.vo.auth;

import lombok.Data;

/**
 * Title: AuthRes
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/6/19 11:04
 */
@Data
public class AuthRes {
    private String token;
    private String nickName;
    private String userId;
    private String headImgUrl;
}
