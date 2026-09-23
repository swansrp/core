package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.account.UserInfo;
import com.bidr.authorization.bo.permit.PermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.bidr.kernel.constant.err.ErrCodeSys.SYS_SESSION_TIME_OUT;

/**
 * Title: AuthLogin
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/8/23 14:17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthLogin implements AuthRole {

    /**
     * dev 冒烟测试固定令牌：请求头 Token 直接传该值即以系统管理员身份通过鉴权；
     * 仅 dev profile 生效，pre/prod 不受影响（见 buildDevAdminContext）
     */
    public static final String DEV_ADMIN_TOKEN = "QVJkYWVybnFTUi1BMlluMHdsN3pDZzowOjAwMDAwMQ==";

    private final TokenService tokenService;
    private final SysConfigCacheService sysConfigCacheService;
    @Value("${my.login.ignore:false}")
    private Boolean ignore;
    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        if (ignore) {
            return;
        }
        if (buildDevAdminContext(request)) {
            return;
        }
        TokenInfo token = AuthTokenUtil.extractToken(request);
        Validator.assertNotNull(token, SYS_SESSION_TIME_OUT);
        Validator.assertTrue(tokenService.verifyToken(token), SYS_SESSION_TIME_OUT);
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
        if (map.get(TokenItem.USER_ID.name()) != null) {
            userInfo.setUserId(Long.parseLong(String.valueOf(map.get(TokenItem.USER_ID.name()))));
        }
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

    /**
     * dev 冒烟免登：dev profile 下请求头 Token 为固定令牌时，不走 redis 校验，
     * 直接按系统初始化内置管理员（ac_user user_id=1 / BidrAdmin / 客户号 000001）构建上下文，
     * 并注入系统管理员角色（AuthAdmin 依赖的校验连带通过）；非 dev 环境恒返回 false
     */
    private boolean buildDevAdminContext(HttpServletRequest request) {
        if (!isDevProfile()) {
            return false;
        }
        String header = request.getHeader(RequestConst.TOKEN);
        if (header == null || header.isEmpty()) {
            return false;
        }
        // 兼容 "Bearer <token>" 前缀与裸 token 两种写法，统一取末段比对
        String[] parts = header.split(" ");
        if (!DEV_ADMIN_TOKEN.equals(parts[parts.length - 1])) {
            return false;
        }
        TokenInfo tokenInfo = AuthTokenUtil.resolveToken(header);
        String customerNumber = tokenInfo == null ? "000001" : tokenInfo.getCustomerNumber();
        Map<String, Object> map = new HashMap<>();
        map.put(TokenItem.TOKEN.name(), DEV_ADMIN_TOKEN);
        map.put(TokenItem.USER_ID.name(), "1");
        map.put(TokenItem.OPERATOR.name(), customerNumber);
        map.put(TokenItem.USER_NAME.name(), "BidrAdmin");
        map.put(TokenItem.NICK_NAME.name(), "系统管理员");
        UserInfo userInfo = buildUserInfo(map);
        userInfo.setRoleInfoMap(buildAdminRoleMap());
        AccountContext.set(buildAccountInfo(userInfo, map));
        TokenHolder.set(tokenInfo != null ? tokenInfo
                : AuthTokenUtil.buildToken(DEV_ADMIN_TOKEN, TokenType.WEB_ACCESS_TOKEN, customerNumber, 0));
        log.warn("dev 冒烟令牌生效，已以系统管理员（BidrAdmin）身份构建上下文");
        return true;
    }

    private boolean isDevProfile() {
        for (String profile : activeProfiles.split(",")) {
            if ("dev".equals(profile.trim())) {
                return true;
            }
        }
        return false;
    }

    private Map<Long, RoleInfo> buildAdminRoleMap() {
        RoleInfo roleInfo = new RoleInfo();
        roleInfo.setRoleId(sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID));
        roleInfo.setRoleName("系统管理员");
        Map<Long, RoleInfo> roleInfoMap = new HashMap<>();
        roleInfoMap.put(roleInfo.getRoleId(), roleInfo);
        return roleInfoMap;
    }


}
