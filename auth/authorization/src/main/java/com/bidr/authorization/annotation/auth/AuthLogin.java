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
import com.bidr.authorization.holder.TenantContext;
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
import java.util.*;

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
        // 租户档接线（棒2）：token 缺 TENANT_IDS（老项目/老 token）⇒ null＝旁路形态，数据闸放行；
        // 空集合＝无归属，按主线程裁定同样旁路。平台档由 AuthAdmin 通过路径另行置位。
        TenantContext.set(accountInfo.getTenantIds());
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
        accountInfo.setTenantIds(parseTenantIds(map));
        accountInfo.setExtraData(map);
        return accountInfo;
    }

    /**
     * TENANT_IDS 解析收口（登录解析唯一入口 buildAccountInfo 调用；包可见静态供离线单测直断）。
     * <p>三态口径：缺键/null ⇒ {@code null}（老项目、老 token、dev 冒烟 map 均落此态＝旁路形态，
     * 绝不抛错）；空集合 ⇒ 空 List（用户无租户归属）；有值 ⇒ 逐项转 Long，
     * 脏元素（非数字/坏 JSON 串）按缺键旁路或跳过——token 经 redis 回读，
     * List 实形与 JSON 串形（{@code "[1,2]"}）都必须兼容。</p>
     * <p>public＝解析收口唯一入口，token 回查面（SsoTokenIntrospectService）透出租户集复用同一解析。</p>
     */
    public static List<Long> parseTenantIds(Map<String, Object> map) {
        Object raw = map == null ? null : map.get(TokenItem.TENANT_IDS.name());
        if (raw instanceof String) {
            raw = JsonUtil.readJson(((String) raw).trim(), List.class, Long.class);
        }
        if (!(raw instanceof Collection)) {
            return null;
        }
        List<Long> tenantIds = new ArrayList<>();
        for (Object item : (Collection<?>) raw) {
            if (item instanceof Number) {
                tenantIds.add(((Number) item).longValue());
            } else if (item != null) {
                try {
                    tenantIds.add(Long.parseLong(String.valueOf(item).trim()));
                } catch (NumberFormatException ignored) {
                    // 脏元素跳过：解析宽度问题不应打断登录态构建
                }
            }
        }
        return tenantIds;
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
