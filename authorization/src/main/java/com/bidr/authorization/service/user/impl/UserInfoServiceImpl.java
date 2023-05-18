package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.user.UserInfoService;
import com.bidr.authorization.vo.user.UserInfoRes;
import com.bidr.kernel.config.response.Resp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Title: UserInfoServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/18 09:04
 */
@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl implements UserInfoService {

    private final AcUserService acUserService;

    @Override
    public UserInfoRes getUserInfo() {
        Long userId = AccountContext.getUserId();
        AcUser user = acUserService.getById(userId);
        return Resp.convert(user, UserInfoRes.class);
    }
}
