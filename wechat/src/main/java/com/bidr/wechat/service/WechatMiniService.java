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
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.platform.service.rest.RestService;
import com.bidr.wechat.bo.OpenIdBO;
import com.bidr.wechat.constant.WechatParamConst;
import com.bidr.wechat.constant.WechatTokenItem;
import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.miniprogram.OpenIdRespPO;
import com.bidr.wechat.po.miniprogram.WxMiniUserInfo;
import com.bidr.wechat.sdk.WxMiniDecrypt;
import com.bidr.wechat.service.account.SyncAccountService;
import com.bidr.wechat.vo.auth.AuthRes;
import com.bidr.wechat.vo.login.WechatMiniLoginReq;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author Sharp
 */
@Service
public class WechatMiniService {

    private static String openIdUrl;

    @Resource
    private SysConfigCacheService frameCacheService;
    @Resource
    private TokenService tokenService;
    @Resource
    private RestService restService;
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
        return new OpenIdBO(po.getOpenid(), po.getSessionKey());
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
