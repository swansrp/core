/**
 * Title: LoginRes.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-7-30 18:19
 * @description Project Name: Grote
 * @Package: com.srct.service.account.vo
 */
package com.bidr.authorization.vo.login;

import lombok.Data;

@Data
public class LoginRes {
    private String customerNumber;
    private String name;
    private String accessToken;
    private String refreshToken;
}
