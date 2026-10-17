package com.bidr.mqtt.inf;

import org.eclipse.paho.client.mqttv3.MqttMessage;

/**
 * Title: MqttHandler
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/19 09:58
 */
public interface MqttHandler {
    /**
     * MQTT消息处理
     *
     * @param topic   主题
     * @param message 内容
     */
    void handleMessage(String topic, MqttMessage message);
}
