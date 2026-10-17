package com.bidr.socket.io.dao.repository.mongo;

import com.bidr.mongo.dao.repository.BaseMongoRepository;
import com.bidr.socket.io.dao.po.chat.ChatHistory;
import org.springframework.stereotype.Repository;

/**
 * Title: ChatHistoryRepository
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/28 15:21
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.chat.dao.repository.mongo
 */
@Repository
public class ChatHistoryRepository extends BaseMongoRepository<ChatHistory> {
}
