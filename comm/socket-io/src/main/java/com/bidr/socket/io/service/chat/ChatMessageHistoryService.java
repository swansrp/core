package com.bidr.socket.io.service.chat;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.constant.param.ChatParam;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.dao.repository.mongo.ChatMessageHistoryRepository;
import com.bidr.socket.io.vo.chat.history.ChatDeliveredReq;
import com.bidr.socket.io.vo.chat.history.ChatHistoryReq;
import com.bidr.socket.io.vo.chat.msg.ChatMessageRes;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

/**
 * Title: ChatMessageHistoryService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/10/9 17:08
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.chat.service.chat
 */
@Slf4j
@Service
public class ChatMessageHistoryService {

    @Resource
    private TokenService tokenService;
    @Resource
    private SysConfigCacheService sysConfigCacheService;
    @Resource
    private ChatMessageHistoryRepository chatMessageHistoryRepository;

    public Page<ChatMessage> queryHistory(ChatHistoryReq req) {
        String operatorId = tokenService.getCurrentUserId();
        Page<TopicChatMessage> topicChatMessageList = null;
        if (StringUtils.isNotBlank(req.getTargetId())) {
            String targetId = req.getTargetId();
            topicChatMessageList = chatMessageHistoryRepository.getChatMessageByTargetId(operatorId, targetId,
                    req.getBeforeAt(), req.getCurrentPage().intValue(), req.getPageSize().intValue());
        } else if (StringUtils.isNotBlank(req.getRoomId())) {
            String roomId = req.getRoomId();
            topicChatMessageList = chatMessageHistoryRepository.getChatMessageByRoomId(operatorId, roomId,
                    req.getBeforeAt(), req.getCurrentPage().intValue(), req.getPageSize().intValue());
        } else {
            Validator.assertException(ErrCodeSys.PA_DATA_NOT_EXIST, "聊天历史目标id");
        }
        return Resp.convert(topicChatMessageList, ChatMessage.class);
    }

    public List<TopicChatMessage> getUnDeliveredChatMessage(String operator, String roomId) {
        return chatMessageHistoryRepository.getUnDeliveredChatMessage(operator, roomId);
    }

    public List<ChatMessageRes> getUnDeliveredChatMessage(ChatDeliveredReq req) {
        String operatorId = tokenService.getCurrentUserId();
        List<TopicChatMessage> resList = chatMessageHistoryRepository.getUnDeliveredChatMessage(operatorId,
                req.getRoomId());
        if (CollectionUtils.isNotEmpty(resList)) {
            for (TopicChatMessage message : resList) {
                message.setDelivered(CommonConst.YES);
                chatMessageHistoryRepository.updateById(message);
            }
        }
        return ReflectionUtil.copyList(resList, ChatMessageRes.class);
    }

    public List<ChatMessage> getAndDeleteChatMessage(String originalMsgId, String userId, String roomId,
                                                     String targetId) {
        List<TopicChatMessage> messageList = chatMessageHistoryRepository.getMessageByOriginalMsgId(originalMsgId,
                userId, roomId, targetId);
        if (CollectionUtils.isNotEmpty(messageList)) {
            for (TopicChatMessage message : messageList) {
                chatMessageHistoryRepository.deleteById(message);
            }
        }
        return ReflectionUtil.copyList(messageList, ChatMessage.class);
    }

    public void feedbackChatMessageById(String msgId) {
        TopicChatMessage topicChatMessage = chatMessageHistoryRepository.selectById(msgId);
        topicChatMessage.setDelivered(CommonConst.YES);
        save(topicChatMessage);
    }

    public void save(TopicChatMessage msg) {
        int expired = sysConfigCacheService.getParamInt(ChatParam.CHAT_MESSAGE_EXPIRED_TIME);
        msg.setExpireTime(DateUtil.endTime(DateUtils.addSeconds(new Date(), expired)));
        chatMessageHistoryRepository.insertOrUpdateSelectiveById(msg);
    }
}
