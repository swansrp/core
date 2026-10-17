package com.bidr.wechat.service.auth;

import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.platform.service.rest.RestService;
import com.bidr.wechat.constant.WechatParamConst;
import com.bidr.wechat.constant.WechatTokenItem;
import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.constant.err.WechatErrCode;
import com.bidr.wechat.po.WechatBaseRes;
import com.bidr.wechat.po.auth.AuthTokenRes;
import com.bidr.wechat.po.auth.AuthUserInfoReq;
import com.bidr.wechat.po.auth.AuthUserInfoRes;
import com.bidr.wechat.service.account.SyncAccountService;
import com.bidr.wechat.vo.auth.AuthReq;
import com.bidr.wechat.vo.auth.AuthUrlReq;
import com.bidr.wechat.vo.auth.AuthUrlRes;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.URLEncoder;
import java.util.Map;

/**
 * Title: WechatAuthService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/19 10:51
 */
@Service
public class WechatAuthService {

    @Resource
    private RestService restService;
    @Resource
    private TokenService tokenService;
    @Resource
    private SysConfigCacheService frameCacheService;
    @Resource
    private SyncAccountService syncAccountService;

    public AuthUrlRes getOAuthUrl(AuthUrlReq req) {
        String url = buildOAuth2Url(req);
        AuthUrlRes res = new AuthUrlRes();
        res.setUrl(url);
        return res;
    }

    public String buildOAuth2Url(AuthUrlReq req) {
        try {
            String wxCorpId = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_PUBLIC_APP_ID);
            String encodeRedirectUrl = URLEncoder.encode(req.getRedirectUrl(), "UTF-8");
            return String.format(WechatUrlConst.WECHAT_PUBLIC_OAUTH_URL_FORMAT, wxCorpId, encodeRedirectUrl, req.getState());
        } catch (Exception e) {
            Validator.assertException(e);
            return null;
        }
    }

    public LoginRes login(AuthReq req) {
        String publicAppId = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_PUBLIC_APP_ID);
        String publicSecret = frameCacheService.getParamValueAvail(WechatParamConst.WECHAT_PUBLIC_APP_SECRET);
        String getAccessTokenUrl = String.format(WechatUrlConst.WECHAT_PUBLIC_OAUTH_ACCESS_TOKEN_GET_URL_FORMAT,
                publicAppId, publicSecret, req.getCode());
        AuthTokenRes authTokenRes = restService.get(getAccessTokenUrl, AuthTokenRes.class);
        Validator.assertNotBlank(authTokenRes.getOpenId(), ErrCodeSys.SYS_SESSION_TIME_OUT);
        AuthUserInfoReq authUserInfoReq = buildAuthUserInfoReq(authTokenRes);
        AuthUserInfoRes res = restService.get(WechatUrlConst.WECHAT_PUBLIC_OAUTH_USER_INFO_GET_URL, authUserInfoReq,
                AuthUserInfoRes.class);
        validateWechatBaseRes(res);
        LoginRes loginRes = syncAccountService.loginOrReg(res.getOpenId(), res.getUnionId(), res.getNickName(),
                res.getHeadImgUrl());
        fillToken(res);
        return loginRes;
    }

    private AuthUserInfoReq buildAuthUserInfoReq(AuthTokenRes authTokenRes) {
        AuthUserInfoReq authUserInfoReq = new AuthUserInfoReq();
        authUserInfoReq.setOpenId(authTokenRes.getOpenId());
        authUserInfoReq.setAccessToken(authTokenRes.getAccessToken());
        return authUserInfoReq;
    }

    private void validateWechatBaseRes(WechatBaseRes res) {
        if (res.getErrCode() != null) {
            throw new ServiceException(WechatErrCode.WECHAT_SERVER_ERROR, res.getErrCode(), res.getErrMsg());
        }
    }

    private void fillToken(AuthUserInfoRes authUserInfoRes) {
        Map<String, Object> map = tokenService.getTokenValue();
        map.put(WechatTokenItem.OPEN_ID.name(), authUserInfoRes.getOpenId());
        map.put(WechatTokenItem.NICK_NAME.name(), authUserInfoRes.getNickName());
        map.put(WechatTokenItem.AVATAR.name(), authUserInfoRes.getHeadImgUrl());
        map.put(WechatTokenItem.SEX.name(), authUserInfoRes.getSex());
        map.put(WechatTokenItem.PROVINCE.name(), authUserInfoRes.getProvince());
        map.put(WechatTokenItem.CITY.name(), authUserInfoRes.getCity());
        map.put(WechatTokenItem.COUNTRY.name(), authUserInfoRes.getCountry());
        map.put(WechatTokenItem.PRIVILEGE.name(), authUserInfoRes.getPrivilege());
        map.put(WechatTokenItem.UNION_ID.name(), authUserInfoRes.getUnionId());
        tokenService.setTokenValue(map);
    }

    public LoginRes bindPhoneNumber(String phoneNumber) {
        return syncAccountService.loginOrRegByPhoneNumber(phoneNumber);
    }

}
