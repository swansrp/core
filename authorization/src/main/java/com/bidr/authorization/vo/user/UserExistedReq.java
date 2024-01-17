package com.bidr.authorization.vo.user;

import lombok.Data;

/**
 * Title: UserExistedReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/16 09:31
 */
@Data
public class UserExistedReq {
    private String phoneNumber;
    private String email;
    private String userName;
}
