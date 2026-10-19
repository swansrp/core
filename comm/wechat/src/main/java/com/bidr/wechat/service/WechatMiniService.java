/**
 * Title: WechatServiceImpl.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.service.impl
 * @author Sharp
 * @date 2019-01-30 21:39:12
 */
package com.bidr.wechat.service;

import com.alibaba.excel.util.StringUtils;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.platform.service.rest.RestService;
import com.bidr.wechat.bo.OpenIdBO;
import com.bidr.wechat.constant.WechatErrorCodeConst;
import com.bidr.wechat.constant.WechatParamConst;
import com.bidr.wechat.constant.WechatRedisKeyConst;
import com.bidr.wechat.constant.WechatTokenItem;
import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.miniprogram.OpenIdRespPO;
import com.bidr.wechat.po.miniprogram.PhoneNumberRespPO;
import com.bidr.wechat.po.miniprogram.WxMiniUserInfo;
import com.bidr.wechat.po.platform.WechatPlatformAccessTokenRes;
import com.bidr.wechat.sdk.WxMiniDecrypt;
import com.bidr.wechat.service.account.SyncAccountService;
import com.bidr.wechat.vo.auth.AuthRes;
import com.bidr.wechat.vo.login.WechatMiniLoginReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sharp
 */
@Service
public class WechatMiniService {

    private static String openIdUrl;

    /**
     * 微信 access_token 缓存提前失效余量（秒），使缓存 TTL 略短于微信 expires_in
     */
    private static final int MINI_ACCESS_TOKEN_TTL_MARGIN = 300;
    /**
     * 微信未返回 expires_in 时的兜底 TTL（秒）
     */
    private static final int MINI_ACCESS_TOKEN_DEFAULT_TTL = 7200;

    @Resource
    private SysConfigCacheService frameCacheService;
    @Resource
    private TokenService tokenService;
    @Resource
    private RestService restService;
    @Resource
    private RedisService redisService;
    @Resource
    private SyncAccountService syncAccountService;

    public AuthRes buildMiniAuthLoginRes(String wechatCode) {
        OpenIdBO bo = getOpenId(wechatCode);
        TokenInfo token = tokenService.buildWechatToken(bo.getOpenId());
        tokenService.putItem(token, TokenItem.OPERATOR.name(), bo.getOpenId());
        tokenService.putItem(token, WechatTokenItem.SESSION_KEY.name(), bo.getSessionKey());
        AuthRes res = new AuthRes();
        res.setUserId(bo.getOpenId());
        res.setToken(token.getToken());
        res.setUnionId(bo.getUnionId());
        return res;
    }

    private OpenIdBO getOpenId(String wechatCode) {
        if (StringUtils.isBlank(openIdUrl)) {
            init();
        }
        String url = buildUrl(openIdUrl, wechatCode);
        OpenIdRespPO po = restService.get(url, OpenIdRespPO.class);
        Validator.assertNull(po.getErrCode(), ErrCodeSys.SYS_ERR_MSG, po.getErrMsg());
        Validator.assertNotBlank(po.getOpenid(), ErrCodeSys.SYS_ERR_MSG, "微信小程序鉴权失败");
        return new OpenIdBO(po.getOpenid(), po.getSessionKey(), po.getUnionId());
    }

    /**
     * 微信 code 换取登录态凭证（公开语义，等价 getOpenId）
     *
     * @param wechatCode 小端 wx.login 拿到的 code
     * @return openid + unionId + sessionKey
     */
    public OpenIdBO getSession(String wechatCode) {
        return getOpenId(wechatCode);
    }

    /**
     * 微信小程序 phoneCode 兑换用户手机号
     *
     * @param phoneCode 小端 getPhoneNumber 拿到的 code
     * @return 纯手机号
     */
    public String getPhoneNumberByCode(String phoneCode) {
        String accessToken = getMiniAccessToken();
        String url = buildUrl(WechatUrlConst.WECHAT_MINI_PHONE_GET_URL, accessToken);
        Map<String, Object> body = new HashMap<>();
        body.put("code", phoneCode);
        PhoneNumberRespPO po = restService.post(url, body, PhoneNumberRespPO.class);
        // 本接口成功响应显式携带 errcode=0（与 jscode2session 成功时不含 errcode 字段不同），只拒非 0 错误码
        Validator.assertTrue(po.getErrCode() == null || po.getErrCode() == WechatErrorCodeConst.SUCCESS,
                ErrCodeSys.SYS_ERR_MSG, po.getErrMsg());
        Validator.assertNotNull(po.getPhoneInfo(), ErrCodeSys.SYS_ERR_MSG, "微信小程序手机号获取失败");
        Validator.assertNotBlank(po.getPhoneInfo().getPhoneNumber(), ErrCodeSys.SYS_ERR_MSG, "微信小程序手机号获取失败");
        return po.getPhoneInfo().getPhoneNumber();
    }

    /**
     * 获取微信小程序 access_token（stable_token，幂等不互相踢），redis 缓存、TTL 略短于微信 expires_in
     */
    private String getMiniAccessToken() {
        String token = redisService.get(WechatRedisKeyConst.MINI_ACCESS_TOKEN_KEY, String.class);
        if (StringUtils.isBlank(token)) {
            String miniAppId = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_MINI_APP_ID);
            String miniSecret = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_MINI_APP_SECRET);
            Map<String, Object> body = new HashMap<>();
            body.put("grant_type", "client_credential");
            body.put("appid", miniAppId);
            body.put("secret", miniSecret);
            WechatPlatformAccessTokenRes po = restService.post(WechatUrlConst.WECHAT_MINI_STABLE_TOKEN_URL, body,
                    WechatPlatformAccessTokenRes.class);
            Validator.assertNotBlank(po.getAccessToken(), ErrCodeSys.SYS_ERR_MSG, "微信小程序access_token获取失败");
            token = po.getAccessToken();
            int expiresIn = po.getExpiresIn() == null ? MINI_ACCESS_TOKEN_DEFAULT_TTL : po.getExpiresIn();
            redisService.set(WechatRedisKeyConst.MINI_ACCESS_TOKEN_KEY,
                    Math.max(expiresIn - MINI_ACCESS_TOKEN_TTL_MARGIN, MINI_ACCESS_TOKEN_TTL_MARGIN), token);
        }
        return token;
    }

    private String buildUrl(String format, Object... argArray) {
        return String.format(format, argArray);
    }

    public LoginRes login(WechatMiniLoginReq req) {
        TokenInfo token = tokenService.getToken();
        String sessionKey = tokenService.getItem(token, WechatTokenItem.SESSION_KEY.name(), String.class);
        WxMiniUserInfo userInfo = decryptUserInfo(req.getEncryptedData(), req.getIv(), sessionKey);
        LoginRes loginRes = syncAccountService.loginOrReg(userInfo.getOpenId(), userInfo.getUnionId(), userInfo.getNickName(), userInfo.getAvatarUrl());
        fillToken(userInfo);
        return loginRes;
    }

    private WxMiniUserInfo decryptUserInfo(String data, String iv, String sessionKey) {
        return JsonUtil.readJson(WxMiniDecrypt.decrypt(data, sessionKey, iv), WxMiniUserInfo.class);
    }

    private void fillToken(WxMiniUserInfo userInfo) {
        Map<String, Object> map = tokenService.getTokenValue();
        map.put(WechatTokenItem.OPEN_ID.name(), userInfo.getOpenId());
        map.put(WechatTokenItem.NICK_NAME.name(), userInfo.getNickName());
        map.put(WechatTokenItem.AVATAR.name(), userInfo.getAvatarUrl());
        map.put(WechatTokenItem.SEX.name(), userInfo.getGender());
        map.put(WechatTokenItem.PROVINCE.name(), userInfo.getProvince());
        map.put(WechatTokenItem.CITY.name(), userInfo.getCity());
        map.put(WechatTokenItem.COUNTRY.name(), userInfo.getCountry());
        map.put(WechatTokenItem.UNION_ID.name(), userInfo.getUnionId());
        tokenService.setTokenValue(map);
    }

    protected void init() {
        String miniAppId = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_MINI_APP_ID);
        String miniSecret = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_MINI_APP_SECRET);
        openIdUrl = buildUrl(WechatUrlConst.WECHAT_MINI_OPENID_GET_URL_FORMAT, miniAppId, miniSecret, "%s");
    }

}
