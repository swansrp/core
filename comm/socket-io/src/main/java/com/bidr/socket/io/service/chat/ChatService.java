package com.bidr.socket.io.service.chat;


import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.constant.ChatMessageConstant;
import com.bidr.socket.io.constant.err.SocketCodeSys;
import com.bidr.socket.io.service.socket.SendMessageService;
import com.bidr.socket.io.vo.chat.msg.ChatReq;
import com.bidr.socket.io.vo.chat.msg.ChatRes;
import com.bidr.socket.io.vo.chat.msg.RetractChatReq;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: ChatService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/25 18:25
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.service.chat
 */
@Slf4j
@Service
public class ChatService {

    @Resource
    private TokenService tokenService;
    @Resource
    private SendMessageService sendMessageService;
    @Resource
    private ChatMessageHistoryService chatMessageHistoryService;

    public ChatRes chat(ChatReq req) {
        ChatMessage chatMessage = buildChatMessage(req);
        String sessionId = tokenService.getItem(TokenItem.SESSION_ID.name(), String.class);
        Validator.assertNotBlank(sessionId, SocketCodeSys.SOCKET_DISCONNECTED);
        chatMessage.setSessionId(sessionId);
        if (StringUtils.isNotBlank(req.getTargetId())) {
            sendMessageService.publishMsg(req.getTargetId(), chatMessage);
        } else if (StringUtils.isNotBlank(req.getRoomId())) {
            sendMessageService.broadcastMsg(chatMessage);
        } else {
            Validator.assertException(ErrCodeSys.PA_DATA_NOT_EXIST, "消息目的地");
        }
        return ReflectionUtil.copy(chatMessage, ChatRes.class);
    }

    private ChatMessage buildChatMessage(ChatReq req) {
        ChatMessage chatMessage = ReflectionUtil.copy(req, ChatMessage.class);
        chatMessage.setUserId(tokenService.getItem(TokenItem.OPERATOR.name(), String.class));
        chatMessage.setAvatar(tokenService.getItem(TokenItem.AVATAR.name(), String.class));
        return chatMessage;
    }

    public void feedback(String msgId) {
        chatMessageHistoryService.feedbackChatMessageById(msgId);
    }

    public void retract(RetractChatReq req) {
        String operator = tokenService.getItem(TokenItem.OPERATOR.name(), String.class);
        List<ChatMessage> retractChatMessageList = chatMessageHistoryService.getAndDeleteChatMessage(req.getMsgId(),
                operator, req.getRoomId(), req.getTargetId());
        if (CollectionUtils.isNotEmpty(retractChatMessageList)) {
            for (ChatMessage retractChatMessage : retractChatMessageList) {
                sendMessageService.publishMsg(ChatMessageConstant.RETRACT, retractChatMessage.getTargetId(),
                        retractChatMessage);
            }
        }
    }
}
