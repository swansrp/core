package com.bidr.mqtt.service;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.mqtt.inf.MqttHandler;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: MqttCallbackService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/19 09:54
 */
@Slf4j
@Service
public class MqttCallbackService implements MqttCallback {

    @Autowired(required = false)
    private List<MqttHandler> mqttHandlerList;

    @Override
    public void connectionLost(Throwable throwable) {
        log.info("与服务器断开连接");
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) {
        log.trace(JsonUtil.toJson(mqttMessage));
        log.debug(topic + " : " + mqttMessage);
        if (FuncUtil.isNotEmpty(mqttHandlerList)) {
            for (MqttHandler mqttHandler : mqttHandlerList) {
                mqttHandler.handleMessage(topic, mqttMessage);
            }
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        IMqttAsyncClient client = token.getClient();
        System.out.println(client.getClientId() + "发布消息成功！");
    }
}
