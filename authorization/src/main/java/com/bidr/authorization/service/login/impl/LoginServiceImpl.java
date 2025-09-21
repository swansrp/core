package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.bo.account.UserInfo;
import com.bidr.authorization.bo.account.UserPermitInfo;
import com.bidr.authorization.bo.account.UserRolePermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.login.QrCodeReq;
import com.bidr.authorization.vo.login.SsoLoginReq;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Map;

/**
 * Title: LoginServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:51
 */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final AcUserService acUserService;
    private final SysConfigCacheService frameCacheService;
    private final TokenService tokenService;
    private final AcUserRoleMenuService acUserRoleMenuService;
    private final CreateUserService creatUserService;
    private final HttpServletRequest request;

    @Override
    public LoginRes login(AcUser user) {
        Validator.assertTrue(FuncUtil.equals(user.getStatus(), ActiveStatusDict.ACTIVATE.getValue()),
                AccountErrCode.AC_LOCK);
        return buildLoginRes(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = ServiceException.class)
    public LoginRes login(String loginId, String password) {
        AcUser user = acUserService.getUserByUserName(loginId);
        if (user == null) {
            user = acUserService.getUserByPhoneNumber(loginId);
        }
        if (user == null) {
            user = acUserService.getUserByEmail(loginId);
        }
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        Validator.assertTrue(FuncUtil.equals(user.getStatus(), ActiveStatusDict.ACTIVATE.getValue()),
                AccountErrCode.AC_LOCK);
        verifyPassword(user, user.getPassword(), password);
        return login(user);
    }

    private void verifyPassword(AcUser user, String passwordMd5, String password) {
        Validator.assertNotBlank(user.getPassword(), AccountErrCode.AC_PASSWORD_NOT_EXISTED);
        boolean verify = false;
        try {
            verify = Md5Util.verify(password, passwordMd5);
        } catch (Exception e) {
            Validator.assertException(AccountErrCode.AC_PASSWORD_LOGIN_FORMAT);
        }
        if (!verify) {
            int passwordMistakeMaxTime = frameCacheService.getParamInt(AccountParam.ACCOUNT_LOCK_MISTAKE_NUMBER);
            user.setPasswordErrorTime(user.getPasswordErrorTime() + 1);
            if (user.getPasswordErrorTime() >= passwordMistakeMaxTime) {
                user.setStatus(ActiveStatusDict.LOCKING.getValue());
                acUserService.updateById(user);
                Validator.assertException(AccountErrCode.AC_PASSWORD_MAX_ERROR_TIMES);
            }
            acUserService.updateById(user);
            Validator.assertException(AccountErrCode.AC_PASSWORD_NOT_RIGHT,
                    String.valueOf(passwordMistakeMaxTime - user.getPasswordErrorTime()));
        } else {
            user.setPasswordErrorTime(0);
        }

    }

    @Override
    public LoginRes loginOrReg(String phoneNumber) {
        AcUser user = acUserService.getUserByPhoneNumber(phoneNumber);
        if (FuncUtil.isEmpty(user)) {
            user = creatUserService.createUserWithName(phoneNumber, phoneNumber);
        }
        return buildLoginRes(user);
    }

    @Override
    public LoginRes loginOrRegByWechatMiniOpenId(String wechatOpenId) {
        return loginOrRegByWechatPhoneNumber(wechatOpenId, null, null, null);
    }

    @Override
    public LoginRes loginOrRegByWechatUnionId(String wechatUnionId, String nickName, String avatar) {
        return loginOrRegByWechatPhoneNumber(wechatUnionId, nickName, null, avatar);
    }

    @Override
    public LoginRes loginOrRegByWechatPhoneNumber(String wechatId, String nickName, String phoneNumber, String avatar) {
        AcUser wechatUser = acUserService.getUserByWechatId(wechatId);
        AcUser phoneNumberUser = acUserService.getUserByPhoneNumber(phoneNumber);
        if (FuncUtil.isEmpty(wechatUser)) {
            if (FuncUtil.isNotEmpty(phoneNumberUser)) {
                wechatUser = creatUserService.mergeWechatPhoneNumber(wechatId, nickName, phoneNumberUser, avatar);
            } else {
                wechatUser = creatUserService.createUserFromWechat(wechatId, nickName, phoneNumber, avatar);
            }
        } else {
            if (FuncUtil.isNotEmpty(phoneNumberUser)) {
                wechatUser = creatUserService.mergeWechatPhoneNumber(wechatId, nickName, phoneNumberUser, avatar);
            } else {
                wechatUser.setCustomerNumber(phoneNumber);
                wechatUser.setUserName(phoneNumber);
                wechatUser.setPhoneNumber(phoneNumber);
            }
        }
        return buildLoginRes(wechatUser);
    }

    @Override
    public LoginRes refreshLogin(String refreshToken) {
        TokenInfo tokenInfo = AuthTokenUtil.decode(refreshToken);
        Validator.assertTrue(tokenService.isTokenExist(tokenInfo), ErrCodeSys.SYS_SESSION_TIME_OUT);
        String customerNumber = tokenService.getItem(tokenInfo, TokenItem.OPERATOR.name(), String.class);
        Validator.assertNotBlank(customerNumber, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        Validator.assertNotEquals(user.getStatus(), ActiveStatusDict.LOCKING.getValue(), AccountErrCode.AC_LOCK);
        tokenService.removeToken(tokenInfo);
        return buildLoginRes(user);
    }

    @Override
    public LoginRes register(String userName, String password) {
        return this.registerWithEmail(userName, password, null);
    }

    @Override
    public LoginRes registerWithEmail(String userName, String password, String email) {
        AcUser user = acUserService.getUserByUserName(userName);
        Validator.assertNull(user, AccountErrCode.AC_USER_ALREADY_EXISTED);
        user = creatUserService.createUser(userName, password, userName, null, email, null);
        return buildLoginRes(user);
    }

    @Override
    public LoginRes registerWithPhoneNumber(String userName, String password, String phoneNumber) {
        AcUser user = acUserService.getUserByUserName(userName);
        Validator.assertNull(user, AccountErrCode.AC_USER_ALREADY_EXISTED);
        user = creatUserService.createUser(userName, password, userName, phoneNumber, null, null);
        return buildLoginRes(user);
    }

    @Override
    public LoginRes ghostLogin(String customerNumber) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        return login(user);
    }

    @Override
    public LoginRes loginByQrCode(QrCodeReq qrCodeReq) {
        return null;
    }

    @Override
    public void emailRegister(String id, String email) {

    }

    @Override
    public LoginRes ssoLoginOrReg(SsoLoginReq ssoLoginReq) {
        return null;
    }

    @Override
    public LoginRes loginOrReg(AcAccount account) {
        AcUser user = acUserService.getByCustomerNumber(account.getId());
        if (FuncUtil.isEmpty(user)) {
            user = creatUserService.createUser(account);
        }
        return buildLoginRes(user);
    }

    @Override
    public void logoff() {
        tokenService.removeToken();
        tokenService.verifyToken(null);
    }

    private LoginRes buildLoginRes(AcUser user) {
        recordLogin(user);
        LoginRes res = ReflectionUtil.copy(user, LoginRes.class);
        fillToken(res, user);
        return res;
    }

    private void recordLogin(AcUser user) {
        user.setLoginDate(new Date());
        user.setLoginIp(HttpUtil.getRemoteIp(request));
        acUserService.updateById(user);
    }

    private void fillToken(LoginRes res, AcUser acUser) {
        TokenInfo accessToken = buildAccessToken(acUser.getUserId(), acUser.getCustomerNumber(), acUser.getName());
        TokenInfo refreshToken = buildRefreshToken(acUser.getCustomerNumber());
        res.setAccessToken(AuthTokenUtil.getToken(accessToken));
        if (FuncUtil.isNotEmpty(refreshToken)) {
            // 微信相关不存在refreshToken
            res.setRefreshToken(AuthTokenUtil.getToken(refreshToken));
        }
        String clientType = ClientTypeHolder.get();
        UserRolePermitInfo info = acUserRoleMenuService.getByCustomerNumberAndClientType(acUser.getCustomerNumber(),
                clientType);
        buildPermitTree(info, accessToken);
        buildAccountInfo(info, accessToken);
        TokenHolder.set(accessToken);
    }

    private TokenInfo buildAccessToken(Long userId, String customerNumber, String nickName) {
        TokenInfo accessToken = null;
        String clientType = ClientTypeHolder.get();
        if (FuncUtil.equals(clientType, ClientType.WEB.getValue())) {
            accessToken = tokenService.buildWebAccessToken(customerNumber);
        } else if (FuncUtil.equals(clientType, ClientType.APP.getValue())) {
            accessToken = tokenService.buildAppAccessToken(customerNumber);
        } else if (FuncUtil.equals(clientType, ClientType.WECHAT.getValue())) {
            accessToken = tokenService.buildWechatToken(customerNumber);
        } else if (FuncUtil.equals(clientType, ClientType.PUBLIC.getValue())) {
            accessToken = tokenService.buildWxPlatformToken(customerNumber);
        }
        tokenService.putItem(accessToken, TokenItem.USER_ID.name(), userId);
        tokenService.putItem(accessToken, TokenItem.OPERATOR.name(), customerNumber);
        tokenService.putItem(accessToken, TokenItem.NICK_NAME.name(), nickName);
        return accessToken;
    }

    private TokenInfo buildRefreshToken(String customerNumber) {
        TokenInfo refreshToken = null;
        String clientType = ClientTypeHolder.get();
        if (clientType.equals(ClientType.APP.getValue())) {
            refreshToken = tokenService.buildAppRefreshToken(customerNumber);
        } else if (clientType.equals(ClientType.WEB.getValue())) {
            refreshToken = tokenService.buildWebRefreshToken(customerNumber);
        }
        return refreshToken;
    }

    private void buildPermitTree(UserPermitInfo info, TokenInfo accessToken) {
        if (FuncUtil.isNotEmpty(info.getMenuList())) {
            tokenService.putItem(accessToken, TokenItem.PERMIT_TREE.name(), info.getMenuList());
        }
    }

    private void buildAccountInfo(UserRolePermitInfo info, TokenInfo accessToken) {
        Map<Long, RoleInfo> role = ReflectionUtil.reflectToMap(info.getRoleInfoList(), RoleInfo::getRoleId);
        tokenService.putItem(accessToken, TokenItem.USER_INFO.name(), ReflectionUtil.copy(info, UserInfo.class));
        tokenService.putItem(accessToken, TokenItem.ROLE_MAP.name(), role);
    }
}
