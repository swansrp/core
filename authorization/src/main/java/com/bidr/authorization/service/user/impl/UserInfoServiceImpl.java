package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.user.UserInfoService;
import com.bidr.authorization.vo.user.UserExistedReq;
import com.bidr.authorization.vo.user.UserInfoRes;
import com.bidr.authorization.vo.user.UserRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Override
    public UserRes userExisted(UserExistedReq req) {
        List<AcUser> user = acUserService.existedUser(req);
        Validator.assertNotEmpty(user, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        return Resp.convert(user.get(0), UserRes.class);
    }
}
