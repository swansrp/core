package com.bidr.mqtt.config;

import com.bidr.mqtt.service.MqttCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * Title: MqttConfig
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/19 09:29
 */
@Configuration
@Slf4j
public class MqttConfig {
    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Value("${mqtt.host}")
    private String hostUrl;

    @Value("${mqtt.client.id}")
    private String clientId;

    @Resource
    private MqttCallbackService mqttCallbackService;

    /**
     * 在bean初始化后连接到服务器
     */
    @Bean
    public MqttClient MqttClient() throws MqttException {
        MqttClient client;
        try {
            //创建MQTT客户端对象
            client = new MqttClient(hostUrl, clientId, new MemoryPersistence());
            //连接设置
            MqttConnectOptions options = new MqttConnectOptions();
            //是否清空session，设置false表示服务器会保留客户端的连接记录（订阅主题，qos）,客户端重连之后能获取到服务器在客户端断开连接期间推送的消息
            //设置为true表示每次连接服务器都是以新的身份
            options.setCleanSession(true);
            //设置连接用户名
            options.setUserName(username);
            //设置连接密码
            options.setPassword(password.toCharArray());
            //设置超时时间，单位为秒
            options.setConnectionTimeout(100);
            //设置心跳时间 单位为秒，表示服务器每隔 1.5*20秒的时间向客户端发送心跳判断客户端是否在线
            options.setKeepAliveInterval(20);
            //设置遗嘱消息的话题，若客户端和服务器之间的连接意外断开，服务器将发布客户端的遗嘱信息
            options.setWill("willTopic", (clientId + "与服务器断开连接").getBytes(), 0, false);
            //设置回调
            client.setCallback(mqttCallbackService);
            client.connect(options);
        } catch (MqttException e) {
            log.error("MQTT 初始化失败");
            throw e;
        }
        return client;
    }


}
