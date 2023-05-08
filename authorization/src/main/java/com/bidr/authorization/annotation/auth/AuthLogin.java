package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.account.UserInfo;
import com.bidr.authorization.bo.permit.PermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.bidr.kernel.constant.err.ErrCodeSys.SYS_SESSION_TIME_OUT;

/**
 * Title: AuthLogin
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/8/23 14:17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthLogin implements AuthRole {
    private final TokenService tokenService;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        TokenInfo token = AuthTokenUtil.extractToken(request);
        Validator.assertNotNull(token, SYS_SESSION_TIME_OUT);
        tokenService.verifyToken(token);
        buildContext(token);
    }

    public void buildContext(TokenInfo token) {
        TokenHolder.set(token);
        Validator.assertTrue(tokenService.isLoginToken(), SYS_SESSION_TIME_OUT);
        Map<String, Object> map = tokenService.getTokenValue(token);
        UserInfo userInfo = buildUserInfo(map);
        Map<Long, RoleInfo> roleInfoMap = JsonUtil.readJson(map.get(TokenItem.ROLE_MAP.name()), Map.class, Long.class,
                RoleInfo.class);
        List<PermitInfo> permitInfoList = JsonUtil.readJson(map.get(TokenItem.PERMIT_LIST.name()), List.class,
                PermitInfo.class);
        userInfo.setRoleInfoMap(roleInfoMap);
        userInfo.setPermitInfoList(permitInfoList);
        AccountInfo accountInfo = buildAccountInfo(userInfo, map);
        AccountContext.set(accountInfo);
    }

    private UserInfo buildUserInfo(Map<String, Object> map) {
        UserInfo userInfo = new UserInfo();
        if (map.get(TokenItem.NICK_NAME.name()) != null) {
            userInfo.setName(String.valueOf(map.get(TokenItem.NICK_NAME.name())));
        }
        if (map.get(TokenItem.OPERATOR.name()) != null) {
            userInfo.setCustomerNumber(String.valueOf(map.get(TokenItem.OPERATOR.name())));
        }
        if (map.get(TokenItem.USER_NAME.name()) != null) {
            userInfo.setUserName(String.valueOf(map.get(TokenItem.USER_NAME.name())));
        }
        if (map.get(TokenItem.PHONE_NUMBER.name()) != null) {
            userInfo.setPhoneNumber(String.valueOf(map.get(TokenItem.PHONE_NUMBER.name())));
        }
        if (map.get(TokenItem.EMAIL.name()) != null) {
            userInfo.setEmail(String.valueOf(map.get(TokenItem.EMAIL.name())));
        }
        return userInfo;
    }

    private AccountInfo buildAccountInfo(UserInfo userInfo, Map<String, Object> map) {
        AccountInfo accountInfo = ReflectionUtil.copy(userInfo, AccountInfo.class);
        if (map.get(TokenItem.TOKEN.name()) != null) {
            accountInfo.setToken(String.valueOf(map.get(TokenItem.TOKEN.name())));
        }
        if (map.get(TokenItem.CLIENT_TYPE.name()) != null) {
            accountInfo.setClientType(String.valueOf(map.get(TokenItem.CLIENT_TYPE.name())));
        }
        accountInfo.setExtraData(map);
        return accountInfo;
    }


}
