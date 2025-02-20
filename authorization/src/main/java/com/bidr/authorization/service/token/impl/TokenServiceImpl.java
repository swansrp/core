package com.bidr.authorization.service.token.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.RandomUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Title: TokenServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:53
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {

    private static final String LOGIN_STATUS = "LOGIN_STATUS";
    private static final String GUEST_OPERATOR = "GUEST";
    private static final String EXPIRED_PARAM_SUFFIX = "_EXPIRED";
    private static final int TOKEN_DEFAULT_TIMEOUT = 1200;

    private final RedisService redisService;
    private final SysConfigCacheService frameCacheService;

    @Override
    public String fetchToken() {
        TokenInfo token = AuthTokenUtil.buildToken(RandomUtil.getUUID(), TokenType.GUEST_TOKEN, GUEST_OPERATOR, TOKEN_DEFAULT_TIMEOUT);
        saveToken(token, GUEST_OPERATOR, TOKEN_DEFAULT_TIMEOUT);
        return AuthTokenUtil.getToken(token);
    }

    private void saveToken(TokenInfo token, String customerNumber, int expired) {
        Date now = new Date();
        Map<String, Object> tokenMap = new HashMap<>(16);
        tokenMap.put(TokenItem.TOKEN.name(), AuthTokenUtil.getToken(token));
        tokenMap.put(TokenItem.OPERATOR.name(), customerNumber);
        tokenMap.put(TokenItem.TIMESTAMP.name(), DateUtil.formatDate(now, DateUtil.DATE_TIME_NORMAL));
        tokenMap.put(TokenItem.EXPIRED.name(), expired);
        String key = AuthTokenUtil.getKey(token);
        redisService.hashSet(key, tokenMap);
        redisService.expire(key, expired);
        redisService.zSetRemoveByScore(LOGIN_STATUS, 0, now.getTime());
        redisService.zSetAdd(LOGIN_STATUS, key, now.getTime() + expired * 1000L);
    }

    @Override
    public boolean verifyToken(TokenInfo token) {
        Validator.assertNotNull(token, ErrCodeSys.SYS_SESSION_TIME_OUT);
        boolean result = redisService.hasKey(getKey(token));
        if (result) {
            try {
                String operator = getItem(token, TokenItem.OPERATOR.name(), String.class);
                if (FuncUtil.equals(operator, GUEST_OPERATOR)) {
                    return false;
                }
                result = StringUtils.equals(token.getCustomerNumber(), operator);
            } catch (Exception e) {
                return false;
            }
        }
        return result;
    }

    @Override
    public void updateTokenValue(TokenInfo token, String key, Object obj) {
        String tokenKey = getKey(token);
        Map<String, Object> tokenMap = redisService.hashGet(tokenKey);
        Validator.assertNotNull(tokenMap, ErrCodeSys.SYS_SESSION_TIME_OUT);
        tokenMap.put(key, obj);
        int expired = Integer.parseInt(tokenMap.getOrDefault(TokenItem.EXPIRED.name(), TOKEN_DEFAULT_TIMEOUT).toString());
        tokenMap.put(TokenItem.TIMESTAMP.name(), DateUtil.formatDate(new Date(), DateUtil.DATE_TIME_NORMAL));
        redisService.hashSet(tokenKey, tokenMap);
        redisService.expire(tokenKey, expired);
    }

    @Override
    public TokenInfo buildAppAccessToken(String customerNumber) {
        boolean loginSingleton = frameCacheService.getParamSwitch(AccountParam.LOGIN_SINGLETON);
        if (loginSingleton) {
            removeTokenForLoginSingleton(customerNumber, TokenType.APP_ACCESS_TOKEN);
        }
        return buildToken(customerNumber, TokenType.APP_ACCESS_TOKEN);
    }

    private void removeTokenForLoginSingleton(String customerNumber, TokenType tokenType) {
        Date now = new Date();
        String prefixKeys = AuthTokenUtil.getPrefix(tokenType, customerNumber);
        redisService.zSetRemoveByScore(LOGIN_STATUS, 0, now.getTime());
        long count = redisService.zSetSize(LOGIN_STATUS);
        Set<String> loginTokens = redisService.zSetRange(LOGIN_STATUS, 0, count, String.class);
        List<String> tokenList = loginTokens.stream().filter(token -> token.startsWith(prefixKeys)).collect(Collectors.toList());
        log.info("用户:{}, 类型:{}, 已登录数: {} ", customerNumber, tokenType.name(), tokenList.size());
        if (CollectionUtils.isNotEmpty(tokenList)) {
            redisService.delete(tokenList);
            redisService.zSetRemoveBySet(LOGIN_STATUS, new HashSet<>(tokenList));
        }
    }

    private TokenInfo buildToken(String customerNumber, TokenType tokenType) {
        AccountParam expiredParam = getExpiredParamByTokenType(tokenType.toString());
        int expired = frameCacheService.getParamInt(expiredParam);
        TokenInfo token = AuthTokenUtil.buildToken(RandomUtil.getUUID(), tokenType, customerNumber, expired);
        saveToken(token, customerNumber, expired);
        token.setExpired(expired);
        return token;
    }

    private AccountParam getExpiredParamByTokenType(String tokenType) {
        return AccountParam.valueOf(tokenType + EXPIRED_PARAM_SUFFIX);
    }

    @Override
    public TokenInfo buildAppRefreshToken(String customerNumber) {
        boolean loginSingleton = frameCacheService.getParamSwitch(AccountParam.LOGIN_SINGLETON);
        if (loginSingleton) {
            removeTokenForLoginSingleton(customerNumber, TokenType.APP_REFRESH_TOKEN);
        }
        return buildToken(customerNumber, TokenType.APP_REFRESH_TOKEN);
    }

    @Override
    public TokenInfo buildWebAccessToken(String customerNumber) {
        boolean loginSingleton = frameCacheService.getParamSwitch(AccountParam.LOGIN_SINGLETON);
        if (loginSingleton) {
            removeTokenForLoginSingleton(customerNumber, TokenType.WEB_ACCESS_TOKEN);
        }
        return buildToken(customerNumber, TokenType.WEB_ACCESS_TOKEN);
    }

    @Override
    public TokenInfo buildWebRefreshToken(String customerNumber) {
        boolean loginSingleton = frameCacheService.getParamSwitch(AccountParam.LOGIN_SINGLETON);
        if (loginSingleton) {
            removeTokenForLoginSingleton(customerNumber, TokenType.WEB_REFRESH_TOKEN);
        }
        return buildToken(customerNumber, TokenType.WEB_REFRESH_TOKEN);
    }

    @Override
    public TokenInfo buildWechatToken(String customerNumber) {
        boolean loginSingleton = frameCacheService.getParamSwitch(AccountParam.LOGIN_SINGLETON);
        if (loginSingleton) {
            removeTokenForLoginSingleton(customerNumber, TokenType.WECHAT_TOKEN);
        }
        return buildToken(customerNumber, TokenType.WECHAT_TOKEN);
    }

    @Override
    public TokenInfo buildWxPlatformToken(String customerNumber) {
        boolean loginSingleton = frameCacheService.getParamSwitch(AccountParam.LOGIN_SINGLETON);
        if (loginSingleton) {
            removeTokenForLoginSingleton(customerNumber, TokenType.PLATFORM_TOKEN);
        }
        return buildToken(customerNumber, TokenType.PLATFORM_TOKEN);
    }

    @Override
    public TokenInfo buildIotToken(String customerNumber) {
        return buildToken(customerNumber, TokenType.IOT_TOKEN);
    }

    @Override
    public TokenInfo buildEmailToken(String userId) {
        return buildToken(userId, TokenType.CHANGE_PWD_TOKEN);
    }

    @Override
    public TokenInfo buildOpenPlatformToken(String appKey) {
        return buildToken(appKey, TokenType.PLATFORM_TOKEN);
    }

    @Override
    public <T> T getItem(TokenInfo token, String itemKey, Class<?> collectionClass, Class<?>... elementClasses) {
        Map<String, Object> map = getTokenValue(token);
        Validator.assertNotEmpty(map, ErrCodeSys.SYS_SESSION_TIME_OUT);
        Validator.assertNotEmpty(map, ErrCodeSys.PA_DATA_NOT_EXIST, "token内容");
        return JsonUtil.readJson(JsonUtil.toJson(map.get(itemKey)), collectionClass, elementClasses);
    }

    @Override
    public <T> T getItem(String itemKey, Class<?> collectionClass, Class<?>... elementClasses) {
        return getItem(getToken(), itemKey, collectionClass, elementClasses);
    }

    @Override
    public TokenInfo getToken() {
        return getAndValidateToken();
    }

    @Override
    public void setToken(TokenInfo token) {
        validateAndSetToken(token);
    }

    private void validateAndSetToken(TokenInfo token) {
        Validator.assertNotNull(token, ErrCodeSys.PA_DATA_NOT_EXIST, "token");
        TokenHolder.set(token);
    }

    @Override
    public Map<String, Object> getTokenValue(TokenInfo token) {
        return redisService.hashGet(getKey(token));
    }

    @Override
    public Map<String, Object> getTokenValue() {
        return getTokenValue(getToken());
    }

    @Override
    public void setTokenValue(Map<String, Object> map) {
        setTokenValue(getToken(), map);
    }

    @Override
    public boolean isTokenExist(TokenInfo token) {
        return redisService.hasKey(getKey(token));
    }

    @Override
    public boolean isTokenExist() {
        return isTokenExist(getToken());
    }

    @Override
    public void putItem(TokenInfo token, String itemKey, Object value) {
        Map<String, Object> map = getTokenValue(token);
        if (MapUtils.isEmpty(map)) {
            map = new HashMap<>(16);
        }
        map.put(itemKey, value);
        setTokenValue(token, map);
    }

    @Override
    public void putItem(String itemKey, Object value) {
        putItem(getToken(), itemKey, value);
    }

    @Override
    public void removeItemByToken(TokenInfo token, String... itemKey) {
        redisService.hashDel(getKey(token), itemKey);
    }

    @Override
    public void removeItem(String... itemKey) {
        TokenInfo token = getAndValidateToken();
        removeItemByToken(token, itemKey);
    }

    @Override
    public void removeToken(TokenInfo token) {
        redisService.delete(getKey(token));
    }

    @Override
    public void removeToken() {
        removeToken(getToken());
    }

    @Override
    public void setTokenValue(TokenInfo token, Map<String, Object> map) {
        int expired = Integer.parseInt(map.getOrDefault(TokenItem.EXPIRED.name(), TOKEN_DEFAULT_TIMEOUT).toString());
        String key = getKey(token);
        map.put(TokenItem.TIMESTAMP.name(), DateUtil.formatDate(new Date(), DateUtil.DATE_TIME_NORMAL));
        redisService.hashSet(key, map);
        redisService.expire(key, expired);
    }

    @Override
    public String getTokenType() {
        return getTokenType(getToken());
    }

    @Override
    public String getTokenType(TokenInfo token) {
        return token.getType().name();
    }

    @Override
    public String getCurrentUserId() {
        String userId = getItem(TokenItem.OPERATOR.name(), String.class);
        Validator.assertNotBlank(userId, ErrCodeSys.PA_DATA_NOT_EXIST, "用户id");
        return userId;
    }

    @Override
    public Boolean isLoginToken() {
        Validator.assertTrue(isTokenExist(), ErrCodeSys.SYS_SESSION_TIME_OUT);
        return FuncUtil.notEquals(getToken().getCustomerNumber(), GUEST_OPERATOR);
    }

    private TokenInfo getAndValidateToken() {
        TokenInfo token = TokenHolder.get();
        Validator.assertNotNull(token, ErrCodeSys.SYS_SESSION_TIME_OUT);
        return token;
    }

    private String getKey(TokenInfo token) {
        return AuthTokenUtil.getKey(token);
    }

}
