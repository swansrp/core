/**
 * Title: LoginRes.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @description Project Name: Grote
 * @Package: com.srct.service.account.vo
 * @since 2019-7-30 18:19
 */
package com.bidr.authorization.vo.login;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class LoginRes {
    @JsonIgnore
    private Long userId;
    private String customerNumber;
    private String name;
    private String avtar;
    private String accessToken;
    private String refreshToken;
}
