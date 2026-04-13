package com.bidr.kafka.example;

import com.bidr.kafka.service.KafkaConsumerManager;
import com.bidr.kafka.service.KafkaProducerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * Title: ExampleKafkaController
 * Description: Kafka使用示例Controller Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/3/1
 */
// @RestController
// @RequestMapping("/kafka")
// 引入kafka模块依赖后自动启用，无需额外注解
public class ExampleKafkaController {

    @Autowired
    private KafkaProducerService producerService;

    @Autowired
    private KafkaConsumerManager consumerManager;

    /**
     * 发送消息示例
     */
    @PostMapping("/send")
    public String sendMessage(@RequestParam String topic, @RequestParam String message) {
        producerService.send(topic, message);
        return "Message sent to topic: " + topic;
    }

    /**
     * 同步发送消息示例
     */
    @PostMapping("/send-sync")
    public String sendSync(@RequestParam String topic, @RequestParam String message) {
        try {
            producerService.sendSync(topic, message);
            return "Message sent synchronously to topic: " + topic;
        } catch (Exception e) {
            return "Failed to send message: " + e.getMessage();
        }
    }

    /**
     * 事务发送消息示例
     */
    @PostMapping("/send-transaction")
    public String sendInTransaction(@RequestParam String topic, @RequestParam String message) {
        try {
            producerService.send(topic, message);
            return "Message sent in transaction to topic: " + topic;
        } catch (Exception e) {
            return "Failed to send message in transaction: " + e.getMessage();
        }
    }

    /**
     * 动态启动消费者
     */
    @PostMapping("/consumer/start")
    public String startConsumer(@RequestParam String topic) {
        boolean result = consumerManager.startConsumer(topic);
        return result ? "Consumer started for topic: " + topic : "Failed to start consumer";
    }

    /**
     * 动态暂停消费者
     */
    @PostMapping("/consumer/pause")
    public String pauseConsumer(@RequestParam String topic) {
        boolean result = consumerManager.pauseConsumer(topic);
        return result ? "Consumer paused for topic: " + topic : "Failed to pause consumer";
    }

    /**
     * 动态恢复消费者
     */
    @PostMapping("/consumer/resume")
    public String resumeConsumer(@RequestParam String topic) {
        boolean result = consumerManager.resumeConsumer(topic);
        return result ? "Consumer resumed for topic: " + topic : "Failed to resume consumer";
    }

    /**
     * 动态停止消费者
     */
    @PostMapping("/consumer/stop")
    public String stopConsumer(@RequestParam String topic) {
        boolean result = consumerManager.stopConsumer(topic);
        return result ? "Consumer stopped for topic: " + topic : "Failed to stop consumer";
    }

    /**
     * 获取消费者状态
     */
    @GetMapping("/consumer/status")
    public Map<String, String> getConsumerStatus() {
        return consumerManager.getAllConsumerStatus();
    }

    /**
     * 获取指定Topic消费者状态
     */
    @GetMapping("/consumer/status/{topic}")
    public String getConsumerStatus(@PathVariable String topic) {
        return consumerManager.getConsumerStatus(topic);
    }
}
