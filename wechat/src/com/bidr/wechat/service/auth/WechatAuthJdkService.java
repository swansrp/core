package com.bidr.wechat.service.auth;

import com.bidr.kernel.utils.RandomUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.wechat.constant.WechatConst;
import com.bidr.wechat.constant.WechatRedisKeyConst;
import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.auth.JsApiSignature;
import com.bidr.wechat.po.auth.JsApiTicketReq;
import com.bidr.wechat.po.auth.JsApiTicketRes;
import com.bidr.wechat.service.WechatPublicService;
import com.bidr.wechat.vo.auth.AuthJdkApiReq;
import com.bidr.wechat.vo.auth.AuthJdkApiRes;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: WechatAuthJdkService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/19 17:58
 */
@Service
public class WechatAuthJdkService {

    @Resource
    private RedisService redisService;
    @Resource
    private SysConfigCacheService frameCacheService;
    @Resource
    private WechatPublicService wechatPublicService;

    public AuthJdkApiRes getAuthJdkApiSignature(AuthJdkApiReq req) {
        JsApiSignature signature = buildSignature(req.getBaseUrl());
        AuthJdkApiRes res = ReflectionUtil.copy(signature, AuthJdkApiRes.class);
        res.setSignature(signature.getSignatureStr());
        String appId = frameCacheService.getParamValueAvail(WechatConst.WECHAT_PUBLIC_APP_ID);
        res.setAppId(appId);
        return res;
    }

    private JsApiSignature buildSignature(String baseUrl) {
        JsApiSignature signature = new JsApiSignature();
        signature.setJsApiTicket(buildJsApiTicket());
        signature.setNonceStr(RandomUtil.getString(16));
        signature.setTimestamp(String.valueOf(System.currentTimeMillis() / 1000));
        signature.setUrl(baseUrl);
        signature.setSignatureStr(buildSignatureStr(signature));
        return signature;
    }

    private String buildJsApiTicket() {
        String ticket = redisService.get(WechatRedisKeyConst.JS_API_TICKET_KEY, String.class);
        if (StringUtils.isBlank(ticket)) {
            JsApiTicketRes res = wechatPublicService.get(WechatUrlConst.WECHAT_PUBLIC_JS_API_TICKET_GET_URL, new JsApiTicketReq(), JsApiTicketRes.class);
            redisService.set(WechatRedisKeyConst.JS_API_TICKET_KEY, res.getExpiresIn(), res.getTicket());
            ticket = res.getTicket();
        }
        return ticket;
    }

    private String buildSignatureStr(JsApiSignature signature) {
        String res = "jsapi_ticket=" + signature.getJsApiTicket() + "&noncestr=" + signature.getNonceStr() +
                "&timestamp=" + signature.getTimestamp() +
                "&url=" + signature.getUrl();
        return DigestUtils.sha1Hex(res);
    }
}
