package com.bidr.mqtt.service;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.stereotype.Service;

/**
 * Title: MqttService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/19 09:43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {
    private final MqttClient client;

    public void connect() {
        try {
            client.connect();
        } catch (MqttException e) {
            log.error("连接失败", e);
            throw new ServiceException(e.getMessage());
        }
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            log.error("断开失败", e);
            throw new ServiceException(e.getMessage());
        }
    }

    public void publish(int qos, boolean retained, String topic, Object message) {
        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setQos(qos);
        mqttMessage.setRetained(retained);
        mqttMessage.setPayload(JsonUtil.toJson(message).getBytes());
        //主题的目的地，用于发布/订阅信息
        MqttTopic mqttTopic = client.getTopic(topic);
        //提供一种机制来跟踪消息的传递进度
        //用于在以非阻塞方式（在后台运行）执行发布是跟踪消息的传递进度
        MqttDeliveryToken token;
        try {
            //将指定消息发布到主题，但不等待消息传递完成，返回的token可用于跟踪消息的传递状态
            //一旦此方法干净地返回，消息就已被客户端接受发布，当连接可用，将在后台完成消息传递。
            token = mqttTopic.publish(mqttMessage);
            token.waitForCompletion();
        } catch (MqttException e) {
            log.error("发送失败", e);
            throw new ServiceException(e.getMessage());
        }
    }

    public void subscribe(String topic, int qos) {
        try {
            client.subscribe(topic, qos);
        } catch (MqttException e) {
            log.error("订阅失败", e);
            throw new ServiceException(e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            client.unsubscribe(topic);
        } catch (MqttException e) {
            log.error("解除订阅失败", e);
            throw new ServiceException(e.getMessage());
        }
    }

}
