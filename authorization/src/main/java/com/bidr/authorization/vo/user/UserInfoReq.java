package com.bidr.authorization.vo.user;

import lombok.Builder;
import lombok.Data;

/**
 * Title: UserInfoReq
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/12/27 23:55
 */
@Data
@Builder
public class UserInfoReq {
    private String name;
    private String phoneNumber;
    private String nickName;
    private String avatar;
    private String email;
    private String sex;
    private String customerNumber;
}
