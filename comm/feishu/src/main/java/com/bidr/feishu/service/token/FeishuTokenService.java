package com.bidr.feishu.service.token;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.redis.service.RedisService;
import com.bidr.platform.service.rest.RestService;
import com.bidr.feishu.config.FeishuProperties;
import com.bidr.feishu.constant.FeishuErrorCode;
import com.bidr.feishu.constant.FeishuUrl;
import com.bidr.feishu.dto.token.FeishuTokenResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: FeishuTokenService
 * Description: 飞书 tenant_access_token 获取服务（对标 WxBaseService 的 token 管理），redis 缓存
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
public class FeishuTokenService {

    private static final String FEISHU_TOKEN_KEY = "FEISHU:TOKEN:TENANT:";

    /**
     * 缓存提前过期时间（秒），避免临界失效
     */
    private static final int EXPIRE_AHEAD = 200;

    @Resource
    private FeishuProperties feishuProperties;

    @Resource
    private RedisService redisService;

    @Resource
    private RestService restService;

    /**
     * 获取当前线程默认 token
     */
    public String getDefaultToken() {
        return FeishuTokenHolder.get();
    }

    /**
     * 设置当前线程默认 token
     */
    public void setDefaultToken(String token) {
        FeishuTokenHolder.set(token);
    }

    /**
     * 清除当前线程默认 token
     */
    public void resetDefaultToken() {
        setDefaultToken("");
    }

    /**
     * 获取 tenant_access_token，优先取当前线程与 redis 缓存，失效时向飞书重新申请
     */
    public String getToken() {
        String token = getDefaultToken();
        if (FuncUtil.isNotEmpty(token)) {
            return token;
        }
        token = redisService.get(getAccessTokenKey(), String.class);
        if (FuncUtil.isEmpty(token)) {
            token = refreshToken();
        } else {
            setDefaultToken(token);
        }
        return token;
    }

    /**
     * 向飞书开放平台申请新的 tenant_access_token 并写入 redis
     */
    public String refreshToken() {
        Map<String, Object> body = new HashMap<>(4);
        body.put("app_id", feishuProperties.getAppId());
        body.put("app_secret", feishuProperties.getAppSecret());
        FeishuTokenResp resp = restService.post(feishuProperties.getHost() + FeishuUrl.TENANT_ACCESS_TOKEN, body,
                FeishuTokenResp.class);
        if (FuncUtil.isEmpty(resp) || FeishuErrorCode.SUCCESS != resp.getCode()) {
            log.error("获取飞书 tenant_access_token 失败: {}", FuncUtil.isEmpty(resp) ? "空响应" : resp.getMsg());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "获取飞书 tenant_access_token 失败");
        }
        int expire = FuncUtil.isEmpty(resp.getExpire()) ? 7200 : resp.getExpire();
        redisService.set(getAccessTokenKey(), Math.max(expire - EXPIRE_AHEAD, 60), resp.getTenantAccessToken());
        setDefaultToken(resp.getTenantAccessToken());
        return resp.getTenantAccessToken();
    }

    private String getAccessTokenKey() {
        return FEISHU_TOKEN_KEY + feishuProperties.getAppId();
    }
}
