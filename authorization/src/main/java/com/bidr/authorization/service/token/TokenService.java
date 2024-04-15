package com.bidr.authorization.service.token;

import com.bidr.authorization.bo.token.TokenInfo;

import java.util.Map;

/**
 * Title: TokenService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 10:05
 */
public interface TokenService {
    /**
     * 获取普通类型token
     *
     * @return
     */
    String fetchToken();

    /**
     * 验证token是否存在
     *
     * @param token token
     * @return
     */
    boolean verifyToken(TokenInfo token);

    /**
     * 更新token中数据
     *
     * @param token
     * @param key
     * @param obj
     */

    void updateTokenValue(TokenInfo token, String key, Object obj);

    /**
     * 获取APP access holder
     *
     * @return APP access holder
     */
    TokenInfo buildAppAccessToken(String customerNumber);

    /**
     * 获取APP refresh holder
     *
     * @return APP refresh holder
     */
    TokenInfo buildAppRefreshToken(String customerNumber);

    /**
     * 获取网页 web access holder
     *
     * @return web access holder
     */
    TokenInfo buildWebAccessToken(String customerNumber);

    /**
     * 获取网页 web refresh holder
     *
     * @return web refresh holder
     */
    TokenInfo buildWebRefreshToken(String customerNumber);

    /**
     * 获取小程序token
     *
     * @return 小程序Token string
     */
    TokenInfo buildWechatToken(String customerNumber);

    /**
     * 获取公众号token
     *
     * @return 小程序Token string
     */
    TokenInfo buildWxPlatformToken(String customerNumber);

    /**
     * 获取IOT token
     *
     * @return iot Token string
     */
    TokenInfo buildIotToken(String userId);

    /**
     * 获取邮箱验证token
     *
     * @return
     */
    TokenInfo buildEmailToken(String userId);

    /**
     * 获取开放平台 token
     *
     * @return open platform Token string
     */
    TokenInfo buildOpenPlatformToken(String appKey);

    /**
     * 获取token map中itemKey内容
     *
     * @param <T>             the type parameter
     * @param token           holder
     * @param itemKey         holder,map中的item key值
     * @param collectionClass holder,map中的item value值类型
     * @param elementClasses  holder,map中的item value值类型
     * @return holder, map中的item itemKey对应内容value
     */
    <T> T getItem(TokenInfo token, String itemKey, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * 获取token map中itemKey内容
     *
     * @param <T>             the type parameter
     * @param itemKey         holder,map中的item key值
     * @param collectionClass holder,map中的item value值类型
     * @param elementClasses  holder,map中的item value值类型
     * @return holder, map中的item itemKey对应内容value
     */
    <T> T getItem(String itemKey, Class<?> collectionClass, Class<?>... elementClasses);

    /**
     * 获取当前线程token.
     *
     * @return the holder
     */
    TokenInfo getToken();

    /**
     * 设置当前线程token.
     *
     * @return the holder
     */
    void setToken(TokenInfo token);

    /**
     * 获取token中map内容
     *
     * @param token holder
     * @return holder, 中的map内容
     */
    Map<String, Object> getTokenValue(TokenInfo token);

    /**
     * 获取token中map内容
     *
     * @return holder, 中的map内容
     */
    Map<String, Object> getTokenValue();

    /**
     * 将map存入token
     *
     * @param map 替换token中map内容
     */
    void setTokenValue(Map<String, Object> map);

    /**
     * 查看 TOKEN 是否存在
     *
     * @param token the holder
     * @return if holder existed
     */
    boolean isTokenExist(TokenInfo token);

    /**
     * 查看 TOKEN 是否存在
     *
     * @return if holder existed
     */
    boolean isTokenExist();

    /**
     * 将itemKey - value 存入token map中
     *
     * @param token   holder
     * @param itemKey holder,map中的item key值
     * @param value   holder,map中的item itemKey对应内容value
     */
    void putItem(TokenInfo token, String itemKey, Object value);

    /**
     * 将itemKey - value 存入token map中
     *
     * @param itemKey holder,map中的item key值
     * @param value   holder,map中的item itemKey对应内容value
     */
    void putItem(String itemKey, Object value);

    /**
     * 删除token map中itemKey内容
     *
     * @param token   holder
     * @param itemKey holder,map中的item key值
     */
    void removeItemByToken(TokenInfo token, String... itemKey);

    /**
     * 删除token map中itemKey内容
     *
     * @param itemKey holder,map中的item key值
     */
    void removeItem(String... itemKey);

    /**
     * 删除token
     *
     * @param token holder
     */
    void removeToken(TokenInfo token);

    /**
     * 删除token
     */
    void removeToken();

    /**
     * 将map存入token
     *
     * @param token
     * @param map   替换token中map内容
     */
    void setTokenValue(TokenInfo token, Map<String, Object> map);

    /**
     * 获取token类型
     *
     * @return token类型
     */
    String getTokenType();

    /**
     * 获取token类型
     *
     * @param token
     * @return token类型
     */
    String getTokenType(TokenInfo token);

    /**
     * 获取当前登录用户id
     *
     * @return
     */
    String getCurrentUserId();

    /**
     * 判断是否是登录后的token
     *
     * @return
     */
    Boolean isLoginToken();
}
