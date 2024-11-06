package com.bidr.socket.io.service.socket;


import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.service.session.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: SocketIoReceiveMessageService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/22 16:08
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.service.chat
 */
@Slf4j
@Service
public class ReceiveMessageService {

    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private SendMessageService chatSendMessageService;

    public void receiveSubscribeMessage(TopicChatMessage message) {
        log.debug("接收到发送广播, {}", message);
        if (chatSessionService.existed(message.getTargetId())) {
            log.debug("处理广播消息{}", message);
            handleMessage(message);
        }
    }

    private void handleMessage(TopicChatMessage message) {
        chatSendMessageService.show(chatSessionService.getSocketIONamespace(message.getTargetId()), message);
    }
}
