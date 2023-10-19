package com.bidr.mqtt.controller;

import com.bidr.kernel.config.response.Resp;
import com.bidr.mqtt.service.MqttService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: MqttTestController
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/10/19 10:11
 */
@Api(tags = "MQTT - 测试接口")
@RestController
@Profile({"local", "dev", "test"})
@RequestMapping(path = {"/web/mqtt"})
@RequiredArgsConstructor
public class MqttTestController {

    private final MqttService mqttService;

    @RequestMapping(path = {"/connect"}, method = {RequestMethod.POST})
    public void connect() {
        mqttService.connect();
        Resp.notice("成功连接MQTT服务器");
    }

    @RequestMapping(path = {"/disconnect"}, method = {RequestMethod.POST})
    public void disconnect() {
        mqttService.disconnect();
        Resp.notice("已与MQTT服务器断开");


    }

    @RequestMapping(path = {"/publish"}, method = {RequestMethod.POST})
    public void publish(int qos, boolean retained, String topic, String message) {
        mqttService.publish(qos, retained, topic, message);
    }

    @RequestMapping(path = {"/subscribe"}, method = {RequestMethod.POST})
    public void subscribe(int qos, String topic) {
        mqttService.subscribe(topic, qos);
        Resp.notice("已成功订阅: " + topic);
    }

    @RequestMapping(path = {"/unsubscribe"}, method = {RequestMethod.POST})
    public void unsubscribe(String topic) {
        mqttService.unsubscribe(topic);
        Resp.notice("已解除订阅: " + topic);
    }
}
