package com.bidr.socket.io.config;


import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.constant.ChatMessageConstant;
import com.bidr.socket.io.dao.entity.ChatRoomMember;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.dao.repository.mysql.ChatRoomMemberService;
import com.bidr.socket.io.service.chat.ChatMessageHistoryService;
import com.bidr.socket.io.service.session.ChatSessionService;
import com.bidr.socket.io.service.socket.SendMessageService;
import com.bidr.socket.io.utils.ClientUtil;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Title: SocketIoEndpoint
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */
@Slf4j
@Component
public class SocketIoEndpoint {

    @Resource
    private SocketIOServer socketioServer;
    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private SendMessageService sendMessageService;
    @Resource
    private ChatMessageHistoryService chatMessageHistoryService;
    @Resource
    private ChatRoomMemberService chatRoomMemberService;

    public void onConnect(SocketIOClient client) {
        chatSessionService.login(client);
        showUndeliveredMessage(client);
    }

    public void onDisconnect(SocketIOClient client) {
        chatSessionService.logoff(client);
    }

    private void showUndeliveredMessage(SocketIOClient client) {
        String operator = ClientUtil.get(client, TokenItem.OPERATOR, String.class);
        List<ChatRoomMember> chatRoomMemberList = chatRoomMemberService.getRoomListByUserId(operator);
        if (CollectionUtils.isNotEmpty(chatRoomMemberList)) {
            for (ChatRoomMember chatRoomMember : chatRoomMemberList) {
                List<TopicChatMessage> messageList = chatMessageHistoryService.getUnDeliveredChatMessage(operator, chatRoomMember.getRoomId());
                if (CollectionUtils.isNotEmpty(messageList)) {
                    for (TopicChatMessage message : messageList) {
                        sendMessageService.show(client, message);
                        sendMessageService.messageDelivered(message);
                    }
                }
            }
        }
    }
}
