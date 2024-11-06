package com.bidr.socket.io.controller;


import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.config.SocketIoConfig;
import com.bidr.socket.io.constant.ChatMessageConstant;
import com.bidr.socket.io.dao.entity.ChatRoomMember;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.dao.repository.mysql.ChatRoomMemberService;
import com.bidr.socket.io.service.chat.ChatMessageHistoryService;
import com.bidr.socket.io.service.session.ChatSessionService;
import com.bidr.socket.io.service.socket.SendMessageService;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
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
    private SocketIOServer socketIOServer;
    @Resource
    private ChatSessionService chatSessionService;
    @Resource
    private SendMessageService sendMessageService;
    @Resource
    private ChatMessageHistoryService chatMessageHistoryService;
    @Resource
    private ChatRoomMemberService chatRoomMemberService;

    public void onConnect(SocketIOClient client) {
        String operator = client.getHandshakeData().getSingleUrlParam(SocketIoConfig.OPERATOR);
        String namespace = chatSessionService.buildSocketIoNamespace(operator);
        SocketIONamespace socketIONamespace = buildSocketIONamespace(namespace);

        boolean login = chatSessionService.hasLogin(operator);
        log.info("[{}]客户 {} 连接", operator, login ? "已登录" : "未登录");

        UUID sessionId = client.getSessionId();
        String token = client.getHandshakeData().getSingleUrlParam(SocketIoConfig.TOKEN);
        TokenInfo tokenInfo = AuthTokenUtil.resolveToken(token);
        chatSessionService.login(operator, tokenInfo, sessionId);
        // showUndeliveredMessage(operator, socketIONamespace, client);
    }

    private SocketIONamespace buildSocketIONamespace(String namespace) {
        SocketIONamespace socketIONamespace = socketIOServer.getNamespace(namespace);
        if (socketIONamespace == null) {
            log.info("创建namespace: {}", namespace);
            socketIONamespace = socketIOServer.addNamespace(namespace);
            socketIONamespace.addEventListener(ChatMessageConstant.CHAT, ChatMessage.class,
                    sendMessageService.onViewMsgReceived(socketIONamespace));
        }
        return socketIONamespace;
    }

    public void onDisconnect(SocketIOClient client) {
        String operator = client.getHandshakeData().getSingleUrlParam(SocketIoConfig.OPERATOR);
        String token = client.getHandshakeData().getSingleUrlParam(SocketIoConfig.TOKEN);
        log.info("客户 {}-{} 断开", operator, token);
        String sessionId = client.getSessionId().toString();
        TokenInfo tokenInfo = AuthTokenUtil.resolveToken(token);
        chatSessionService.logoff(operator, tokenInfo, sessionId);
    }

    private void showUndeliveredMessage(String operator, SocketIONamespace socketIONamespace, SocketIOClient client) {
        List<ChatRoomMember> chatRoomMemberList = chatRoomMemberService.getRoomListByUserId(operator);
        if (CollectionUtils.isNotEmpty(chatRoomMemberList)) {
            for (ChatRoomMember chatRoomMember : chatRoomMemberList) {
                List<TopicChatMessage> messageList = chatMessageHistoryService.getUnDeliveredChatMessage(operator,
                        chatRoomMember.getRoomId());
                if (CollectionUtils.isNotEmpty(messageList)) {
                    for (TopicChatMessage message : messageList) {
                        sendMessageService.show(socketIONamespace, client, message);
                        sendMessageService.messageDelivered(message);
                    }
                }
            }
        }
    }
}
