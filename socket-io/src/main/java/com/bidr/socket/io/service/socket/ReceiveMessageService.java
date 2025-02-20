package com.bidr.socket.io.service.socket;


import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.constant.ChatMessageConstant;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.utils.ClientUtil;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.DataListener;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: SocketIoReceiveMessageService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/9/22 16:08
 */
@Slf4j
@Service
public class ReceiveMessageService {
    @Resource
    private SendMessageService chatSendMessageService;
    @Resource
    private SendMessageService sendMessageService;

    public void receiveSubscribeMessage(TopicChatMessage message) {
        log.debug("接收到发送广播, {}", message);
        chatSendMessageService.show(message);
    }

    public DataListener<ChatMessage> onViewMsgReceived() {
        return (client, msg, ackSender) -> {
            String operator = ClientUtil.get(client, TokenItem.OPERATOR);
            log.debug("From: 客户端[{}-{}] - 收到消息 '{}'", operator, client.getSessionId().toString(), msg);
            fillChatMessage(client, msg);
            TopicChatMessage topicChatMessage = sendMessageService.buildTopicChatMessage(ChatMessageConstant.CHAT, operator, msg);
            sendMessageService.publish(topicChatMessage);
        };
    }

    private void fillChatMessage(SocketIOClient client, ChatMessage msg) {
        String userFace = ClientUtil.get(client, TokenItem.AVATAR);
        String userName = ClientUtil.get(client, TokenItem.NICK_NAME);
        if (StringUtils.isNotBlank(userName)) {
            msg.setUserId(userName);
        }
        if (StringUtils.isNotBlank(userFace)) {
            msg.setAvatar(userFace);
        }
        msg.setSessionId(client.getSessionId().toString());
    }
}
