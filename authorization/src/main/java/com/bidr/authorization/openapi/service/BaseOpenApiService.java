package com.bidr.authorization.openapi.service;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.dto.SignDTO;
import com.bidr.authorization.openapi.exception.TokenInvalidException;
import com.bidr.authorization.openapi.utils.OpenApiUtil;
import com.bidr.authorization.vo.token.TokenRes;
import com.bidr.kernel.config.response.Response;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.RandomUtil;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.rest.RestService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: BaseOpenApiService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/27 12:41
 */
@Slf4j
@Service
public abstract class BaseOpenApiService {
    @Resource
    protected RedisService redisService;
    @Resource
    protected RestService restService;

    protected abstract String getAccessTokenKey();

    protected abstract int getAccessTokenExpired();

    protected abstract String getBaseUrl();

    protected abstract String getAppId();

    public synchronized String getAccessToken() {
        String accessToken = redisService.get(getAccessTokenKey(), String.class);
        if (StringUtils.isEmpty(accessToken)) {
            TokenRes res = fetchAccessToken();
            redisService.set(getAccessTokenKey(), getAccessTokenExpired(), res.getToken());
            accessToken = res.getToken();
        }
        return accessToken;
    }

    public TokenRes fetchAccessToken() {
        SignDTO signDTO = buildSignDTO();
        Response res = restService.get(getBaseUrl() + "/token", signDTO, Response.class);
        return JsonUtil.readJson(res.getPayload(), TokenRes.class);
    }

    @Retryable(value = {TokenInvalidException.class}, maxAttempts = 5)
    public <T> T post(String url, Class<T> resClazz, Object req) {
        return post(url, getHttpHeaders(), resClazz, req);
    }

    @Retryable(value = {TokenInvalidException.class}, maxAttempts = 5)
    public <T> T post(String url, HttpHeaders header, Class<T> resClazz, Object req) {
        if (header == null) {
            header = getHttpHeaders();
        }
        Response<?> res = restService.post(url, header, req, Response.class);
        handleCommonError(res);
        return JsonUtil.readJson(res.getPayload(), resClazz);
    }

    @Retryable(value = {TokenInvalidException.class}, maxAttempts = 5)
    public <T> T get(String url, Object param, Class<T> resClazz) {
        return get(url, getHttpHeaders(), param, resClazz);
    }

    @Retryable(value = {TokenInvalidException.class}, maxAttempts = 5)
    public <T> T get(String url, HttpHeaders header, Object param, Class<T> resClazz) {
        if (header == null) {
            header = getHttpHeaders();
        }
        Response<?> res = restService.get(url, header, param, Response.class);
        handleCommonError(res);
        return JsonUtil.readJson(res.getPayload(), resClazz);
    }

    private void handleCommonError(Response<?> res) {
        if (res == null) {
            throw new ServiceException("访问失败");
        } else if (FuncUtil.equals(res.getStatus().getCode(), ErrCodeSys.SYS_SESSION_TIME_OUT.getErrCode())) {
            redisService.delete(getAccessTokenKey());
            throw new TokenInvalidException();
        } else if (!FuncUtil.equals(res.getStatus().getCode(), ErrCodeSys.SUCCESS.getErrCode())) {
            log.info("系统错误: {}-{}", res.getStatus().getMsg(), res.getStatus().getDetails());
        }
    }

    private SignDTO buildSignDTO() {
        // todo
        String secret = RandomUtil.getUUID();
        SignDTO signDTO = OpenApiUtil.sign(secret);
        return signDTO;
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);
        header.set(RequestConst.TOKEN, getAccessToken());
        header.set(RequestConst.CLIENT_TYPE, "WEB");
        return header;
    }
}
