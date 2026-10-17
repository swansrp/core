package com.bidr.socket.io.dao.repository.mongo;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.mongo.dao.repository.BaseMongoRepository;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

/**
 * Title: ChatMessageHistoryRepository
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/28 14:12
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.chat.dao.repository.mongo
 */
@Repository
public class ChatMessageHistoryRepository extends BaseMongoRepository<TopicChatMessage> {

    public Page<TopicChatMessage> getChatMessageByRoomId(String operatorId, String roomId, Date beforeAt,
                                                         int currentPageNumber, int pageSize) {
        Query query = new Query(Criteria.where("userId").is(operatorId));
        query.addCriteria(Criteria.where("roomId").is(roomId));
        query.addCriteria(Criteria.where("timestamp").lte(beforeAt.getTime()));
        super.sortHelper(query, Sort.Direction.DESC, "timestamp");
        return super.select(query, currentPageNumber, pageSize);
    }

    public Page<TopicChatMessage> getChatMessageByTargetId(String operatorId, String targetId, Date beforeAt,
                                                           int currentPageNumber, int pageSize) {
        Query query = new Query(Criteria.where("userId").is(operatorId));
        query.addCriteria(Criteria.where("targetId").is(targetId));
        query.addCriteria(Criteria.where("timestamp").lte(beforeAt.getTime()));
        super.sortHelper(query, Sort.Direction.DESC, "timestamp");
        return super.select(query, currentPageNumber, pageSize);
    }

    public List<TopicChatMessage> getUnDeliveredChatMessage(String userId, String roomId) {
        Query query = new Query(Criteria.where("targetId").is(userId));
        query.addCriteria(Criteria.where("roomId").is(roomId));
        query.addCriteria(Criteria.where("delivered").is(CommonConst.NO));
        super.sortHelper(query, Sort.Direction.ASC, "timestamp");
        return super.select(query);
    }

    public List<TopicChatMessage> getMessageByOriginalMsgId(String originalMsgId, String userId, String roomId,
                                                            String targetId) {
        Query query = new Query(Criteria.where("originalMsgId").is(originalMsgId));
        query.addCriteria(Criteria.where("userId").is(userId));
        if (StringUtils.isNotBlank(roomId)) {
            query.addCriteria(Criteria.where("roomId").is(roomId));
        }
        if (StringUtils.isNotBlank(targetId)) {
            query.addCriteria(Criteria.where("targetId").is(targetId));
        }
        return super.select(query);
    }

    public void cleanMessage(String roomId) {
        Query query = new Query(Criteria.where("roomId").is(roomId));
        super.delete(query);
    }
}
