package com.bidr.wechat.service;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.platform.service.rest.RestService;
import com.bidr.platform.utils.XmlUtil;
import com.bidr.wechat.constant.WechatConst;
import com.bidr.wechat.constant.WechatErrorCodeConst;
import com.bidr.wechat.constant.WechatRedisKeyConst;
import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.constant.err.WechatErrCode;
import com.bidr.wechat.po.WechatBaseReq;
import com.bidr.wechat.po.WechatBaseRes;
import com.bidr.wechat.po.platform.WechatPlatformAccessTokenRes;
import com.bidr.wechat.sdk.AesException;
import com.bidr.wechat.sdk.SHA1;
import com.bidr.wechat.sdk.WxBizMsgCrypt;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.RetryException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * Title: WechatSignatureServiceImpl
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/1/22 23:31
 */
@Slf4j
@Service
public class WechatPublicService {

    private static WxBizMsgCrypt crypt;
    private static String signatureToken;

    @Resource
    private SysConfigCacheService frameCacheService;
    @Resource
    private RestService restService;
    @Resource
    private RedisService redisService;

    public void signature(String signature, String timestamp, String nonce) {
        String sha1 = null;
        try {
            sha1 = SHA1.getSHA1(signatureToken, timestamp, nonce);
        } catch (AesException e) {
            Validator.assertException(e);
        }
        Validator.assertNotBlank(signature, ErrCodeSys.SYS_ERR_MSG, "验签失败");
        Validator.assertTrue(StringUtils.equals(sha1, signature), ErrCodeSys.SYS_ERR_MSG, "验签失败");
    }

    public <T> T deCrypt(String msgSignature, String timeStamp, String nonce, String postData, Class<T> clazz) {
        String result = null;
        try {
            result = crypt.decryptMsg(msgSignature, timeStamp, nonce, postData);
        } catch (AesException e) {
            Validator.assertException(e);
        }
        return XmlUtil.toNormalObject(result, clazz);
    }

    public String crypt(String replyMsg, String timeStamp, String nonce) {
        String result = null;
        try {
            result = crypt.encryptMsg(replyMsg, timeStamp, nonce);
        } catch (AesException e) {
            Validator.assertException(e);
        }
        return result;
    }

    @Retryable(value = {RetryException.class}, maxAttempts = 2)
    public <RES extends WechatBaseRes> RES post(String url, Class<RES> resClazz, Object req) {
        return post(url, getHttpHeaders(), resClazz, new WechatBaseReq(), req);
    }

    @Retryable(value = {RetryException.class}, maxAttempts = 2)
    public <RES extends WechatBaseRes, REQ extends WechatBaseReq> RES post(String url, HttpHeaders header, Class<RES> resClazz, REQ param, Object req) {
        if (header == null) {
            header = getHttpHeaders();
        }
        param.setAccessToken(getPublicAccessToken());
        RES res = restService.post(url, header, param, req, resClazz);
        handleCommonError(res);
        return res;
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);
        return header;
    }

    public String getPublicAccessToken() {
        String token = redisService.get(WechatRedisKeyConst.ACCESS_TOKEN_KEY, String.class);
        if (StringUtils.isBlank(token)) {
            String publicAppId = frameCacheService.getParamValueAvail(WechatConst.WECHAT_PUBLIC_APP_ID);
            String publicSecret = frameCacheService.getParamValueAvail(WechatConst.WECHAT_PUBLIC_APP_SECRET);
            String publicAccessTokenUrl = String.format(WechatUrlConst.WECHAT_PUBLIC_ACCESS_TOKEN_GET_URL_FORMAT, publicAppId, publicSecret);
            WechatPlatformAccessTokenRes po = restService.get(publicAccessTokenUrl, WechatPlatformAccessTokenRes.class);
            token = po.getAccessToken();
            redisService.set(WechatRedisKeyConst.ACCESS_TOKEN_KEY, po.getExpiresIn(), token);
        }
        return token;
    }

    private void handleCommonError(WechatBaseRes res) {
        if (res == null) {
            throw new ServiceException("微信公众号访问失败");
        } else if (res.getErrCode() != null && res.getErrCode() != WechatErrorCodeConst.SUCCESS) {
            if (res.getErrCode() == WechatErrorCodeConst.TOKEN_INVALID) {
                redisService.delete(WechatRedisKeyConst.ACCESS_TOKEN_KEY);
                throw new RetryException("TOKEN_INVALID");
            } else if (res.getErrCode() == WechatErrorCodeConst.SYS_BUSY) {
                throw new RetryException("SYS_BUSY");
            } else {
                ServiceException exception = new ServiceException(WechatErrCode.WECHAT_SERVER_ERROR, res.getErrCode(), res.getErrMsg());
                exception.setErrObj(res.getErrCode());
                throw exception;
            }
        }
    }

    @Retryable(value = {RetryException.class}, maxAttempts = 2)
    public <RES extends WechatBaseRes, REQ extends WechatBaseReq> RES post(String url, Class<RES> resClazz, REQ param, Object req) {
        return post(url, getHttpHeaders(), resClazz, param, req);
    }

    @Retryable(value = {RetryException.class}, maxAttempts = 2)
    public <RES extends WechatBaseRes, REQ extends WechatBaseReq> RES get(String url, REQ param, Class<RES> resClazz) {
        return get(url, getHttpHeaders(), param, resClazz);
    }

    @Retryable(value = {RetryException.class}, maxAttempts = 2)
    public <RES extends WechatBaseRes, REQ extends WechatBaseReq> RES get(String url, HttpHeaders header, REQ param, Class<RES> resClazz) {
        if (header == null) {
            header = getHttpHeaders();
        }
        param.setAccessToken(getPublicAccessToken());
        RES res = restService.get(url, header, param, resClazz);
        handleCommonError(res);
        return res;
    }

    @Retryable(value = {RetryException.class}, maxAttempts = 2)
    public <RES extends WechatBaseRes> RES get(String url, Class<RES> resClazz) {
        return get(url, getHttpHeaders(), new WechatBaseReq(), resClazz);
    }

    @PostConstruct
    protected void init() {
        String aesKey = frameCacheService.getParamValueAvail(WechatConst.WECHAT_PUBLIC_AES_KEY);
        String publicAppId = frameCacheService.getParamValueAvail(WechatConst.WECHAT_PUBLIC_APP_ID);
        signatureToken = frameCacheService.getParamValueAvail(WechatConst.WECHAT_PUBLIC_TOKEN);
        try {
            crypt = new WxBizMsgCrypt(signatureToken, aesKey, publicAppId);
        } catch (AesException e) {
            Validator.assertException(e);
        }
    }

}
