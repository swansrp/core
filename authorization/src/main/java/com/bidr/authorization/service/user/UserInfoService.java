package com.bidr.authorization.service.user;

import com.bidr.authorization.vo.user.UserExistedReq;
import com.bidr.authorization.vo.user.UserInfoRes;
import com.bidr.authorization.vo.user.UserRes;

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
     * @return 当前用户详细信息
     */
    UserInfoRes getUserInfo();

    /**
     * 用户是否存在
     *
     * @param req 请求
     * @return 用户信息
     */
    UserRes userExisted(UserExistedReq req);
}
