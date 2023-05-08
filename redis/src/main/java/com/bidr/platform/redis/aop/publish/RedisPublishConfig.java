package com.bidr.platform.redis.aop.publish;

import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.NetUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: RedisPublishConfig
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/10/19 10:01
 */
@Slf4j
@Component
public class RedisPublishConfig {

    private static final Map<String, List<Integer>> MAP = new ConcurrentHashMap<>();
    @Resource
    private RedissonClient redissonClient;
    @Value("${server.port}")
    private String port;

    public void registerPublish(String topic, Object delegate, Method method, Class<?> parameterType) {
        log.info("创建订阅频道: {}", topic);
        Integer listenerId = redissonClient.getTopic(topic).addListener(String.class, (channel, msg) -> {
            if (parameterType != null) {
                RedisPublishDto<?> obj = JsonUtil.readJson(msg, RedisPublishDto.class, parameterType);
                if (needExec(obj)) {
                    ReflectionUtil.invoke(delegate, method, obj.getData());
                }
            }
        });
        MAP.getOrDefault(topic, new ArrayList<>()).add(listenerId);
    }

    private boolean needExec(RedisPublishDto dto) {
        return !StringUtils.equals(dto.getSerialNumber(), buildRedisPublishMsgSerialNumber());
    }

    private String buildRedisPublishMsgSerialNumber() {
        return NetUtil.getLocalIp() + ":" + port;
    }

    public void registerPublish(String topic, Object delegate, Method method) {
        log.info("创建订阅频道: {}", topic);
        Integer listenerId = redissonClient.getTopic(topic).addListener(String.class, (channel, msg) -> {
            RedisPublishDto<?> obj = JsonUtil.readJson(msg, RedisPublishDto.class);
            if (needExec(obj)) {
                ReflectionUtil.invoke(delegate, method, obj);
            }
        });
        MAP.getOrDefault(topic, new ArrayList<>()).add(listenerId);
    }

    public void deRegisterPublish(String topic) {
        log.info("取消订阅频道: {}", topic);
        List<Integer> listenerIdList = MAP.getOrDefault(topic, new ArrayList<>());
        redissonClient.getTopic(topic).removeListener((Integer[]) listenerIdList.toArray());
    }

    public void publish(String topic) {
        publish(topic, null, false);
    }

    /**
     * 广播主题
     *
     * @param topic           主题
     * @param redisPublishMsg 广播参数
     * @param localExec       本机接到广播后是否执行
     */
    public void publish(String topic, Object redisPublishMsg, boolean localExec) {
        RedisPublishDto<Object> msg = new RedisPublishDto<>();
        if (!localExec) {
            msg.setSerialNumber(buildRedisPublishMsgSerialNumber());
        }
        msg.setData(redisPublishMsg);
        redissonClient.getTopic(topic).publish(JsonUtil.toJsonString(msg, false, false, true));
    }

    public void publish(String topic, Object redisPublishMsg) {
        publish(topic, redisPublishMsg, false);
    }

}
