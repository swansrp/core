package com.bidr.authorization.openapi.service;

import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.authorization.dto.openapi.OpenApiTokenRcv;
import com.bidr.authorization.dto.openapi.OpenApiTokenRes;
import com.bidr.authorization.openapi.exception.TokenInvalidException;
import com.bidr.authorization.openapi.utils.OpenApiUtil;
import com.bidr.authorization.vo.token.TokenRes;
import com.bidr.kernel.config.response.Response;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
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
 * @since 2023/04/27 12:41
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

    protected abstract String getAppKey();

    protected abstract String getAppSecret();

    public synchronized String getAccessToken() {
        String accessToken = redisService.get(getAccessTokenKey(), String.class);
        if (StringUtils.isEmpty(accessToken)) {
            OpenApiTokenRes res = fetchAccessToken();
            redisService.set(getAccessTokenKey(), res.getExpired(), res.getToken());
            accessToken = res.getToken();
        }
        return accessToken;
    }

    public OpenApiTokenRes fetchAccessToken() {
        OpenApiTokenRcv openApiTokenRcv = buildOpenApiTokenRcv();
        Response<OpenApiTokenRes> res = restService.post(getBaseUrl() + "/open-api/token", openApiTokenRcv, Response.class, OpenApiTokenRes.class);
        return res.getPayload();
    }

    @Retryable(value = {TokenInvalidException.class}, maxAttempts = 5)
    public <T> T post(String url, Object req, Class<T> resClazz) {
        return post(url, getHttpHeaders(), req, resClazz);
    }

    @Retryable(value = {TokenInvalidException.class}, maxAttempts = 5)
    public <T> T post(String url, HttpHeaders header, Object req, Class<T> resClazz) {
        if (header == null) {
            header = getHttpHeaders();
        }
        Response<T> res = restService.post(getBaseUrl() + url, header, req, Response.class, resClazz);
        handleCommonError(res);
        return res.getPayload();
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
        Response<?> res = restService.get(getBaseUrl() + url, header, param, Response.class);
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

    private OpenApiTokenRcv buildOpenApiTokenRcv() {
        OpenApiTokenRcv openApiTokenRcv = ReflectionUtil.copy(OpenApiUtil.sign(getAppSecret()), OpenApiTokenRcv.class);
        openApiTokenRcv.setAppKey(getAppKey());
        return openApiTokenRcv;
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders header = new HttpHeaders();
        header.setContentType(MediaType.APPLICATION_JSON);
        header.set(RequestConst.TOKEN, "Bearer " + getAccessToken());
        header.set(RequestConst.CLIENT_TYPE, ClientType.PLATFORM.name());
        return header;
    }
}
