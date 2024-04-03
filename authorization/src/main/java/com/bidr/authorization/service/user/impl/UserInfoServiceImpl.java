package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.UserInfoService;
import com.bidr.authorization.vo.user.RealNameReq;
import com.bidr.authorization.vo.user.UserExistedReq;
import com.bidr.authorization.vo.user.UserInfoRes;
import com.bidr.authorization.vo.user.UserRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TokenService tokenService;

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void realName(RealNameReq req) {
        String customerNumber = tokenService.getItem(TokenItem.OPERATOR.name(), String.class);
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        Validator.assertTrue(StringUtils.isBlank(user.getIdNumber()), ErrCodeSys.SYS_ERR_MSG, "已实名");
        user.setIdNumber(req.getId());
        user.setName(req.getName());
        acUserService.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAvatar(String avatar) {
        String customerNumber = tokenService.getItem(TokenItem.OPERATOR.name(), String.class);
        setAvatar(customerNumber, avatar);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setAvatar(String customerNumber, String avatar) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        user.setAvatar(avatar);
        acUserService.updateById(user);
    }
}
