package com.bidr.socket.io.service.socket;


import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.mybatis.dao.repository.SaSequenceService;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.redis.aop.publish.RedisPublishConfig;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.config.SocketIoConfig;
import com.bidr.socket.io.constant.ChatMessageConstant;
import com.bidr.socket.io.constant.dict.MessageTypeDict;
import com.bidr.socket.io.constant.param.ChatParam;
import com.bidr.socket.io.dao.entity.ChatRoomMember;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.dao.repository.mysql.ChatRoomMemberService;
import com.bidr.socket.io.service.chat.ChatMessageHistoryService;
import com.bidr.socket.io.utils.ClientUtil;
import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

/**
 * Title: SobotSendMessageService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/9/22 17:26
 */
@Slf4j
@Service
public class SendMessageService {

    private static final String MSG_SEQ_ID = "MSG_ID_SEQ";

    @Resource
    private ChatRoomMemberService chatRoomMemberService;
    @Resource
    private RedisPublishConfig redisPublishConfig;
    @Resource
    private ChatMessageHistoryService chatMessageHistoryService;
    @Resource
    private SaSequenceService saSequenceService;
    @Resource
    private SysConfigCacheService sysConfigCacheService;
    @Lazy
    @Resource
    private SocketIOServer socketioServer;

    public TopicChatMessage buildTopicChatMessage(String msgType, String target, ChatMessage msg) {
        TopicChatMessage topicChatMessage = ReflectionUtil.copy(msg, TopicChatMessage.class);
        if (StringUtils.isBlank(topicChatMessage.getMsgId())) {
            topicChatMessage.setMsgId(UUID.randomUUID().toString());
        }
        if (StringUtils.isBlank(topicChatMessage.getOriginalMsgId())) {
            topicChatMessage.setOriginalMsgId(topicChatMessage.getMsgId());
        }
        topicChatMessage.setTopic(msgType);
        topicChatMessage.setTargetId(target);
        topicChatMessage.setTimestamp(System.currentTimeMillis());
        return topicChatMessage;
    }

    public void publish(TopicChatMessage topicChatMessage) {
        log.debug("广播消息 {}", topicChatMessage);
        redisPublishConfig.publish(SocketIoConfig.buildChatMessageTopic(), topicChatMessage, true);
    }

    public void broadcastMsg(ChatMessage chatMessage) {
        List<ChatRoomMember> memberList = chatRoomMemberService.getMemberListByRoomId(chatMessage.getRoomId());
        if (CollectionUtils.isNotEmpty(memberList)) {
            String msgId;
            try {
                msgId = saSequenceService.getSeq(MSG_SEQ_ID);
            } catch (Exception e) {
                msgId = UUID.randomUUID().toString();
            }
            chatMessage.setOriginalMsgId(msgId);
            for (ChatRoomMember member : memberList) {
                publishMsg(ChatMessageConstant.CHAT, member.getUserId(), chatMessage);
            }
            chatMessage.setMsgId(msgId);
        }
    }

    public void publishMsg(String msgType, String target, ChatMessage msg) {
        TopicChatMessage topicChatMessage;
        topicChatMessage = saveUndeliveredTopicChatMessage(msgType, target, msg);
        publish(topicChatMessage);
    }

    private TopicChatMessage saveUndeliveredTopicChatMessage(String msgType, String target, ChatMessage msg) {
        TopicChatMessage topicChatMessage = buildTopicChatMessage(msgType, target, msg);
        topicChatMessage.setDelivered(BoolDict.NO.getValue());
        saveMessageHistory(topicChatMessage);
        return topicChatMessage;
    }

    private void saveMessageHistory(TopicChatMessage topicChatMessage) {
        chatMessageHistoryService.save(topicChatMessage);
    }

    public void publishMsg(String target, ChatMessage msg) {
        publishMsg(ChatMessageConstant.CHAT, target, msg);
    }

    public void show(TopicChatMessage topicChatMessage) {
        BroadcastOperations roomOperations = socketioServer.getRoomOperations(topicChatMessage.getTargetId());
        log.info("广播client数量:{}", roomOperations.getClients().size());
        if (CollectionUtils.isNotEmpty(roomOperations.getClients())) {
            for (SocketIOClient client : roomOperations.getClients()) {
                log.info("{}-{}", topicChatMessage.getTargetId(), client.getSessionId().toString());
                if (!client.getSessionId().toString().equals(topicChatMessage.getSessionId())) {
                    show(client, topicChatMessage);
                } else {
                    show(client, topicChatMessage);
                }
                boolean manualDeliveredAck = sysConfigCacheService.getParamSwitch(ChatParam.CHAT_MESSAGE_MANUAL_DELIVERED_ACK);
                if (!manualDeliveredAck) {
                    messageDelivered(topicChatMessage);
                }
            }
        }
    }

    public void show(SocketIOClient client, TopicChatMessage message) {
        String operator = ClientUtil.get(client, TokenItem.OPERATOR);
        log.debug("To: 客户端[{}-{}] - 发送消息 {}", operator, client.getSessionId().toString(), message);
        client.sendEvent(message.getTopic(), message);
    }

    public void messageDelivered(TopicChatMessage topicChatMessage) {
        topicChatMessage.setDelivered(BoolDict.YES.getValue());
        saveMessageHistory(topicChatMessage);
    }

    public void showSysMsg(String target, String msg) {
        TopicChatMessage topicChatMessage = buildTopicChatMessage(ChatMessageConstant.SYS, target, buildSysChatMessage(msg));
        socketioServer.getRoomOperations(target).sendEvent(topicChatMessage.getTopic(), topicChatMessage);
    }

    private ChatMessage buildSysChatMessage(String msg) {
        ChatMessage message = new ChatMessage();
        message.setMsgType(MessageTypeDict.SYS.getValue());
        message.setContent(msg);
        return message;
    }

}
