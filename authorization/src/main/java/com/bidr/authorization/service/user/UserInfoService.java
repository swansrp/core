package com.bidr.authorization.service.user;

import com.bidr.authorization.vo.user.UserInfoRes;

/**
 * Title: UserInfoService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 09:03
 */
public interface UserInfoService {
    /**
     * 获取当前登录用户的用户信息
     *
     * @return
     */
    UserInfoRes getUserInfo();
}
